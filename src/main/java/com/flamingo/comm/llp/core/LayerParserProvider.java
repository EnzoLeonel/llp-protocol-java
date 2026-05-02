package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPLayerParser;

import java.util.Optional;

/**
 * Functional interface used to resolve a {@link LLPLayerParser}
 * for a given layer identifier.
 *
 * <p>This abstraction allows decoupling the core parser logic from
 * the underlying mechanism used to discover or provide layer parsers.
 * It can be backed by a registry, dependency injection, or custom logic.</p>
 *
 * <p>Typical usage includes:</p>
 * <ul>
 *     <li>Default SPI-based lookup using {@link LayerRegistry}</li>
 *     <li>Custom providers for testing or controlled environments</li>
 * </ul>
 *
 * <p>This interface is designed to be lightweight and easily replaceable,
 * making it suitable for dependency injection and testing.</p>
 */
@FunctionalInterface
interface LayerParserProvider {

    /**
     * Returns a parser for the given layer identifier.
     *
     * @param layerId the layer identifier (1-255)
     * @return an {@link Optional} containing the corresponding parser if available,
     * or empty if the layer is not recognized
     */
    Optional<LLPLayerParser> get(int layerId);
}
