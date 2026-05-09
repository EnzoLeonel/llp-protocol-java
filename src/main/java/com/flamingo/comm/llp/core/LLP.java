package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPLayerBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Main entry point and factory facade for the LLP protocol library.
 *
 * <p>This class provides access to builders for creating LLP frame builders,
 * frame parsers, and incremental parsers.</p>
 *
 * <h2>Provided Components</h2>
 * <ul>
 *     <li>{@link LLPFrameBuilder} for outbound frame construction</li>
 *     <li>{@link LLPFrameParser} for parsing complete frames</li>
 *     <li>{@link LLPIncrementalParser} for streaming/incremental parsing</li>
 * </ul>
 *
 * <h2>Design Goals</h2>
 * <ul>
 *     <li>Simple and minimal public API</li>
 *     <li>Separation of inbound and outbound responsibilities</li>
 *     <li>Immutable runtime components after construction</li>
 *     <li>Support for plugin-based layer discovery via SPI</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * LLPFrameBuilder<byte[]> builder = LLP.frameBuilder()
 *         .addLayer(new MyLayerBuilder())
 *         .build();
 *
 * byte[] frame = builder.build(payloadBuffer);
 * }</pre>
 *
 * <pre>{@code
 * LLPFrameParser parser = LLP.frameParser()
 *         .build();
 * }</pre>
 *
 * <pre>{@code
 * LLPIncrementalParser incremental = LLP.incrementalParser()
 *         .maxPayloadBytes(4096)
 *         .timeoutMs(1000)
 *         .build();
 * }</pre>
 */
public final class LLP {

    private LLP() {
        // Utility class
    }

    /**
     * Creates a new configurator for an {@link LLPFrameBuilder}.
     */
    public static FrameBuilderConfigurator frameBuilder() {
        return new FrameBuilderConfigurator();
    }

    /**
     * Creates a new builder for configuring an {@link LLPFrameParser}.
     */
    public static FrameParserBuilder frameParser() {
        return new FrameParserBuilder();
    }

    /**
     * Creates a new builder for configuring an {@link LLPIncrementalParser}.
     */
    public static IncrementalParserBuilder incrementalParser() {
        return new IncrementalParserBuilder();
    }

    /**
     * Configurator used to setup and create {@link LLPFrameBuilder} instances.
     */
    public static final class FrameBuilderConfigurator {

        private final List<LLPLayerBuilder> layers = new ArrayList<>();

        private FrameBuilderConfigurator() {}

        public FrameBuilderConfigurator addLayer(LLPLayerBuilder layer) {
            layers.add(Objects.requireNonNull(layer, "Layer cannot be null"));
            return this;
        }

        public FrameBuilderConfigurator addLayers(List<LLPLayerBuilder> layers) {
            Objects.requireNonNull(layers, "Layers list cannot be null");
            layers.forEach(this::addLayer);
            return this;
        }

        public LLPFrameBuilder<byte[]> build() {
            // Se asume que ByteArrayFrameBuilder hace una copia defensiva de 'layers'
            return new ByteArrayFrameBuilder(layers);
        }
    }

    /**
     * Builder used to configure and create {@link LLPFrameParser} instances.
     */
    public static final class FrameParserBuilder {

        private LayerParserProvider provider = LayerParserRegistry.getInstance()::get;

        private FrameParserBuilder() {}

        public FrameParserBuilder parserProvider(LayerParserProvider provider) {
            this.provider = Objects.requireNonNull(provider, "Provider cannot be null");
            return this;
        }

        public LLPFrameParser build() {
            return new SimpleFrameParser(provider);
        }
    }

    /**
     * Builder used to configure and create {@link LLPIncrementalParser} instances.
     */
    public static final class IncrementalParserBuilder {

        private LayerParserProvider provider = LayerParserRegistry.getInstance()::get;
        private int maxPayloadBytes = -1;
        private long timeoutMs = -1;

        private IncrementalParserBuilder() {}

        public IncrementalParserBuilder parserProvider(LayerParserProvider provider) {
            this.provider = Objects.requireNonNull(provider, "Provider cannot be null");
            return this;
        }

        public IncrementalParserBuilder maxPayloadBytes(int bytes) {
            this.maxPayloadBytes = bytes;
            return this;
        }

        public IncrementalParserBuilder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public LLPIncrementalParser build() {
            return new LLPIncrementalParser(provider, maxPayloadBytes, timeoutMs);
        }
    }
}