package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPNode;

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
    private static final byte[] EMPTY_ARRAY = new byte[0];

    /**
     * Shared instance for empty payload (singleton).
     */
    public static final FinalNode EMPTY = new FinalNode(EMPTY_ARRAY);

    private final byte[] payload;

    /**
     * Creates a FinalNode with payload.
     *
     * @param payload raw payload (nullable → treated as empty)
     */
    FinalNode(byte[] payload) {
        this.payload = (payload == null || payload.length == 0)
                ? EMPTY_ARRAY
                : Arrays.copyOf(payload, payload.length);
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
     * @return an array of bytes containing the raw payload sent by the sender, or an empty array
     */
    public byte[] getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "FinalNode{" +
                "payloadHex=" + HexFormat.of().formatHex(payload).toUpperCase(Locale.ROOT) +
                '}';
    }
}