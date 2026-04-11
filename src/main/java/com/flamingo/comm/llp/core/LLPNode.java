package com.flamingo.comm.llp.core;

import java.util.Optional;

/**
 * Represents a single layer (node) within an LLP frame.
 *
 * <p>An {@code LLPNode} forms part of a hierarchical structure where each node
 * may contain another inner node, creating a chain of layers (similar to an onion model).</p>
 *
 * <p>Each node is responsible for interpreting its own metadata and delegating
 * further parsing to its inner node if present.</p>
 *
 * <p>Implementations of this interface should be immutable and thread-safe.</p>
 */
public interface LLPNode {

    /**
     * Determines whether this node is terminal.
     *
     * <p>A node is considered terminal if:
     * <ul>
     *     <li>It has no inner node</li>
     *     <li>Its inner node is the {@link FinalNode}</li>
     * </ul>
     *
     * @return {@code true} if this is the last meaningful layer, {@code false} otherwise
     */
    default boolean isTerminal() {
        return getInnerNode().isEmpty() ||
                getInnerNode().get().getId() == FinalNode.ID;
    }

    /**
     * Returns the unique identifier of this layer.
     *
     * <p>Layer ID {@code 0} is reserved for the {@link FinalNode},
     * which represents the raw payload.</p>
     *
     * @return layer identifier (0-255)
     */
    int getId();

    /**
     * Indicates whether this layer can be safely skipped if not recognized.
     *
     * <p>If {@code true}, a parser that does not support this layer may ignore it
     * and continue processing the inner payload.</p>
     *
     * <p>If {@code false}, the parser should fail if the layer is not supported,
     * as it may alter the payload semantics.</p>
     *
     * @return {@code true} if skippable, {@code false} otherwise
     */
    boolean isSkippable();

    /**
     * Returns the inner node (next layer).
     *
     * <p>If present, the inner node represents the next layer in the hierarchy.</p>
     *
     * @return optional inner node
     */
    Optional<LLPNode> getInnerNode();
}
