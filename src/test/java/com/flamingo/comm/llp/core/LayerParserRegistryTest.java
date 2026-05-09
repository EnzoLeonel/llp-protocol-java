package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPLayerParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LayerParserRegistryTest {

    @Test
    void shouldInitializeEmptyWhenNoParsersProvided() {
        // Arrange
        Iterable<LLPLayerParser> emptyLoad = List.of();

        // Act
        LayerParserRegistry registry = LayerParserRegistry.createForTest(emptyLoad);

        // Assert
        assertTrue(registry.get(1).isEmpty());
    }

    @Test
    void shouldRegisterAndRetrieveParsersSuccessfully() {
        // Arrange
        LLPLayerParser parser1 = mock(LLPLayerParser.class);
        when(parser1.getLayerId()).thenReturn(10);

        LLPLayerParser parser2 = mock(LLPLayerParser.class);
        when(parser2.getLayerId()).thenReturn(20);

        Iterable<LLPLayerParser> validLoad = List.of(parser1, parser2);

        // Act
        LayerParserRegistry registry = LayerParserRegistry.createForTest(validLoad);

        // Assert
        Optional<LLPLayerParser> retrieved1 = registry.get(10);
        assertTrue(retrieved1.isPresent());
        assertEquals(parser1, retrieved1.get());

        Optional<LLPLayerParser> retrieved2 = registry.get(20);
        assertTrue(retrieved2.isPresent());
        assertEquals(parser2, retrieved2.get());

        assertTrue(registry.get(99).isEmpty());
    }

    @Test
    void shouldThrowExceptionWhenDuplicateLayerIdsAreDetected() {
        // Arrange
        LLPLayerParser parser1 = mock(LLPLayerParser.class);
        when(parser1.getLayerId()).thenReturn(5);

        LLPLayerParser parser2 = mock(LLPLayerParser.class);

        when(parser2.getLayerId()).thenReturn(5);

        Iterable<LLPLayerParser> duplicateLoad = List.of(parser1, parser2);

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> LayerParserRegistry.createForTest(duplicateLoad)
        );

        assertTrue(exception.getMessage().contains("5"));
    }

    @Test
    void shouldBeIndependentFromInputCollection() {
        LLPLayerParser parser = mock(LLPLayerParser.class);
        when(parser.getLayerId()).thenReturn(1);

        List<LLPLayerParser> list = new java.util.ArrayList<>();
        list.add(parser);

        LayerParserRegistry registry = LayerParserRegistry.createForTest(list);

        list.clear();

        assertTrue(registry.get(1).isPresent());
    }

    @Test
    void shouldThrowWhenParserIsNull() {
        List<LLPLayerParser> list = new ArrayList<>();
        list.add(null);

        assertThrows(NullPointerException.class,
                () -> LayerParserRegistry.createForTest(list));
    }

    @Test
    void shouldHandleNegativeLayerIds() {
        LLPLayerParser parser = mock(LLPLayerParser.class);
        when(parser.getLayerId()).thenReturn(-1);

        LayerParserRegistry registry =
                LayerParserRegistry.createForTest(List.of(parser));

        assertTrue(registry.get(-1).isPresent());
    }

    @Test
    void shouldReturnSameInstanceForSameId() {
        LLPLayerParser parser = mock(LLPLayerParser.class);
        when(parser.getLayerId()).thenReturn(42);

        LayerParserRegistry registry =
                LayerParserRegistry.createForTest(List.of(parser));

        assertSame(parser, registry.get(42).orElseThrow());
    }

    @Test
    void shouldOnlyCallGetLayerIdDuringRegistration() {
        LLPLayerParser parser = mock(LLPLayerParser.class);
        when(parser.getLayerId()).thenReturn(1);

        LayerParserRegistry.createForTest(List.of(parser));

        verify(parser, times(1)).getLayerId();
        verifyNoMoreInteractions(parser);
    }

    @Test
    void shouldAllowMultipleIndependentRegistries() {
        LLPLayerParser p1 = mock(LLPLayerParser.class);
        when(p1.getLayerId()).thenReturn(1);

        LLPLayerParser p2 = mock(LLPLayerParser.class);
        when(p2.getLayerId()).thenReturn(2);

        LayerParserRegistry r1 = LayerParserRegistry.createForTest(List.of(p1));
        LayerParserRegistry r2 = LayerParserRegistry.createForTest(List.of(p2));

        assertTrue(r1.get(1).isPresent());
        assertTrue(r2.get(2).isPresent());

        assertTrue(r1.get(2).isEmpty());
        assertTrue(r2.get(1).isEmpty());
    }

    @Test
    void shouldReturnSingletonInstance() {
        LayerParserRegistry instance1 = LayerParserRegistry.getInstance();
        LayerParserRegistry instance2 = LayerParserRegistry.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2);
    }
}
