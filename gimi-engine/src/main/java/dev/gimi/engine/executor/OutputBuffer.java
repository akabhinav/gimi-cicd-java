package dev.gimi.engine.executor;

/**
 * Bounded output buffer that prevents unbounded memory growth from long-running
 * pipeline steps. Captures the first {@value #MAX_OUTPUT_BYTES} bytes of output
 * and discards the rest with a truncation notice.
 *
 * <p>At 10K concurrent pipelines, unbounded StringBuilders could consume gigabytes
 * of heap. This buffer caps each step's output at 10 MB.
 */
final class OutputBuffer {

    /** Maximum output size per step (10 MB). */
    static final int MAX_OUTPUT_BYTES = 10 * 1024 * 1024;

    private static final String TRUNCATION_NOTICE =
            "\n... [OUTPUT TRUNCATED — exceeded " + (MAX_OUTPUT_BYTES / 1024 / 1024) + " MB limit] ...\n";

    private final StringBuilder buffer = new StringBuilder(8192);
    private boolean truncated = false;
    private int byteCount = 0;

    /**
     * Appends text to the buffer if the size limit has not been reached.
     */
    void append(String text) {
        if (truncated) {
            return;
        }

        int textBytes = text.length(); // approximate; sufficient for size guard
        if (byteCount + textBytes > MAX_OUTPUT_BYTES) {
            int remaining = MAX_OUTPUT_BYTES - byteCount;
            if (remaining > 0) {
                buffer.append(text, 0, remaining);
            }
            buffer.append(TRUNCATION_NOTICE);
            truncated = true;
            byteCount = MAX_OUTPUT_BYTES;
        } else {
            buffer.append(text);
            byteCount += textBytes;
        }
    }

    /**
     * Returns whether the output was truncated due to exceeding the size limit.
     */
    boolean isTruncated() {
        return truncated;
    }

    @Override
    public String toString() {
        return buffer.toString();
    }
}
