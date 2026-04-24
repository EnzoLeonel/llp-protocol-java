package com.flamingo.comm.llp.spi;

import java.nio.ByteBuffer;

/**
 * Represents the raw data of a single LLP layer during parsing.
 *
 * <p>This interface provides access to the metadata and payload sections
 * of a layer as {@link ByteBuffer} instances. It is used as the input
 * for {@link LLPLayerParser} implementations.</p>
 *
 * <h2>Design Goals</h2>
 * <ul>
 *     <li>Avoid unnecessary copying of data (zero-copy where possible).</li>
 *     <li>Provide a flexible and efficient way to access layer content.</li>
 *     <li>Allow implementations to decide whether to copy or process data in-place.</li>
 * </ul>
 *
 * <h2>Buffer Characteristics</h2>
 * <ul>
 *     <li>Buffers are never {@code null} but may be empty.</li>
 *     <li>Buffers are typically provided as <b>read-only</b> views.</li>
 *     <li>Implementations must treat buffers as immutable.</li>
 *     <li>If data needs to be retained beyond parsing, it should be copied.</li>
 * </ul>
 *
 * <h2>Usage Notes</h2>
 * <ul>
 *     <li>Calling {@link ByteBuffer#slice()} or {@link ByteBuffer#duplicate()}
 *     is recommended if position/limit changes are required.</li>
 *     <li>Modifying the buffer (if not read-only) leads to undefined behavior.</li>
 * </ul>
 *
 * @see LLPLayerParser
 */
public interface LayerData {

    /**
     * Returns the metadata buffer of the layer.
     *
     * <p>The metadata contains layer-specific information and may be empty.</p>
     *
     * @return a non-null {@link ByteBuffer} representing metadata
     */
    ByteBuffer metadata();

    /**
     * Returns the payload buffer of the layer.
     *
     * <p>The payload may contain another nested LLP layer or the final raw payload.</p>
     *
     * @return a non-null {@link ByteBuffer} representing payload
     */
    ByteBuffer payload();
}