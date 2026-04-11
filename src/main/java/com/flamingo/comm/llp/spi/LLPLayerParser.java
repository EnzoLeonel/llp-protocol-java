package com.flamingo.comm.llp.spi;

/**
 * Service Provider Interface (SPI) for parsing LLP protocol layers.
 *
 * <p>
 * Implementations of this interface are responsible for interpreting
 * a specific layer within the LLP protocol stack.
 * Each layer is identified by a unique {@code layerId} and is parsed
 * from its raw metadata and payload components.
 * </p>
 *
 * <p>
 * This interface is intended to be implemented by external libraries
 * (plugins) that extend the LLP protocol with additional functionality
 * such as encryption, compression, routing, etc.
 * </p>
 *
 * <p>
 * Implementations are typically discovered at runtime using Java's
 * {@link java.util.ServiceLoader} mechanism.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *     <li>Declare the layer identifier via {@link #getLayerId()}.</li>
 *     <li>Parse raw metadata and payload into a domain-specific {@link LLPNode}.</li>
 *     <li>Interpret metadata according to the layer's internal specification.</li>
 * </ul>
 *
 * <h2>Contract</h2>
 * <ul>
 *     <li>The {@code layerId} must be unique across all registered layers.</li>
 *     <li>The core LLP parser guarantees that metadata and payload are already
 *     correctly extracted according to the protocol format.</li>
 *     <li>The implementation must not modify the provided byte arrays.</li>
 *     <li>If parsing fails, the implementation should throw a runtime exception
 *     or return a fallback node, depending on the design choice.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public class EncryptionLayerParser implements LLPLayerParser {
 *
 *     @Override
 *     public int getLayerId() {
 *         return 10;
 *     }
 *
 *     @Override
 *     public LLPNode parse(byte[] metadata, byte[] payload) {
 *         // Interpret metadata (e.g., algorithm, IV, etc.)
 *         return new EncryptionNode(metadata, payload);
 *     }
 * }
 * }</pre>
 *
 * <p>
 * The returned {@link LLPNode} will be integrated into the {@code LLPNodeChain}
 * by the core parser.
 * </p>
 *
 * @see LLPNode
 * @see java.util.ServiceLoader
 */
public interface LLPLayerParser {

    /**
     * Returns the unique identifier of the layer handled by this parser.
     *
     * <p>
     * This value must match the {@code LAYER_ID} field present in the LLP frame.
     * </p>
     *
     * @return layer identifier (1-255)
     */
    int getLayerId();

    /**
     * Parses a layer from its raw metadata and payload.
     *
     * <p>
     * The core LLP parser is responsible for extracting the metadata and payload
     * based on the protocol specification:
     * </p>
     *
     * <pre>
     * [LAYER_ID][METADATA_LENGTH][METADATA][PAYLOAD]
     * </pre>
     *
     * <p>
     * This method should interpret the metadata and construct an appropriate
     * {@link LLPNode} implementation.
     * </p>
     *
     * @param metadata raw metadata bytes (never {@code null}, may be empty)
     * @param payload  raw payload bytes (never {@code null}, may be empty)
     * @return parsed {@link LLPNode} representing this layer
     */
    LLPNode parse(byte[] metadata, byte[] payload);
}