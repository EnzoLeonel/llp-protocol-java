package com.flamingo.comm.llp.core;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Visitor for processing {@link LLPNode} instances based on their concrete type.
 *
 * <p>This class allows registering handlers for specific node types and
 * executing them when visiting nodes.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * frame.visitNodes(visitor -> visitor
 *     .on(EncryptionNode.class, node -> {
 *         // handle encryption layer
 *     })
 *     .on(CompressionNode.class, node -> {
 *         // handle compression layer
 *     })
 * );
 * }</pre>
 *
 * <p>This implementation performs exact class matching (no inheritance lookup).</p>
 *
 * <p>Note: This is a lightweight alternative to the traditional Visitor pattern,
 * designed to avoid boilerplate in extensible layer-based architectures.</p>
 */
public class LLPNodeVisitor {

    private final Map<Class<?>, Consumer<?>> handlers = new HashMap<>();

    /**
     * Registers a handler for a specific node type.
     *
     * @param type    node class
     * @param handler handler to execute when a node of this type is visited
     * @param <T>     node type
     * @return this visitor instance (for chaining)
     */
    public <T extends LLPNode> LLPNodeVisitor on(Class<T> type, Consumer<T> handler) {
        handlers.put(type, handler);
        return this;
    }

    /**
     * Visits a node and executes the corresponding handler if registered.
     *
     * <p>This method performs an exact class match using {@code node.getClass()}.</p>
     *
     * @param node node to process
     */
    @SuppressWarnings("unchecked")
    public void visit(LLPNode node) {
        Consumer handler = handlers.get(node.getClass());

        if (handler != null) {
            handler.accept(node);
        }
    }
}