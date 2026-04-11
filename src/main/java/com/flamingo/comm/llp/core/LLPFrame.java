package com.flamingo.comm.llp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents a fully received and validated LLP frame.
 *
 * <p>This class is immutable and thread-safe.</p>
 *
 * <p>An LLPFrame contains a hierarchy of {@link LLPNode} elements (layers),
 * starting from the outermost layer down to the final payload node.</p>
 */
public final class LLPFrame {

    private final LLPNode content;
    private final int crc;
    private final long timestamp;

    /**
     * Creates a new frame with the current system timestamp.
     *
     * @param content root node (outermost layer)
     * @param crc     calculated CRC value
     */
    LLPFrame(LLPNode content, int crc) {
        this(content, crc, System.currentTimeMillis());
    }

    /**
     * Creates a new frame.
     *
     * @param content   root node (outermost layer)
     * @param crc       calculated CRC value
     * @param timestamp creation timestamp (milliseconds)
     */
    LLPFrame(LLPNode content, int crc, long timestamp) {
        this.content = (content != null) ? content : FinalNode.EMPTY;
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

    /**
     * Finds the first node of the given type in the frame hierarchy.
     *
     * @param type node class
     * @param <T>  node type
     * @return optional node instance
     */
    public <T extends LLPNode> Optional<T> getNode(Class<T> type) {
        LLPNode current = this.content;

        while (current != null) {
            if (type.isInstance(current)) {
                return Optional.of(type.cast(current));
            }
            current = current.getInnerNode().orElse(null);
        }

        return Optional.empty();
    }

    /**
     * Finds the first node with the given layer ID.
     *
     * @param id layer identifier
     * @return optional node
     */
    public Optional<LLPNode> getNode(int id) {
        LLPNode current = this.content;

        while (current != null) {
            if (current.getId() == id) {
                return Optional.of(current);
            }
            current = current.getInnerNode().orElse(null);
        }

        return Optional.empty();
    }

    /**
     * Returns the deepest (last) node in the hierarchy.
     *
     * @return last node
     */
    public LLPNode getDeepestNode() {
        LLPNode current = this.content;

        while (current.getInnerNode().isPresent()) {
            current = current.getInnerNode().get();
        }

        return current;
    }

    /**
     * Returns all nodes in traversal order (outer → inner).
     *
     * @return immutable list of nodes
     */
    public List<LLPNode> getNodes() {
        List<LLPNode> nodes = new ArrayList<>();
        LLPNode current = this.content;

        while (current != null) {
            nodes.add(current);
            current = current.getInnerNode().orElse(null);
        }

        return List.copyOf(nodes);
    }

    /**
     * Traverses all nodes using a visitor.
     *
     * @param consumer visitor builder
     */
    public void visitNodes(Consumer<LLPNodeVisitor> consumer) {
        LLPNodeVisitor visitor = new LLPNodeVisitor();
        consumer.accept(visitor);
        LLPNode current = content;

        while (current != null) {
            visitor.visit(current);
            current = current.getInnerNode().orElse(null);
        }
    }

    /**
     * Returns a string representation of the frame.
     */
    @Override
    public String toString() {
        return "LLPFrame{" +
                "crc=" + crc +
                ", timestamp=" + timestamp +
                ", nodes=" + getNodes() +
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
                Objects.equals(content, that.content);
    }

    /**
     * Returns the hash code of the frame.
     */
    @Override
    public int hashCode() {
        return Objects.hash(content, crc);
    }
}