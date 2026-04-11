package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPNode;

import java.util.Objects;

/**
 * Represents a fully received and validated LLP frame.
 *
 * <p>This class is immutable and thread-safe.</p>
 *
 * <p>An LLPFrame contains a hierarchy of {@link LLPNode} elements (layers),
 * starting from the outermost layer down to the final payload node.</p>
 */
public final class LLPFrame {

    private final LLPNodeChain nodeChain;
    private final int crc;
    private final long timestamp;

    /**
     * Creates a new frame with the current system timestamp.
     *
     * @param nodeChain nested nodes
     * @param crc     calculated CRC value
     */
    LLPFrame(LLPNodeChain nodeChain, int crc) {
        this(nodeChain, crc, System.currentTimeMillis());
    }

    /**
     * Creates a new frame.
     *
     * @param nodeChain   nested nodes
     * @param crc       calculated CRC value
     * @param timestamp creation timestamp (milliseconds)
     */
    LLPFrame(LLPNodeChain nodeChain, int crc, long timestamp) {
        this.nodeChain = nodeChain;
        this.crc = crc;
        this.timestamp = timestamp;
    }

    /**
     * Returns the CRC value of the frame.
     */
    public int crc() {
        return crc;
    }

    /**
     * Returns the timestamp when the frame was created.
     */
    public long timestamp() {
        return timestamp;
    }

    public LLPNodeChain chain() {
        return nodeChain;
    }

    /**
     * Returns a string representation of the frame.
     */
    @Override
    public String toString() {
        return "LLPFrame{" +
                "crc=" + crc +
                ", timestamp=" + timestamp +
                ", nodes=" + nodeChain.size() +
                '}';
    }

    /**
     * Compares this frame with another object.
     *
     * <p>Equality is based on content and CRC.
     * Timestamp is intentionally ignored.</p>
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LLPFrame that)) return false;

        return crc == that.crc &&
                Objects.equals(nodeChain, that.nodeChain);
    }

    /**
     * Returns the hash code of the frame.
     */
    @Override
    public int hashCode() {
        return Objects.hash(nodeChain, crc);
    }
}