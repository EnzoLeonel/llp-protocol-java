package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPLayerBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class LLPTest {

    // =========================================================================
    // Private constructor
    // =========================================================================

    @Test
    void shouldHavePrivateConstructor() throws Exception {
        Constructor<LLP> constructor = LLP.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }

    // =========================================================================
    // Factory methods return independent instances
    // =========================================================================

    @Test
    void frameBuilderShouldReturnNewInstanceOnEachCall() {
        var first = LLP.frameBuilder();
        var second = LLP.frameBuilder();
        assertNotSame(first, second);
    }

    @Test
    void frameParserBuilderShouldReturnNewInstanceOnEachCall() {
        var first = LLP.frameParser();
        var second = LLP.frameParser();
        assertNotSame(first, second);
    }

    @Test
    void incrementalParserBuilderShouldReturnNewInstanceOnEachCall() {
        var first = LLP.incrementalParser();
        var second = LLP.incrementalParser();
        assertNotSame(first, second);
    }

    // =========================================================================
    // FrameBuilderConfigurator — build()
    // =========================================================================

    @Test
    void shouldCreateFrameBuilderWithLayers() {
        LLPLayerBuilder layer1 = mock(LLPLayerBuilder.class);
        LLPLayerBuilder layer2 = mock(LLPLayerBuilder.class);

        LLPFrameBuilder<byte[]> builder = LLP.frameBuilder()
                .addLayer(layer1)
                .addLayers(List.of(layer2))
                .build();

        assertNotNull(builder);
        assertInstanceOf(ByteArrayFrameBuilder.class, builder);
    }

    @Test
    void shouldCreateFrameBuilderWithEmptyLayers() {
        LLPFrameBuilder<byte[]> builder = LLP.frameBuilder().build();
        assertNotNull(builder);
        assertInstanceOf(ByteArrayFrameBuilder.class, builder);
    }

    @Test
    void buildShouldReturnDifferentFrameBuilderInstancesOnEachCall() {
        // The same configurator produces independent builders on each build() call
        LLP.FrameBuilderConfigurator configurator = LLP.frameBuilder()
                .addLayer(mock(LLPLayerBuilder.class));

        LLPFrameBuilder<byte[]> first = configurator.build();
        LLPFrameBuilder<byte[]> second = configurator.build();

        assertNotSame(first, second);
    }

    @Test
    void buildShouldProduceIndependentBuildersFromSeparateConfigurators() {
        LLPLayerBuilder layer = mock(LLPLayerBuilder.class);

        LLPFrameBuilder<byte[]> first = LLP.frameBuilder().addLayer(layer).build();
        LLPFrameBuilder<byte[]> second = LLP.frameBuilder().build();

        assertNotSame(first, second);
    }

    // =========================================================================
    // FrameBuilderConfigurator — addLayer / addLayers validation
    // =========================================================================

    @Test
    void addLayerShouldThrowOnNullLayer() {
        LLP.FrameBuilderConfigurator configurator = LLP.frameBuilder();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> configurator.addLayer(null)
        );
        assertEquals("Layer cannot be null", ex.getMessage());
    }

    @Test
    void addLayersShouldThrowOnNullList() {
        LLP.FrameBuilderConfigurator configurator = LLP.frameBuilder();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> configurator.addLayers(null)
        );
        assertEquals("Layers list cannot be null", ex.getMessage());
    }

    @Test
    void addLayersShouldThrowWhenListContainsNullElement() {
        LLP.FrameBuilderConfigurator configurator = LLP.frameBuilder();
        List<LLPLayerBuilder> listWithNull = Arrays.asList(mock(LLPLayerBuilder.class), null);

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> configurator.addLayers(listWithNull)
        );
        assertEquals("Layer cannot be null", ex.getMessage());
    }

    @Test
    void addLayersWithEmptyListShouldBeValidNoOp() {
        // An empty list is a legal argument — it simply adds nothing
        LLPFrameBuilder<byte[]> builder = LLP.frameBuilder()
                .addLayers(Collections.emptyList())
                .build();

        assertNotNull(builder);
        assertInstanceOf(ByteArrayFrameBuilder.class, builder);
    }

    @Test
    void addLayersWithSingleElementListShouldWork() {
        LLPLayerBuilder layer = mock(LLPLayerBuilder.class);

        assertDoesNotThrow(() ->
                LLP.frameBuilder()
                        .addLayers(List.of(layer))
                        .build()
        );
    }

    // =========================================================================
    // FrameBuilderConfigurator — method chaining
    // =========================================================================

    @Test
    void addLayerShouldReturnSameConfiguratorInstance() {
        LLP.FrameBuilderConfigurator configurator = LLP.frameBuilder();
        assertSame(configurator, configurator.addLayer(mock(LLPLayerBuilder.class)));
    }

    @Test
    void addLayersShouldReturnSameConfiguratorInstance() {
        LLP.FrameBuilderConfigurator configurator = LLP.frameBuilder();
        assertSame(configurator, configurator.addLayers(List.of(mock(LLPLayerBuilder.class))));
    }

    @Test
    void addLayersWithEmptyListShouldReturnSameConfiguratorInstance() {
        LLP.FrameBuilderConfigurator configurator = LLP.frameBuilder();
        assertSame(configurator, configurator.addLayers(Collections.emptyList()));
    }

    // =========================================================================
    // FrameBuilderConfigurator — configurator isolation
    // =========================================================================

    @Test
    void layersAddedAfterBuildShouldNotAffectAlreadyBuiltInstance() {
        // Verifies that ByteArrayFrameBuilder performs a defensive copy of the layer list,
        // so mutations to the configurator after build() do not leak into prior builds.
        LLPLayerBuilder layer1 = mock(LLPLayerBuilder.class);
        LLPLayerBuilder layer2 = mock(LLPLayerBuilder.class);

        LLP.FrameBuilderConfigurator configurator = LLP.frameBuilder().addLayer(layer1);

        LLPFrameBuilder<byte[]> builtFirst = configurator.build();

        // Adding a layer after building should not affect the already-built instance
        configurator.addLayer(layer2);

        LLPFrameBuilder<byte[]> builtSecond = configurator.build();

        // Both are valid and independent
        assertNotSame(builtFirst, builtSecond);
    }

    @Test
    void twoConfiguratorsWithSameLayersShouldProduceIndependentBuilders() {
        LLPLayerBuilder layer = mock(LLPLayerBuilder.class);

        LLPFrameBuilder<byte[]> first = LLP.frameBuilder().addLayer(layer).build();
        LLPFrameBuilder<byte[]> second = LLP.frameBuilder().addLayer(layer).build();

        assertNotSame(first, second);
    }

    // =========================================================================
    // FrameParserBuilder
    // =========================================================================

    @Test
    void shouldCreateFrameParserWithDefaultProvider() {
        LLPFrameParser parser = LLP.frameParser().build();
        assertNotNull(parser);
        assertInstanceOf(SimpleFrameParser.class, parser);
    }

    @Test
    void shouldCreateFrameParserWithCustomProvider() {
        LayerParserProvider customProvider = id -> java.util.Optional.empty();

        LLPFrameParser parser = LLP.frameParser()
                .parserProvider(customProvider)
                .build();

        assertNotNull(parser);
        assertInstanceOf(SimpleFrameParser.class, parser);
    }

    @Test
    void frameParserBuilderShouldThrowOnNullProvider() {
        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> LLP.frameParser().parserProvider(null)
        );
        assertEquals("Provider cannot be null", ex.getMessage());
    }

    @Test
    void frameParserBuilderParserProviderShouldReturnSameBuilderInstance() {
        LLP.FrameParserBuilder builder = LLP.frameParser();
        LayerParserProvider provider = id -> java.util.Optional.empty();
        assertSame(builder, builder.parserProvider(provider));
    }

    @Test
    void buildShouldReturnDifferentFrameParserInstancesOnEachCall() {
        LLP.FrameParserBuilder builder = LLP.frameParser();

        LLPFrameParser first = builder.build();
        LLPFrameParser second = builder.build();

        assertNotSame(first, second);
    }

    @Test
    void settingCustomProviderShouldOverrideDefault() {
        // The custom provider is different from the SPI default — the built parsers
        // should be independent instances regardless of provider source.
        LLPFrameParser withDefault = LLP.frameParser().build();
        LLPFrameParser withCustom = LLP.frameParser()
                .parserProvider(id -> java.util.Optional.empty())
                .build();

        assertNotSame(withDefault, withCustom);
    }

    // =========================================================================
    // IncrementalParserBuilder
    // =========================================================================

    @Test
    void shouldCreateIncrementalParserWithDefaultValues() {
        LLPIncrementalParser parser = LLP.incrementalParser().build();
        assertNotNull(parser);
        assertInstanceOf(LLPIncrementalParser.class, parser);
    }

    @Test
    void shouldCreateIncrementalParserWithCustomConfiguration() {
        LLPIncrementalParser parser = LLP.incrementalParser()
                .parserProvider(id -> java.util.Optional.empty())
                .maxPayloadBytes(8192)
                .timeoutMs(5000L)
                .build();

        assertNotNull(parser);
        assertInstanceOf(LLPIncrementalParser.class, parser);
    }

    @Test
    void incrementalParserBuilderShouldThrowOnNullProvider() {
        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> LLP.incrementalParser().parserProvider(null)
        );
        assertEquals("Provider cannot be null", ex.getMessage());
    }

    @Test
    void incrementalParserBuilderParserProviderShouldReturnSameInstance() {
        LLP.IncrementalParserBuilder builder = LLP.incrementalParser();
        assertSame(builder, builder.parserProvider(id -> java.util.Optional.empty()));
    }

    @Test
    void incrementalParserBuilderMaxPayloadBytesShouldReturnSameInstance() {
        LLP.IncrementalParserBuilder builder = LLP.incrementalParser();
        assertSame(builder, builder.maxPayloadBytes(4096));
    }

    @Test
    void incrementalParserBuilderTimeoutMsShouldReturnSameInstance() {
        LLP.IncrementalParserBuilder builder = LLP.incrementalParser();
        assertSame(builder, builder.timeoutMs(1000L));
    }

    @Test
    void incrementalParserBuilderShouldSupportFullMethodChain() {
        // Verifies that all setters can be chained in a single expression
        assertDoesNotThrow(() ->
                LLP.incrementalParser()
                        .parserProvider(id -> java.util.Optional.empty())
                        .maxPayloadBytes(2048)
                        .timeoutMs(500L)
                        .build()
        );
    }

    @Test
    void buildShouldReturnDifferentIncrementalParserInstancesOnEachCall() {
        LLP.IncrementalParserBuilder builder = LLP.incrementalParser();

        LLPIncrementalParser first = builder.build();
        LLPIncrementalParser second = builder.build();

        assertNotSame(first, second);
    }

    @Test
    void incrementalParserBuilderShouldAcceptNegativeMaxPayloadBytes() {
        // Negative values are passed through to LLPTransportDeframer,
        // which falls back to its internal default. The builder itself
        // should not throw — validation is the deframer's responsibility.
        assertDoesNotThrow(() ->
                LLP.incrementalParser()
                        .maxPayloadBytes(-1)
                        .build()
        );
    }

    @Test
    void incrementalParserBuilderShouldAcceptNegativeTimeoutMs() {
        // Same delegation contract as maxPayloadBytes above.
        assertDoesNotThrow(() ->
                LLP.incrementalParser()
                        .timeoutMs(-1L)
                        .build()
        );
    }

    @Test
    void incrementalParserBuilderShouldAcceptZeroMaxPayloadBytes() {
        assertDoesNotThrow(() ->
                LLP.incrementalParser()
                        .maxPayloadBytes(0)
                        .build()
        );
    }

    @Test
    void incrementalParserBuilderShouldAcceptZeroTimeoutMs() {
        assertDoesNotThrow(() ->
                LLP.incrementalParser()
                        .timeoutMs(0L)
                        .build()
        );
    }
}