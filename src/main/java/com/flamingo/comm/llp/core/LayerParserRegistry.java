package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPLayerParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Registry of {@link LLPLayerParser} implementations indexed by their layer ID.
 *
 * <p>This registry is responsible for holding and providing access to all available
 * {@link LLPLayerParser} instances. Parsers are indexed using their unique
 * layer identifier, as defined by {@link LLPLayerParser#getLayerId()}.</p>
 *
 * <h2>Initialization</h2>
 * <ul>
 *     <li>In production, the default instance is lazily initialized using
 *     {@link ServiceLoader} to discover implementations via Java SPI.</li>
 *     <li>For testing purposes, custom instances can be created using
 *     {@link #createForTest(Iterable)}.</li>
 * </ul>
 *
 * <h2>Constraints</h2>
 * <ul>
 *     <li>Each parser must declare a unique layer ID.</li>
 *     <li>If duplicate IDs are detected during initialization, an
 *     {@link IllegalStateException} is thrown.</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is immutable after construction and safe for concurrent access.</p>
 *
 * <h2>Design Notes</h2>
 * <ul>
 *     <li>The registry is intentionally decoupled from {@link ServiceLoader}
 *     by accepting an {@link Iterable}, improving testability and flexibility.</li>
 *     <li>The internal storage is an immutable {@link Map}, ensuring that
 *     parser definitions cannot be modified after initialization.</li>
 * </ul>
 */
final class LayerParserRegistry {

    private final Map<Integer, LLPLayerParser> parsers;

    /**
     * Creates a new registry from the provided parsers.
     *
     * <p>This constructor is private to enforce controlled creation through
     * factory methods.</p>
     *
     * @param loadedParsers an iterable collection of parsers to register
     * @throws IllegalStateException if two parsers declare the same layer ID
     */
    private LayerParserRegistry(Iterable<LLPLayerParser> loadedParsers) {
        Map<Integer, LLPLayerParser> tempMap = new HashMap<>();

        for (LLPLayerParser parser : loadedParsers) {
            int parserId = parser.getLayerId();
            if (tempMap.containsKey(parserId)) {
                throw new IllegalStateException(
                        "Duplicate layer ID detected: " + parserId +
                                " for parsers [" + tempMap.get(parserId).getClass().getName() +
                                ", " + parser.getClass().getName() + "]"
                );
            }
            tempMap.put(parserId, parser);
        }

        this.parsers = Map.copyOf(tempMap);
    }

    /**
     * Returns the default registry instance initialized via Java SPI.
     *
     * <p>This method uses a lazy-loaded singleton pattern to ensure that
     * parsers are discovered only once and initialization is thread-safe.</p>
     *
     * @return the singleton {@code LayerParserRegistry} instance
     */
    static LayerParserRegistry getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Creates a registry instance using the provided parsers.
     *
     * <p>This method is intended for testing purposes, allowing callers to
     * bypass SPI discovery and provide controlled parser instances.</p>
     *
     * @param parsers the parsers to register
     * @return a new {@code LayerParserRegistry} instance
     * @throws IllegalStateException if duplicate layer IDs are found
     */
    static LayerParserRegistry createForTest(Iterable<LLPLayerParser> parsers) {
        return new LayerParserRegistry(parsers);
    }

    /**
     * Returns the parser associated with the given layer ID.
     *
     * @param id the layer identifier
     * @return an {@link Optional} containing the parser if present,
     *         or empty if no parser is registered for the given ID
     */
    Optional<LLPLayerParser> get(int id) {
        return Optional.ofNullable(parsers.get(id));
    }

    private static final class Holder {
        static final LayerParserRegistry INSTANCE =
                new LayerParserRegistry(ServiceLoader.load(LLPLayerParser.class));
    }
}