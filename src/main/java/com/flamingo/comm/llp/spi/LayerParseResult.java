package com.flamingo.comm.llp.spi;

import java.nio.ByteBuffer;

/**
 * Represents the result of parsing a single LLP layer.
 *
 * <p>This sealed interface defines the contract used by {@link LLPLayerParser}
 * implementations to communicate the outcome of parsing a specific protocol layer.</p>
 *
 * <h2>Result Types</h2>
 * <ul>
 *     <li>{@link Success}: The layer was successfully parsed, producing an {@link LLPNode}
 *     and a payload (which may be transformed).</li>
 *     <li>{@link Failure}: The layer could not be parsed due to a logical or structural error.</li>
 * </ul>
 *
 * <h2>Design Notes</h2>
 * <ul>
 *     <li>This result operates at <b>layer level</b>, not at frame level.</li>
 *     <li>The core parser aggregates results into an LLPFrame.</li>
 *     <li>The payload in {@link Success} becomes the input for the next layer.</li>
 *     <li>Implementations may return the same buffer (zero-copy) or a new one if transformed.</li>
 *     <li>Buffers must be treated as <b>read-only</b>.</li>
 *     <li>This interface is immutable and thread-safe.</li>
 * </ul>
 */
public sealed interface LayerParseResult
        permits LayerParseResult.Success, LayerParseResult.Failure {

    default boolean isSuccess() {
        return this instanceof Success;
    }

    default boolean isFailure() {
        return this instanceof Failure;
    }

    /**
     * Successful parsing result for a layer.
     *
     * @param node    parsed node (never {@code null})
     * @param payload payload for next layer (never {@code null}, may be empty)
     */
    record Success(LLPNode node, ByteBuffer payload) implements LayerParseResult {

        public Success {
            if (node == null) {
                throw new IllegalArgumentException("node cannot be null");
            }
            if (payload == null) {
                throw new IllegalArgumentException("payload cannot be null");
            }
        }
    }

    /**
     * Failed parsing result.
     *
     * @param errorReason reason for failure (never {@code null})
     */
    record Failure(ParseErrorReason errorReason) implements LayerParseResult {

        public Failure {
            if (errorReason == null) {
                throw new IllegalArgumentException("errorReason cannot be null");
            }
        }
    }
}