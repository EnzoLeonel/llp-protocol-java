package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPNode;
import com.flamingo.comm.llp.spi.ParseErrorReason;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a layer that failed to be parsed.
 *
 * <p>
 * This node is created when a layer parser returns a failure result or
 * throws an unexpected exception.
 * </p>
 *
 * <p>
 * It preserves the layer identifier, metadata, and error information,
 * allowing the user to inspect and react to parsing issues without
 * interrupting the entire parsing process.
 * </p>
 *
 * <p>
 * This class is immutable and thread-safe.
 * </p>
 */
public final class FailureNode implements LLPNode {

    private static final byte[] EMPTY_ARRAY = new byte[0];

    private final int id;
    private final byte[] metadata;
    private final ParseErrorReason errorReason;
    private final Throwable cause;

    /**
     * Creates a FailureNode without a cause and metadata empty.
     *
     * @param id          layer identifier
     * @param errorReason reason for failure (non-null)
     */
    public FailureNode(int id, ParseErrorReason errorReason) {
        this(id, null, errorReason, null);
    }

    /**
     * Creates a FailureNode without a cause.
     *
     * @param id          layer identifier
     * @param metadata    raw metadata (nullable)
     * @param errorReason reason for failure (non-null)
     */
    public FailureNode(int id, byte[] metadata, ParseErrorReason errorReason) {
        this(id, metadata, errorReason, null);
    }

    /**
     * Creates a FailureNode.
     *
     * @param id          layer identifier
     * @param metadata    raw metadata (nullable)
     * @param errorReason reason for failure (non-null)
     * @param cause       optional exception cause (nullable)
     */
    public FailureNode(int id,
                       byte[] metadata,
                       ParseErrorReason errorReason,
                       Throwable cause) {

        this.id = id;
        this.metadata = (metadata != null) ? metadata.clone() : EMPTY_ARRAY;
        this.errorReason = Objects.requireNonNull(errorReason, "errorReason cannot be null");
        this.cause = cause;
    }

    @Override
    public int getId() {
        return id;
    }

    /**
     * Returns raw metadata associated with the failed layer.
     *
     * @return read-only metadata buffer (never null)
     */
    public ByteBuffer getMetadata() {
        return ByteBuffer.wrap(metadata).asReadOnlyBuffer();
    }

    /**
     * Returns the reason why parsing failed.
     *
     * @return parse error reason
     */
    public ParseErrorReason getErrorReason() {
        return errorReason;
    }

    /**
     * Returns the underlying exception cause, if any.
     *
     * <p>This is typically set when a layer parser throws an unexpected exception.</p>
     *
     * @return optional cause
     */
    public Optional<Throwable> getCause() {
        return Optional.ofNullable(cause);
    }

    @Override
    public String toString() {
        return "FailureNode{" +
                "id=" + id +
                ", errorReason=" + errorReason +
                ", metadataLength=" + metadata.length +
                (cause != null ? ", cause=" + cause.getClass().getSimpleName() : "") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FailureNode that)) return false;
        return id == that.id &&
                Objects.equals(errorReason, that.errorReason) &&
                Arrays.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, errorReason, Arrays.hashCode(metadata));
    }
}