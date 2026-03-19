package dev.gimi.engine.storage;

import dev.gimi.core.model.ArtifactRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * Local filesystem-backed implementation of {@link ArtifactStore} for development and testing.
 *
 * <p>Artifacts are stored under a configurable base directory using the key pattern
 * {@code {runId}/{stageName}/{stepName}/{filename}}.
 */
public final class FileSystemArtifactStore implements ArtifactStore {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemArtifactStore.class);

    private final Path baseDir;

    /**
     * Creates a new filesystem-backed artifact store.
     *
     * @param baseDir the base directory under which artifacts are stored
     */
    public FileSystemArtifactStore(Path baseDir) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir must not be null");
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create artifact base directory: " + baseDir, e);
        }
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
        Path targetPath = baseDir.resolve(storageKey);

        try {
            Files.createDirectories(targetPath.getParent());

            // Copy to temp file first, then move for atomicity
            Path tempFile = Files.createTempFile(targetPath.getParent(), "upload-", ".tmp");
            long bytesWritten = Files.copy(content, tempFile, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);

            String checksum = computeMd5(targetPath);

            LOG.debug("Stored artifact: key={}, size={}, checksum={}", storageKey, bytesWritten, checksum);

            return new ArtifactRef(
                    id, runId, stageName, stepName, path, contentType,
                    bytesWritten, checksum, storageKey, Map.of(),
                    Instant.now(), null
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to store artifact: " + storageKey, e);
        }
    }

    @Override
    public Optional<InputStream> retrieve(String storageKey) {
        Objects.requireNonNull(storageKey, "storageKey must not be null");

        Path filePath = baseDir.resolve(storageKey);
        if (!Files.exists(filePath)) {
            LOG.debug("Artifact not found: key={}", storageKey);
            return Optional.empty();
        }

        try {
            return Optional.of(Files.newInputStream(filePath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve artifact: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Objects.requireNonNull(storageKey, "storageKey must not be null");

        Path filePath = baseDir.resolve(storageKey);
        try {
            if (Files.deleteIfExists(filePath)) {
                LOG.debug("Deleted artifact: key={}", storageKey);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete artifact: " + storageKey, e);
        }
    }

    @Override
    public List<ArtifactRef> listByRun(String runId) {
        Objects.requireNonNull(runId, "runId must not be null");

        Path runDir = baseDir.resolve(runId);
        List<ArtifactRef> artifacts = new ArrayList<>();

        if (!Files.isDirectory(runDir)) {
            return artifacts;
        }

        try {
            collectArtifacts(runDir, runId, artifacts);
        } catch (IOException e) {
            throw new RuntimeException("Failed to list artifacts for run: " + runId, e);
        }

        return artifacts;
    }

    private void collectArtifacts(Path dir, String runId, List<ArtifactRef> artifacts) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    collectArtifacts(entry, runId, artifacts);
                } else if (Files.isRegularFile(entry)) {
                    Path relative = baseDir.resolve(runId).relativize(entry);
                    String[] parts = relative.toString().split("/", 3);

                    String stageName = parts.length > 0 ? parts[0] : null;
                    String stepName = parts.length > 1 ? parts[1] : null;
                    String filename = parts.length > 2 ? parts[2] : entry.getFileName().toString();

                    String storageKey = runId + "/" + relative;

                    artifacts.add(new ArtifactRef(
                            UUID.randomUUID().toString(),
                            runId, stageName, stepName, filename, null,
                            Files.size(entry), null, storageKey, Map.of(),
                            Instant.ofEpochMilli(Files.getLastModifiedTime(entry).toMillis()), null
                    ));
                }
            }
        }
    }

    private static String computeMd5(Path path) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] data = Files.readAllBytes(path);
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
}
