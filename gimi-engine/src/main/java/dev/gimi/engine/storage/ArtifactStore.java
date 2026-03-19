package dev.gimi.engine.storage;

import dev.gimi.core.model.ArtifactRef;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for pipeline build artifacts.
 */
public interface ArtifactStore {

    /**
     * Stores an artifact and returns its reference metadata.
     *
     * @param runId       the pipeline run ID
     * @param stageName   the stage that produced the artifact
     * @param stepName    the step that produced the artifact
     * @param path        the original file path or name
     * @param content     the artifact content stream
     * @param sizeBytes   the size of the content in bytes
     * @param contentType the MIME content type
     * @return the artifact reference with storage metadata
     */
    ArtifactRef store(String runId, String stageName, String stepName,
                      String path, InputStream content, long sizeBytes, String contentType);

    /**
     * Retrieves an artifact's content by its storage key.
     *
     * @param storageKey the storage key identifying the artifact
     * @return an input stream of the artifact content, or empty if not found
     */
    Optional<InputStream> retrieve(String storageKey);

    /**
     * Deletes an artifact by its storage key.
     *
     * @param storageKey the storage key identifying the artifact
     */
    void delete(String storageKey);

    /**
     * Lists all artifacts associated with a pipeline run.
     *
     * @param runId the pipeline run ID
     * @return a list of artifact references
     */
    List<ArtifactRef> listByRun(String runId);
}
