package com.flamingo.comm.llp.core;

import java.nio.ByteBuffer;

/**
 * Core contract for building an LLP frame from a given payload.
 *
 * <p>An {@code LLPFrameBuilder} is responsible for applying a sequence of
 * {@link com.flamingo.comm.llp.spi.LLPLayerBuilder} instances in order,
 * producing a fully serialized frame representation.</p>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *     <li><b>Layered composition:</b> Each configured layer contributes metadata
 *     and may optionally transform the payload.</li>
 *     <li><b>Payload propagation:</b> The output of one layer becomes the input
 *     of the next.</li>
 *     <li><b>Single materialization:</b> Implementations are encouraged to avoid
 *     intermediate copies and perform the final byte assembly in a single pass.</li>
 *     <li><b>Pluggable output:</b> The result type {@code T} allows different
 *     representations (e.g., {@code byte[]}, {@code ByteBuffer}, scatter/gather
 *     buffers, etc.).</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * <p>If any layer fails during the build process, the implementation must
 * abort and throw a {@link FrameBuildException}. Partial results must not
 * be returned.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>Implementations are not required to be thread-safe unless explicitly stated.
 * External synchronization may be required if reused across threads.</p>
 *
 * @param <T> the type of the final frame representation
 */
public interface LLPFrameBuilder<T> {

    /**
     * Builds the final frame representation based on the configured layers
     * and the provided initial payload.
     *
     * <p>The given payload represents the innermost data. Each configured
     * layer wraps this payload, optionally transforming it and attaching
     * metadata, until the outermost frame is produced.</p>
     *
     * @param payload the initial payload to be wrapped by the configured layers;
     *                must not be {@code null}
     * @return the fully assembled frame in the configured output format
     * @throws IllegalArgumentException if {@code payload} is {@code null}
     * @throws FrameBuildException      if any layer fails to build or if the layer
     *                                  chain produces an invalid frame
     */
    T build(ByteBuffer payload);
}