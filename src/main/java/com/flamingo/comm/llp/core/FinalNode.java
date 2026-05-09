package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPNode;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Final LLP node (Layer ID = 0).
 *
 * <p>This node represents the innermost payload of an LLP frame.
 * It contains no metadata and cannot have child nodes.</p>
 *
 * <p>This class is immutable and thread-safe.</p>
 */
public final class FinalNode implements LLPNode {
    public static final int ID = 0;
    /**
     * Shared instance for empty payload (singleton).
     */
    private static final byte[] EMPTY_ARRAY = new byte[0];

    public static final FinalNode EMPTY = new FinalNode(EMPTY_ARRAY);
    private final byte[] payload;

    /**
     * Creates a FinalNode with payload.
     * The `of()` factory method prevents it from being null or empty
     *
     * @param payload raw payload (nullable → treated as empty)
     */
    private FinalNode(byte[] payload) {
        this.payload = payload.clone();
    }

    /**
     * The `of()` factory method prevents it from being null or empty
     */
    private FinalNode(ByteBuffer payload) {
        ByteBuffer readOnly = payload.asReadOnlyBuffer();
        byte[] copy = new byte[readOnly.remaining()];
        readOnly.get(copy);

        this.payload = copy;
    }

    @Override
    public int getId() {
        return ID;
    }

    /**
     * Factory method to reuse EMPTY instance when possible.
     */
    static FinalNode of(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return EMPTY;
        }
        return new FinalNode(payload);
    }

    /**
     * Factory method to reuse EMPTY instance when possible.
     */
    static FinalNode of(ByteBuffer payload) {
        if (payload == null || !payload.hasRemaining()) {
            return EMPTY;
        }
        return new FinalNode(payload);
    }

    /**
     * Raw payload sent by the sender
     *
     * @return an immutable array of bytes containing the raw payload sent by the sender, or an empty array
     */
    public ByteBuffer getPayload() {
        return ByteBuffer.wrap(payload).asReadOnlyBuffer();
    }

    @Override
    public String toString() {
        return "FinalNode{" +
                "payloadHex=" + HexFormat.of().formatHex(payload).toUpperCase(Locale.ROOT) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinalNode that)) return false;
        return Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(payload);
    }
}