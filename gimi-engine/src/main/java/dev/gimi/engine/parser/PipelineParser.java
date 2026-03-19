package dev.gimi.engine.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.gimi.core.exception.ParseException;
import dev.gimi.core.model.Pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Parses YAML pipeline definitions into {@link Pipeline} model objects.
 *
 * <p>This class is thread-safe. The underlying {@link ObjectMapper} is fully configured
 * at construction time and is safe for concurrent use.
 */
public final class PipelineParser {

    private final ObjectMapper mapper;

    /**
     * Constructs a new {@code PipelineParser} with a pre-configured {@link ObjectMapper}
     * that reads YAML, uses snake_case property naming, registers the Java Time module,
     * and ignores unknown properties.
     */
    public PipelineParser() {
        this.mapper = new ObjectMapper(new YAMLFactory());
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    /**
     * Parses a YAML string into a {@link Pipeline} record.
     *
     * @param yaml the YAML content representing a pipeline definition
     * @return the parsed {@link Pipeline}
     * @throws ParseException if the YAML content is malformed or cannot be mapped to a {@link Pipeline}
     */
    public Pipeline parse(String yaml) {
        try {
            return mapper.readValue(yaml, Pipeline.class);
        } catch (com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException e) {
            throw new ParseException(
                    "Unknown property '" + e.getPropertyName() + "' in pipeline definition",
                    "Check for typos in property names. Known properties at this level: "
                            + e.getKnownPropertyIds(),
                    e
            );
        } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e) {
            throw new ParseException(
                    "Type mismatch while parsing pipeline: " + e.getOriginalMessage(),
                    "Verify that the value for '" + e.getPathReference()
                            + "' has the correct type (e.g., list vs string, number vs string)",
                    e
            );
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            throw new ParseException(
                    "Malformed YAML at line " + e.getLocation().getLineNr()
                            + ", column " + e.getLocation().getColumnNr(),
                    "Check indentation and YAML syntax near the reported location",
                    e
            );
        } catch (IOException e) {
            throw new ParseException(
                    "Failed to parse pipeline YAML: " + e.getMessage(),
                    "Ensure the YAML is well-formed and matches the expected pipeline schema",
                    e
            );
        }
    }

    /**
     * Parses a YAML file into a {@link Pipeline} record.
     *
     * <p>Reads the entire file content and delegates to {@link #parse(String)}.
     *
     * @param file the path to the YAML pipeline definition file
     * @return the parsed {@link Pipeline}
     * @throws ParseException if the file does not exist or cannot be parsed
     */
    public Pipeline parseFile(Path file) {
        try {
            String content = Files.readString(file);
            return parse(content);
        } catch (NoSuchFileException e) {
            throw new ParseException(
                    "Pipeline file not found: " + file,
                    "Verify the file path exists and is readable",
                    e
            );
        } catch (IOException e) {
            throw new ParseException(
                    "Failed to read pipeline file: " + file,
                    "Check file permissions and ensure the path points to a regular file",
                    e
            );
        }
    }

    /**
     * Returns the configured {@link ObjectMapper} used by this parser.
     *
     * <p>The returned mapper can be reused for additional serialization or deserialization
     * tasks that require the same YAML and naming configuration.
     *
     * @return the configured {@link ObjectMapper}
     */
    public ObjectMapper objectMapper() {
        return mapper;
    }
}
