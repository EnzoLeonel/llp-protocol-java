package com.flamingo.comm.llp.core;

import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Arrays;

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
    public FinalNode(byte[] payload) {
        this.payload = (payload == null || payload.length == 0)
                ? EMPTY_ARRAY
                : Arrays.copyOf(payload, payload.length);
    }

    @Override
    public int getId() {
        return ID;
    }

    @Override
    public Optional<LLPNode> getInnerNode() {
        return Optional.empty();
    }

    @Override
    public boolean isSkippable() {
        return true; // Always skippable by definition
    }

    public byte[] getPayload() {
        return payload;
    }

    /**
     * Factory method to reuse EMPTY instance when possible.
     */
    public static FinalNode of(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return EMPTY;
        }
        return new FinalNode(payload);
    }

    @Override
    public String toString() {
        return "FinalNode{" +
                "payloadHex=" + HexFormat.of().formatHex(payload).toUpperCase(Locale.ROOT) +
                '}';
    }
}