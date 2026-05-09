package com.flamingo.comm.llp.spi;

/**
 * Contract for building (serializing) a specific LLP layer.
 *
 * <p>Implementations of this interface are responsible for constructing
 * the metadata and optionally transforming the payload of a layer
 * during the frame building process.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *     <li>Provide the unique {@code layerId} that identifies the layer in the LLP protocol.</li>
 *     <li>Generate the layer-specific metadata.</li>
 *     <li>Optionally transform the payload (e.g., encryption, compression).</li>
 * </ul>
 *
 * <h2>Payload Handling</h2>
 * <ul>
 *     <li>The input payload represents the output of the previous (inner) layer.</li>
 *     <li>Implementations may either:
 *         <ul>
 *             <li>Leave the payload unchanged, returning {@link LayerBuildResult.Success.UnmodifiedPayload}, or</li>
 *             <li>Return a modified payload using {@link LayerBuildResult.Success.TransformedPayload}.</li>
 *         </ul>
 *     </li>
 *     <li>When no transformation is needed, implementations should avoid creating new buffers
 *     and reuse the provided payload where possible.</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <ul>
 *     <li>Logical or domain-specific failures should be reported using {@link LayerBuildResult.Failure}.</li>
 *     <li>Unexpected exceptions should be avoided. If thrown, they will typically be handled by the core builder.</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <ul>
 *     <li>Implementations are expected to be stateless or thread-safe, as they may be reused
 *     across multiple build operations.</li>
 * </ul>
 *
 * @see LayerBuildPayload
 * @see LayerBuildResult
 */
public interface LLPLayerBuilder {

    /**
     * Returns the unique identifier of the layer.
     *
     * <p>This value will be serialized as the {@code LAYERID} byte
     * in the LLP frame.</p>
     *
     * @return the layer identifier (0-255)
     */
    int getLayerId();

    /**
     * Builds the current layer using the provided payload.
     *
     * <p>The given {@link LayerBuildPayload} represents the input data
     * produced by the previous layer in the build chain.</p>
     *
     * @param payload the input payload (never {@code null})
     * @return the result of the build operation (never {@code null})
     */
    LayerBuildResult build(LayerBuildPayload payload);
}