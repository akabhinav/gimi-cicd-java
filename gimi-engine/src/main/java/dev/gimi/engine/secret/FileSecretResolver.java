package dev.gimi.engine.secret;

import dev.gimi.core.exception.ExecutionException;
import dev.gimi.core.model.SecretRef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Resolves secrets by reading their values from files on disk.
 *
 * <p>The {@link SecretRef#path()} is interpreted as a file system path. The
 * entire file content is read and trimmed to produce the secret value.
 */
public final class FileSecretResolver implements SecretResolver {

    /**
     * Resolves the secret by reading the file specified by the reference's path.
     *
     * @param ref the secret reference whose path is a file system path
     * @return the trimmed contents of the file
     * @throws ExecutionException if the file does not exist or cannot be read
     */
    @Override
    public String resolve(SecretRef ref) {
        try {
            return Files.readString(Path.of(ref.path())).trim();
        } catch (NoSuchFileException e) {
            throw new ExecutionException(
                    "Secret file not found: " + ref.path(),
                    "Create the secret file at '" + ref.path() + "' or update the secret reference path",
                    e
            );
        } catch (IOException e) {
            throw new ExecutionException(
                    "Failed to read secret file: " + ref.path(),
                    "Check file permissions and ensure the path points to a readable file",
                    e
            );
        }
    }
}
