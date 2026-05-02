package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPLayerParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Default registry of {@link LLPLayerParser} implementations discovered via Java SPI.
 *
 * <p>This class uses {@link ServiceLoader} to automatically load all available
 * implementations of {@link LLPLayerParser} present on the classpath at runtime.</p>
 *
 * <p>Each parser is indexed by its unique layer identifier, as defined by
 * {@link LLPLayerParser#getLayerId()}.</p>
 *
 * <p>This registry is typically used as the default {@link LayerParserProvider}
 * in the LLP core parser, enabling a plugin-based architecture where external
 * libraries can contribute new protocol layers.</p>
 *
 * <p><b>Important considerations:</b></p>
 * <ul>
 *     <li>Layer IDs must be unique across all loaded parsers</li>
 *     <li>If multiple parsers declare the same ID, the last one loaded will overwrite the previous</li>
 *     <li>Parsers are loaded once at class initialization time</li>
 * </ul>
 *
 * <p>This class is thread-safe for read operations after initialization.</p>
 */
final class LayerRegistry {

    private static final Map<Integer, LLPLayerParser> parsers = new HashMap<>();

    static {
        ServiceLoader<LLPLayerParser> loader = ServiceLoader.load(LLPLayerParser.class);
        for (LLPLayerParser parser : loader) {

            int parserId = parser.getLayerId();
            if (parsers.containsKey(parserId)) {
                throw new IllegalStateException("Duplicate layer ID: " + parserId);
            }

            parsers.put(parserId, parser);
        }
    }

    private LayerRegistry() {
        // Utility class - no instances allowed
    }

    /**
     * Returns the parser associated with the given layer ID.
     *
     * @param id the layer identifier
     * @return an {@link Optional} containing the parser if found,
     *         or empty if no parser is registered for the given ID
     */
    static Optional<LLPLayerParser> get(int id) {
        return Optional.ofNullable(parsers.get(id));
    }
}
