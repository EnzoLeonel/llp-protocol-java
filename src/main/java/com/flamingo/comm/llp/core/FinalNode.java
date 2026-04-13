package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPNode;

import java.nio.ByteBuffer;
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
    private static final ByteBuffer EMPTY_ARRAY =
            ByteBuffer.wrap(new byte[0]).asReadOnlyBuffer();

    public static final FinalNode EMPTY = new FinalNode(EMPTY_ARRAY);
    private final ByteBuffer payload;

    /**
     * Creates a FinalNode with payload.
     *
     * @param payload raw payload (nullable → treated as empty)
     */
    private FinalNode(byte[] payload) {
        this.payload = ByteBuffer.wrap(payload.clone()).asReadOnlyBuffer();
    }

    private FinalNode(ByteBuffer payload) {
        this.payload = payload;
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
     * Raw payload sent by the sender
     *
     * @return an immutable array of bytes containing the raw payload sent by the sender, or an empty array
     */
    public ByteBuffer getPayload() {
        return payload.asReadOnlyBuffer();
    }

    @Override
    public String toString() {
        byte[] bytes = new byte[payload.remaining()];
        payload.get(bytes);

        return "FinalNode{" +
                "payloadHex=" + HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT) +
                '}';
    }
}