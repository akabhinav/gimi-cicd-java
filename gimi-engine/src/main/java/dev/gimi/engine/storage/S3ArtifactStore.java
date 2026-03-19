package dev.gimi.engine.storage;

import dev.gimi.core.model.ArtifactRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * AWS S3-backed implementation of {@link ArtifactStore}.
 *
 * <p>Artifacts are stored under the key pattern {@code {runId}/{stageName}/{stepName}/{filename}}.
 * Each artifact is assigned a UUID-based ID and an MD5 checksum is computed during upload.
 */
public final class S3ArtifactStore implements ArtifactStore {

    private static final Logger LOG = LoggerFactory.getLogger(S3ArtifactStore.class);

    private final S3Client s3Client;
    private final String bucketName;

    /**
     * Creates a new S3-backed artifact store.
     *
     * @param s3Client   the AWS S3 client
     * @param bucketName the S3 bucket name
     */
    public S3ArtifactStore(S3Client s3Client, String bucketName) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client must not be null");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName must not be null");
    }

    @Override
    public ArtifactRef store(String runId, String stageName, String stepName,
                             String path, InputStream content, long sizeBytes, String contentType) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(content, "content must not be null");

        String filename = Path.of(path).getFileName().toString();
        String storageKey = runId + "/" + stageName + "/" + stepName + "/" + filename;
        String id = UUID.randomUUID().toString();

        try {
            // Read content into byte array for checksum calculation and upload
            byte[] data = content.readAllBytes();
            String checksum = computeMd5(data);

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .contentLength((long) data.length)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(data));

            LOG.debug("Stored artifact: key={}, size={}, checksum={}", storageKey, data.length, checksum);

            return new ArtifactRef(
                    id, runId, stageName, stepName, path, contentType,
                    data.length, checksum, storageKey, Map.of(),
                    Instant.now(), null
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to store artifact: " + storageKey, e);
        }
    }

    @Override
    public Optional<InputStream> retrieve(String storageKey) {
        Objects.requireNonNull(storageKey, "storageKey must not be null");

        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();

            InputStream stream = s3Client.getObject(getRequest);
            return Optional.of(stream);
        } catch (NoSuchKeyException e) {
            LOG.debug("Artifact not found: key={}", storageKey);
            return Optional.empty();
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve artifact: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Objects.requireNonNull(storageKey, "storageKey must not be null");

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            LOG.debug("Deleted artifact: key={}", storageKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete artifact: " + storageKey, e);
        }
    }

    @Override
    public List<ArtifactRef> listByRun(String runId) {
        Objects.requireNonNull(runId, "runId must not be null");

        List<ArtifactRef> artifacts = new ArrayList<>();
        String prefix = runId + "/";

        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);

            for (S3Object obj : response.contents()) {
                String key = obj.key();
                String[] parts = key.split("/", 4);

                String stageName = parts.length > 1 ? parts[1] : null;
                String stepName = parts.length > 2 ? parts[2] : null;
                String filename = parts.length > 3 ? parts[3] : key;

                artifacts.add(new ArtifactRef(
                        UUID.randomUUID().toString(),
                        runId, stageName, stepName, filename, null,
                        obj.size(), null, key, Map.of(),
                        obj.lastModified(), null
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list artifacts for run: " + runId, e);
        }

        return artifacts;
    }

    private static String computeMd5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
}
