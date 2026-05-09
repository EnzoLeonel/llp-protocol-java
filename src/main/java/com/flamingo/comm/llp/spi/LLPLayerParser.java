package com.flamingo.comm.llp.spi;

import java.util.ServiceLoader;

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
 * {@link ServiceLoader} mechanism.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *     <li>Declare the layer identifier via {@link #getLayerId()}.</li>
 *     <li>Parse raw layer data into a domain-specific {@link LLPNode}.</li>
 *     <li>Interpret metadata according to the layer's internal specification.</li>
 * </ul>
 *
 * <h2>Contract</h2>
 * <ul>
 *     <li>The {@code layerId} must be unique across all registered layers.</li>
 *     <li>The core LLP parser guarantees that metadata and payload are already
 *     extracted according to the protocol format.</li>
 *     <li>The provided {@link LayerParseInput} buffers must be treated as <b>read-only</b>.</li>
 *     <li>Implementations must not rely on buffer mutability or shared state.</li>
 *     <li>If parsing fails, implementations should return a {@link LayerParseResult.Failure}
 *     or throw an exception if the failure is unexpected.</li>
 * </ul>
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 *     <li>The use of {@link java.nio.ByteBuffer} allows zero-copy parsing.</li>
 *     <li>Implementations should avoid copying data unless necessary.</li>
 *     <li>If data needs to be retained, it must be explicitly copied.</li>
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
 *     public LayerParseResult parse(LayerParseInput data) {
 *         ByteBuffer metadata = data.metadata();
 *         ByteBuffer payload = data.payload();
 *
 *         // Interpret metadata (e.g., algorithm, IV, etc.)
 *         EncryptionNode node = new EncryptionNode(metadata, payload);
 *
 *         return new LayerParseResult.Success(node, payload);
 *     }
 * }
 * }</pre>
 *
 * <p>
 * The resulting {@link LLPNode} will be integrated into the {@code NodeChain}
 * by the core parser.
 * </p>
 *
 * @see LLPNode
 * @see LayerParseInput
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
     * The core LLP parser provides a {@link LayerParseInput} instance containing
     * the extracted metadata and payload buffers according to the protocol:
     * </p>
     *
     * <pre>
     * [LAYER_ID][METADATA_LENGTH][METADATA][PAYLOAD]
     * </pre>
     *
     * <p>
     * Implementations should interpret the metadata and construct an appropriate
     * {@link LLPNode}, optionally transforming the payload for the next layer.
     * </p>
     *
     * @param layerParseInput container with metadata and payload buffers (never {@code null})
     * @return a {@link LayerParseResult} describing the outcome of the parsing
     */
    LayerParseResult parse(LayerParseInput layerParseInput);
}