package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPNode;

import java.util.Arrays;

/**
 * Represents an unknown or unsupported LLP layer.
 *
 * <p>This node is created when the parser encounters a layer ID
 * for which no registered parser is available.</p>
 *
 * <p>If the layer is marked as skippable, the parser will still
 * continue parsing inner layers, allowing partial compatibility.</p>
 *
 * <p>This node preserves raw metadata for potential future use.</p>
 */
public final class UnknownNode implements LLPNode {

    private final int id;
    private final byte[] metadata;

    UnknownNode(int id, byte[] metadata) {
        this.id = id;
        this.metadata = (metadata != null) ? Arrays.copyOf(metadata, metadata.length) : new byte[0];
    }

    @Override
    public int getId() {
        return id;
    }

    /**
     * Returns raw metadata bytes associated with this unknown layer.
     *
     * @return metadata copy (never null)
     */
    public byte[] getMetadata() {
        return Arrays.copyOf(metadata, metadata.length);
    }

    @Override
    public String toString() {
        return "UnknownNode{" +
                "id=" + id +
                ", metadataLength=" + metadata.length +
                '}';
    }
}
