package com.flamingo.comm.llp.spi;

import com.flamingo.comm.llp.core.FinalNode;

/**
 * Represents a single layer (node) within an LLP frame.
 *
 * <p>An {@code LLPNode} forms part of a hierarchical structure where each node
 * may contain another inner node, creating a chain of layers (similar to an onion model).</p>
 *
 * <p>Each node is responsible for interpreting its own metadata</p>
 *
 * <p>Implementations of this interface should be immutable and thread-safe.</p>
 */
public interface LLPNode {
    /**
     * Returns the unique identifier of this layer.
     *
     * <p>Layer ID {@code 0} is reserved for the {@link FinalNode},
     * which represents the raw payload.</p>
     *
     * @return layer identifier (1-255)
     */
    int getId();
}
