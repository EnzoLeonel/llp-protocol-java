package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPLayerParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

public final class LLPLayerRegistry {
    private static final Map<Integer, LLPLayerParser> parsers = new HashMap<>();

    static {
        ServiceLoader<LLPLayerParser> loader = ServiceLoader.load(LLPLayerParser.class);
        for (LLPLayerParser parser : loader) {
            parsers.put(parser.getLayerId(), parser);
        }
    }

    public static Optional<LLPLayerParser> get(int id) {
        return Optional.ofNullable(parsers.get(id));
    }
}
