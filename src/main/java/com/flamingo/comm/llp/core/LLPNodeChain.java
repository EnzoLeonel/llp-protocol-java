package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents an immutable ordered chain of {@link LLPNode} elements.
 *
 * <p>This structure models the layered composition of an LLP frame, where each node
 * represents a protocol layer. The chain is ordered from outermost layer (index 0)
 * to the innermost (deepest) layer.</p>
 *
 * <p>The class provides utility methods for querying, traversing, and visiting nodes
 * without exposing internal mutability.</p>
 *
 * <p>This class is immutable and thread-safe.</p>
 */
public final class LLPNodeChain implements Iterable<LLPNode> {

    private final List<LLPNode> nodes;

    /**
     * Creates a new {@code LLPNodeChain} from the provided list of nodes.
     *
     * <p>The input list is defensively copied to guarantee immutability.</p>
     *
     * @param nodes ordered list of nodes (outer → inner)
     */
    LLPNodeChain(List<LLPNode> nodes) {
        this.nodes = List.copyOf(nodes);
    }

    /**
     * Returns the number of nodes in the chain.
     *
     * @return total number of nodes
     */
    public int size() {
        return nodes.size();
    }

    /**
     * Returns an immutable view of the underlying node list.
     *
     * <p>The returned list preserves the original order (outer → inner).</p>
     *
     * @return immutable list of nodes
     */
    public List<LLPNode> asList() {
        return nodes;
    }

    /**
     * Finds the first node of the given type in the chain.
     *
     * <p>This method performs a linear search from outermost to innermost node.</p>
     *
     * @param type the class type of the node
     * @param <T>  the node subtype
     * @return an {@link Optional} containing the node if found, otherwise empty
     */
    public <T extends LLPNode> Optional<T> getNode(Class<T> type) {
        return nodes.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
    }

    /**
     * Finds the first node with the given layer identifier.
     *
     * <p>This method performs a linear search from outermost to innermost node.</p>
     *
     * @param id the layer identifier
     * @return an {@link Optional} containing the node if found, otherwise empty
     */
    public Optional<LLPNode> getNode(int id) {
        return nodes.stream()
                .filter(n -> n.getId() == id)
                .findFirst();
    }

    /**
     * Returns the deepest (innermost) node in the chain.
     *
     * <p>This is equivalent to the last element in the chain.</p>
     *
     * @return the deepest node
     * @throws java.util.NoSuchElementException if the chain is empty
     */
    public LLPNode getDeepestNode() {
        return nodes.getLast();
    }

    /**
     * Traverses all nodes using a visitor pattern.
     *
     * <p>A {@link LLPNodeVisitor} is configured using the provided consumer,
     * and then applied to each node in order.</p>
     *
     * @param consumer a function that configures the visitor handlers
     */
    public void visit(Consumer<LLPNodeVisitor> consumer) {
        LLPNodeVisitor visitor = new LLPNodeVisitor();
        consumer.accept(visitor);

        for (LLPNode node : nodes) {
            visitor.visit(node);
        }
    }

    /**
     * Returns an iterator over the nodes in this chain.
     *
     * <p>The iteration order is from outermost to innermost node.</p>
     *
     * @return an iterator over the nodes
     */
    @Override
    public Iterator<LLPNode> iterator() {
        return nodes.iterator();
    }

    /**
     * Builder for constructing {@link LLPNodeChain} instances incrementally.
     *
     * <p>This builder is mutable and intended to be used during parsing or frame construction.
     * Once {@link #build()} is called, the resulting {@link LLPNodeChain} is immutable.</p>
     */
    public static class Builder {

        private final List<LLPNode> nodes = new ArrayList<>();

        /**
         * Adds a node to the chain.
         *
         * <p>Nodes should be added in order from outermost to innermost layer.</p>
         *
         * @param node the node to add
         * @return this builder instance for chaining
         */
        public Builder add(LLPNode node) {
            nodes.add(node);
            return this;
        }

        /**
         * Builds an immutable {@link LLPNodeChain} from the current state.
         *
         * @return a new immutable node chain
         */
        public LLPNodeChain build() {
            return new LLPNodeChain(nodes);
        }
    }
}