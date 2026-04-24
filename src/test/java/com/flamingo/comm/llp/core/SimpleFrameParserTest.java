package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPLayerParser;
import com.flamingo.comm.llp.spi.LLPNode;
import com.flamingo.comm.llp.spi.LayerData;
import com.flamingo.comm.llp.spi.LayerParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimpleFrameParserTest {

    @Mock
    private LayerParserProvider provider;

    @Mock
    private LLPRawFrame rawFrame;

    @Mock
    private LLPLayerParser mockLayerParser;

    @Mock
    private LLPNode mockNode;

    private SimpleFrameParser parser;

    @BeforeEach
    void setUp() {
        parser = new SimpleFrameParser(provider);
    }

    @Test
    void shouldThrowExceptionWhenRawFrameIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(null)
        );
        assertEquals("rawFrame cannot be null", exception.getMessage());
    }

    @Test
    void shouldParseFrameWithOnlyFinalLayer() {
        // Frame: [0x00 (Final ID), 0xAA, 0xBB (Payload)]
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{0x00, (byte) 0xAA, (byte) 0xBB});
        setupRawFrame(payload, 1234, 1000L);

        LLPFrame frame = parser.parse(rawFrame);

        assertNotNull(frame);
        assertEquals(1234, frame.crc());
        assertEquals(1000L, frame.timestamp());

        // Node chain should have exactly 1 node (FinalNode)
        assertEquals(1, frame.chain().size());
        assertInstanceOf(FinalNode.class, frame.chain().asList().getFirst());
    }

    @Test
    void shouldSkipUnknownNonCriticalLayer() {
        // Frame: [0x01 (ID 1 < 128), 0x02 (Meta Len), 0xFF, 0xFF (Meta), 0x00 (Final), 0xDD]
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{0x01, 0x02, (byte) 0xFF, (byte) 0xFF, 0x00, (byte) 0xDD});
        setupRawFrame(payload, 0, 0L);

        // Provider returns empty for ID 1
        when(provider.get(1)).thenReturn(Optional.empty());

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(2, frame.chain().size());
        assertInstanceOf(UnknownNode.class, frame.chain().asList().getFirst());
        assertInstanceOf(FinalNode.class, frame.chain().asList().get(1));
    }

    @Test
    void shouldAbortOnUnknownCriticalLayer() {
        // Frame: [0x85 (ID 133 >= 128), 0x00 (Meta Len), 0x00 (Final ID)]
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{(byte) 0x85, 0x00, 0x00});
        setupRawFrame(payload, 0, 0L);

        when(provider.get(133)).thenReturn(Optional.empty());

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());
        FailureNode failureNode = (FailureNode) frame.chain().asList().getFirst();
        assertEquals(133, failureNode.getId());
        assertEquals(CoreParseErrorReason.UNKNOWN_CRITICAL_LAYER, failureNode.getErrorReason());
    }

    @Test
    void shouldFailWhenMetadataLengthIsMalformed() {
        // Frame: [0x05 (ID 5), 0x04 (Meta Len), 0xAA, 0xBB (Only 2 bytes of meta, malformed!)]
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{0x05, 0x04, (byte) 0xAA, (byte) 0xBB});
        setupRawFrame(payload, 0, 0L);

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());
        FailureNode failureNode = (FailureNode) frame.chain().asList().getFirst();
        assertEquals(5, failureNode.getId());
        assertEquals(CoreParseErrorReason.METADATA_TRUNCATED, failureNode.getErrorReason());
    }

    @Test
    void shouldParseExtendedMetadataLength() {
        // Frame: [0x02 (ID), 0xFF (Ext Flag), 0x00, 0x03 (Len = 3), 0xAA, 0xBB, 0xCC, 0x00 (Final)]
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x02, (byte) 0xFF, 0x00, 0x03, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, 0x00
        });
        setupRawFrame(payload, 0, 0L);

        when(provider.get(2)).thenReturn(Optional.empty()); // Just skip it to verify length logic

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(2, frame.chain().size());
        UnknownNode unknownNode = (UnknownNode) frame.chain().asList().getFirst();
        assertEquals(2, unknownNode.getId());
        assertEquals(3, unknownNode.getMetadata().remaining()); // Successfully parsed 3 bytes of metadata
    }

    @Test
    void shouldProcessSuccessfulPluginParse() {
        // Frame: [0x10 (ID 16), 0x01 (Meta Len), 0xAA (Meta), 0x00 (Final ID)]
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{0x10, 0x01, (byte) 0xAA, 0x00});
        setupRawFrame(payload, 0, 0L);

        when(provider.get(16)).thenReturn(Optional.of(mockLayerParser));

        // Plugin returns success and passes the remaining buffer (which is just 0x00)
        LayerParseResult.Success successResult = new LayerParseResult.Success(
                mockNode,
                ByteBuffer.wrap(new byte[]{0x00})
        );
        when(mockLayerParser.parse(any(LayerData.class))).thenReturn(successResult);

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(2, frame.chain().size());
        assertEquals(mockNode, frame.chain().asList().getFirst());
        assertInstanceOf(FinalNode.class, frame.chain().asList().get(1));
    }

    @Test
    void shouldHandlePluginExceptionAndProtectCore() {
        // Frame: [0x05 (ID 5 < 128), 0x00 (Meta Len), 0x00 (Final)]
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{0x05, 0x00, 0x00});
        setupRawFrame(payload, 0, 0L);

        when(provider.get(5)).thenReturn(Optional.of(mockLayerParser));
        when(mockLayerParser.parse(any(LayerData.class))).thenThrow(new RuntimeException("Simulated plugin crash"));

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(2, frame.chain().size());

        // First node should be a Failure Node due to the crash
        FailureNode failureNode = (FailureNode) frame.chain().asList().getFirst();
        assertEquals(CoreParseErrorReason.PLUGIN_EXCEPTION, failureNode.getErrorReason());

        // Since ID 5 is skippable, the parser should recover and parse the FinalNode
        assertInstanceOf(FinalNode.class, frame.chain().asList().get(1));
    }

    @Test
    void shouldFailWhenExtendedMetadataLengthIsIncomplete() {
        // [ID, 0xFF, only 1 byte instead of 2 for extended length]
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x05, (byte) 0xFF, 0x01
        });

        setupRawFrame(payload, 0, 0L);

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());

        FailureNode node = (FailureNode) frame.chain().asList().getFirst();
        assertEquals(5, node.getId());
        assertEquals(CoreParseErrorReason.LAYER_TOO_SHORT, node.getErrorReason());
    }

    @Test
    void shouldIncludeMetadataInUnknownNode() {
        // [ID, metaLen=2, meta(AA BB), no payload]
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x05, 0x02, (byte) 0xAA, (byte) 0xBB
        });

        setupRawFrame(payload, 0, 0L);

        LLPFrame frame = parser.parse(rawFrame);

        UnknownNode node = (UnknownNode) frame.chain().asList().getFirst();

        ByteBuffer metadata = node.getMetadata();
        byte[] extracted = new byte[metadata.remaining()];
        metadata.get(extracted);

        assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xBB}, extracted);
    }

    @Test
    void shouldIncludeMetadataAndStopOnFailureInNonSkippableLayer() {
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                (byte) 0x85, 0x02, (byte) 0xAA, (byte) 0xBB, 0x00
        });

        setupRawFrame(payload, 0, 0L);

        when(provider.get(133)).thenReturn(Optional.of(mockLayerParser));

        when(mockLayerParser.parse(any())).thenReturn(
                new LayerParseResult.Failure(CoreParseErrorReason.METADATA_TRUNCATED)
        );

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());

        FailureNode node = (FailureNode) frame.chain().asList().getFirst();

        ByteBuffer metadata = node.getMetadata();
        byte[] extracted = new byte[metadata.remaining()];
        metadata.get(extracted);

        assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xBB}, extracted);
    }

    @Test
    void shouldIncludeMetadataInFailureNodeWhenPluginFails() {
        // Frame:
        // [ID=5, metaLen=2, meta=AA BB, payload=00 (final)]
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x05, 0x02, (byte) 0xAA, (byte) 0xBB, 0x00
        });

        setupRawFrame(payload, 0, 0L);

        when(provider.get(5)).thenReturn(Optional.of(mockLayerParser));

        // Plugin fails
        when(mockLayerParser.parse(any())).thenReturn(
                new LayerParseResult.Failure(CoreParseErrorReason.METADATA_TRUNCATED)
        );

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(2, frame.chain().size());

        FailureNode node = (FailureNode) frame.chain().asList().getFirst();

        assertEquals(5, node.getId());
        assertEquals(CoreParseErrorReason.METADATA_TRUNCATED, node.getErrorReason());

        // Verify preserved metadata
        ByteBuffer metadata = node.getMetadata();
        byte[] extracted = new byte[metadata.remaining()];
        metadata.get(extracted);

        assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xBB}, extracted);
    }

    @Test
    void shouldStopParsingWhenPluginReturnsEmptyPayload() {
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x10, 0x00 // Layer 16
        });

        setupRawFrame(payload, 0, 0L);

        when(provider.get(16)).thenReturn(Optional.of(mockLayerParser));

        when(mockLayerParser.parse(any())).thenReturn(
                new LayerParseResult.Success(mockNode, ByteBuffer.allocate(0))
        );

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());
        assertEquals(mockNode, frame.chain().asList().getFirst());
    }

    @Test
    void shouldRejectNullPayloadInSuccess() {
        assertThrows(IllegalArgumentException.class,
                () -> new LayerParseResult.Success(mockNode, null)
        );
    }

    @Test
    void shouldCapturePluginExceptionAsFailureNode() {
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x10, 0x00
        });

        setupRawFrame(payload, 0, 0L);

        when(provider.get(16)).thenReturn(Optional.of(mockLayerParser));

        when(mockLayerParser.parse(any())).thenThrow(IllegalArgumentException.class);

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());
        assertEquals(FailureNode.class, frame.chain().asList().getFirst().getClass());

        FailureNode failureNode = (FailureNode) frame.chain().asList().getFirst();

        assertEquals(CoreParseErrorReason.PLUGIN_EXCEPTION, failureNode.getErrorReason());
        assertEquals(IllegalArgumentException.class, failureNode.getCause().orElseThrow(() -> new IllegalStateException("NodeChain must have at least 1 failure node")).getClass());
    }

    @Test
    void shouldStopOnFailureInNonSkippableLayer() {
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                (byte) 0x85, 0x00 // ID >= 128
        });

        setupRawFrame(payload, 0, 0L);

        when(provider.get(133)).thenReturn(Optional.of(mockLayerParser));

        when(mockLayerParser.parse(any())).thenReturn(
                new LayerParseResult.Failure(CoreParseErrorReason.METADATA_TRUNCATED)
        );

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());

        FailureNode node = (FailureNode) frame.chain().asList().getFirst();
        assertEquals(133, node.getId());
    }

    @Test
    void shouldContinueAfterFailureInSkippableLayer() {
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x05, 0x00, // Layer 5
                0x00        // Final
        });

        setupRawFrame(payload, 0, 0L);

        when(provider.get(5)).thenReturn(Optional.of(mockLayerParser));

        when(mockLayerParser.parse(any())).thenReturn(
                new LayerParseResult.Failure(CoreParseErrorReason.METADATA_TRUNCATED)
        );

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(2, frame.chain().size());

        assertInstanceOf(FailureNode.class, frame.chain().asList().getFirst());
        assertInstanceOf(FinalNode.class, frame.chain().asList().get(1));
    }

    @Test
    void shouldPassReadOnlyBuffersToPlugin() {
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x10, 0x01, 0x01, 0x00
        });

        setupRawFrame(payload, 0, 0L);

        when(provider.get(16)).thenReturn(Optional.of(mockLayerParser));

        when(mockLayerParser.parse(any())).thenAnswer(invocation -> {
            LayerData data = invocation.getArgument(0);

            assertTrue(data.metadata().isReadOnly());
            assertTrue(data.payload().isReadOnly());

            return new LayerParseResult.Success(mockNode, ByteBuffer.wrap(new byte[]{0x00}));
        });

        parser.parse(rawFrame);
    }

    @Test
    void shouldReturnEmptyChainWhenPayloadIsEmpty() {
        setupRawFrame(ByteBuffer.allocate(0), 42, 999L);

        LLPFrame frame = parser.parse(rawFrame);

        assertNotNull(frame);
        assertTrue(frame.chain().asList().isEmpty());
        // CRC and timestamp are preserved even if the payload is empty
        assertEquals(42, frame.crc());
        assertEquals(999L, frame.timestamp());
    }

    @Test
    void shouldReturnLayerTooShortWhenBufferEndsAfterLayerId() {
        // Only the ID byte, without the metaLen byte
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{0x05});
        setupRawFrame(payload, 0, 0L);

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());
        FailureNode node = (FailureNode) frame.chain().asList().getFirst();
        assertEquals(5, node.getId());
        assertEquals(CoreParseErrorReason.LAYER_TOO_SHORT, node.getErrorReason());
        assertTrue(node.getCause().isEmpty());
    }

    @Test
    void shouldReturnLayerTooShortWhenExtendedFlagHasZeroRemainingBytes() {
        // [ID=5, 0xFF] — nothing after the extended flag
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{0x05, (byte) 0xFF});
        setupRawFrame(payload, 0, 0L);

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());
        FailureNode node = (FailureNode) frame.chain().asList().getFirst();
        assertEquals(5, node.getId());
        assertEquals(CoreParseErrorReason.LAYER_TOO_SHORT, node.getErrorReason());
    }

    @Test
    void shouldReturnMalformedWhenExtendedMetadataLengthExceedsAvailableBytes() {
        // metaLen = 256 (0x01, 0x00 big-endian) but there are only 3 metadata bytes
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x02, (byte) 0xFF, 0x01, 0x00,         // ID=2, extended, len=256
                (byte) 0xAA, (byte) 0xBB, (byte) 0xCC  // Only 3 bytes, 253 missing
        });
        setupRawFrame(payload, 0, 0L);

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());
        FailureNode node = (FailureNode) frame.chain().asList().getFirst();
        assertEquals(2, node.getId());
        assertEquals(CoreParseErrorReason.METADATA_TRUNCATED, node.getErrorReason());
    }

    @Test
    void shouldHandleExtendedMetadataWithZeroLength() {
        // [ID=2, 0xFF, 0x00, 0x00 (len=0), 0x00 (final)]
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x02, (byte) 0xFF, 0x00, 0x00, 0x00
        });
        setupRawFrame(payload, 0, 0L);
        when(provider.get(2)).thenReturn(Optional.empty());

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(2, frame.chain().size());
        UnknownNode node = (UnknownNode) frame.chain().asList().getFirst();
        assertEquals(2, node.getId());
        assertEquals(0, node.getMetadata().remaining()); // Empty but valid metadata
        assertInstanceOf(FinalNode.class, frame.chain().asList().get(1));
    }

    @Test
    void shouldParseMultipleConsecutiveUnknownSkippableLayers() {
        // [ID=1, meta=AA, ID=2, meta=BB, ID=3, meta=CC, Final]
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x01, 0x01, (byte) 0xAA,
                0x02, 0x01, (byte) 0xBB,
                0x03, 0x01, (byte) 0xCC,
                0x00
        });
        setupRawFrame(payload, 0, 0L);
        when(provider.get(1)).thenReturn(Optional.empty());
        when(provider.get(2)).thenReturn(Optional.empty());
        when(provider.get(3)).thenReturn(Optional.empty());

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(4, frame.chain().size());
        assertInstanceOf(UnknownNode.class, frame.chain().asList().get(0));
        assertInstanceOf(UnknownNode.class, frame.chain().asList().get(1));
        assertInstanceOf(UnknownNode.class, frame.chain().asList().get(2));
        assertInstanceOf(FinalNode.class,   frame.chain().asList().get(3));

        assertEquals(1, ((UnknownNode) frame.chain().asList().get(0)).getId());
        assertEquals(2, ((UnknownNode) frame.chain().asList().get(1)).getId());
        assertEquals(3, ((UnknownNode) frame.chain().asList().get(2)).getId());
    }

    @Test
    void shouldParseChainedKnownLayersViaPluginReturnedPayload() {
        // Plugin A (ID=16) returns a payload containing another layer (ID=32)
        LLPLayerParser secondLayerParser = mock(LLPLayerParser.class);
        LLPNode secondNode = mock(LLPNode.class);

        ByteBuffer framePayload = ByteBuffer.wrap(new byte[]{0x10, 0x00});
        setupRawFrame(framePayload, 0, 0L);

        when(provider.get(16)).thenReturn(Optional.of(mockLayerParser));
        when(provider.get(32)).thenReturn(Optional.of(secondLayerParser));

        // Plugin A processes its layer and returns the remaining payload (containing ID=32)
        ByteBuffer innerPayload = ByteBuffer.wrap(new byte[]{0x20, 0x00, 0x00});
        when(mockLayerParser.parse(any()))
                .thenReturn(new LayerParseResult.Success(mockNode, innerPayload));

        // Plugin B processes layer ID=32 and returns the FinalNode payload
        when(secondLayerParser.parse(any()))
                .thenReturn(new LayerParseResult.Success(secondNode, ByteBuffer.wrap(new byte[]{0x00})));

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(3, frame.chain().size());
        assertEquals(mockNode,   frame.chain().asList().get(0));
        assertEquals(secondNode, frame.chain().asList().get(1));
        assertInstanceOf(FinalNode.class, frame.chain().asList().get(2));
    }

    @Test
    void shouldStopParsingAfterNonSkippableUnknownLayerIgnoringRemainingBytes() {
        // [ID=133 (>=128, no handler), meta=0, ID=1 (skippable), meta=0, Final=0]
        // The second layer and the final node should not appear in the chain
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                (byte) 0x85, 0x00,   // ID=133, empty meta
                0x01, 0x00,          // ID=1
                0x00                 // Final
        });
        setupRawFrame(payload, 0, 0L);
        when(provider.get(133)).thenReturn(Optional.empty());

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());
        FailureNode node = (FailureNode) frame.chain().asList().getFirst();
        assertEquals(133, node.getId());
        assertEquals(CoreParseErrorReason.UNKNOWN_CRITICAL_LAYER, node.getErrorReason());
    }

    @Test
    void shouldHandleMaxNonExtendedMetadataLength() {
        // metaLen = 254 (maximum without extended flag, since 255 triggers extended mode)
        int metaLen = 254;
        byte[] metaBytes = new byte[metaLen];
        Arrays.fill(metaBytes, (byte) 0x7E);

        ByteBuffer payload = ByteBuffer.allocate(2 + metaLen + 1);
        payload.put((byte) 0x02);      // ID=2
        payload.put((byte) metaLen);   // 254, does NOT trigger extended mode
        payload.put(metaBytes);
        payload.put((byte) 0x00);      // Final
        payload.flip();

        setupRawFrame(payload, 0, 0L);
        when(provider.get(2)).thenReturn(Optional.empty());

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(2, frame.chain().size());
        UnknownNode node = (UnknownNode) frame.chain().asList().getFirst();
        assertEquals(metaLen, node.getMetadata().remaining());

        byte[] extractedMeta = new byte[metaLen];
        node.getMetadata().duplicate().get(extractedMeta);
        assertArrayEquals(metaBytes, extractedMeta);

        assertInstanceOf(FinalNode.class, frame.chain().asList().get(1));
    }

    @Test
    void shouldHandleKnownLayerWithEmptyMetadata() {
        // Valid plugin with metaLen=0 — empty metadata is legal
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{0x10, 0x00, 0x00});
        setupRawFrame(payload, 0, 0L);
        when(provider.get(16)).thenReturn(Optional.of(mockLayerParser));

        when(mockLayerParser.parse(any())).thenAnswer(invocation -> {
            LayerData data = invocation.getArgument(0);
            assertEquals(0, data.metadata().remaining()); // Empty metadata
            return new LayerParseResult.Success(mockNode, ByteBuffer.wrap(new byte[]{0x00}));
        });

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(2, frame.chain().size());
        assertEquals(mockNode, frame.chain().asList().getFirst());
        assertInstanceOf(FinalNode.class, frame.chain().asList().get(1));
    }

    @Test
    void shouldPreserveCorrectMetadataBytesInUnknownNode() {
        // Ensures that UnknownNode stores exactly the metadata bytes, neither more nor less
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x01, 0x04, 0x11, 0x22, 0x33, 0x44, // ID=1, meta=[11 22 33 44]
                0x00                                // Final
        });
        setupRawFrame(payload, 0, 0L);
        when(provider.get(1)).thenReturn(Optional.empty());

        LLPFrame frame = parser.parse(rawFrame);

        UnknownNode node = (UnknownNode) frame.chain().asList().getFirst();
        byte[] extracted = new byte[node.getMetadata().remaining()];
        node.getMetadata().duplicate().get(extracted);

        assertArrayEquals(new byte[]{0x11, 0x22, 0x33, 0x44}, extracted);
    }

    @Test
    void shouldPreserveMetadataBytesInFailureNodeWhenLayerTooShortAfterMetaLenRead() {
        // When metaLen is read but there are not enough metadata bytes,
        // the FailureNode MUST NOT have metadata (could not be read)
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x05, 0x05,               // ID=5, metaLen=5
                (byte) 0xAA, (byte) 0xBB  // Only 2 bytes available, 3 missing
        });
        setupRawFrame(payload, 0, 0L);

        LLPFrame frame = parser.parse(rawFrame);

        FailureNode node = (FailureNode) frame.chain().asList().getFirst();
        assertEquals(5, node.getId());
        assertEquals(CoreParseErrorReason.METADATA_TRUNCATED, node.getErrorReason());
        // No metadata available — the FailureNode metadata should be empty
        assertEquals(0, node.getMetadata().remaining());
    }

    @Test
    void shouldHandleBoundaryLayerId127AsSkippable() {
        // ID=127 is the last passthrough value (< 128)
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{0x7F, 0x00, 0x00});
        setupRawFrame(payload, 0, 0L);
        when(provider.get(127)).thenReturn(Optional.empty());

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(2, frame.chain().size());
        assertInstanceOf(UnknownNode.class, frame.chain().asList().getFirst());
        assertEquals(127, ((UnknownNode) frame.chain().asList().getFirst()).getId());
        assertInstanceOf(FinalNode.class, frame.chain().asList().get(1));
    }

    @Test
    void shouldHandleBoundaryLayerId128AsNonSkippable() {
        // ID=128 is the first non-skippable value (>= 128)
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{(byte) 0x80, 0x00, 0x00});
        setupRawFrame(payload, 0, 0L);
        when(provider.get(128)).thenReturn(Optional.empty());

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());
        FailureNode node = (FailureNode) frame.chain().asList().getFirst();
        assertEquals(128, node.getId());
        assertEquals(CoreParseErrorReason.UNKNOWN_CRITICAL_LAYER, node.getErrorReason());
    }

    @Test
    void shouldHandleFinalLayerAtStartWithNoRawBytes() {
        // [0x00] only — FinalNode with empty payload
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{0x00});
        setupRawFrame(payload, 0, 0L);

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());
        assertInstanceOf(FinalNode.class, frame.chain().asList().getFirst());
    }

    @Test
    void shouldRecoverAndParseFinalNodeAfterMultipleSkippablePluginFailures() {
        // Two plugins fail on consecutive skippable layers, then the FinalNode appears
        LLPLayerParser secondLayerParser = mock(LLPLayerParser.class);

        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x05, 0x00, // ID=5, empty meta
                0x06, 0x00, // ID=6, empty meta
                0x00        // Final
        });
        setupRawFrame(payload, 0, 0L);

        when(provider.get(5)).thenReturn(Optional.of(mockLayerParser));
        when(provider.get(6)).thenReturn(Optional.of(secondLayerParser));

        when(mockLayerParser.parse(any())).thenReturn(
                new LayerParseResult.Failure(CoreParseErrorReason.METADATA_TRUNCATED)
        );
        when(secondLayerParser.parse(any())).thenReturn(
                new LayerParseResult.Failure(CoreParseErrorReason.METADATA_TRUNCATED)
        );

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(3, frame.chain().size());
        assertInstanceOf(FailureNode.class, frame.chain().asList().get(0));
        assertInstanceOf(FailureNode.class, frame.chain().asList().get(1));
        assertInstanceOf(FinalNode.class,   frame.chain().asList().get(2));

        assertEquals(5, ((FailureNode) frame.chain().asList().get(0)).getId());
        assertEquals(6, ((FailureNode) frame.chain().asList().get(1)).getId());
    }

    @Test
    void shouldProtectCoreFromPluginExceptionOnNonSkippableLayer() {
        // Plugin in non-skippable layer throws exception — the core must stop
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                (byte) 0x85, 0x00, // ID=133, non-skippable
                0x01, 0x00, 0x00   // More layers that should NOT be processed
        });
        setupRawFrame(payload, 0, 0L);
        when(provider.get(133)).thenReturn(Optional.of(mockLayerParser));
        when(mockLayerParser.parse(any())).thenThrow(new RuntimeException("plugin crash"));

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(1, frame.chain().size());
        FailureNode node = (FailureNode) frame.chain().asList().getFirst();
        assertEquals(133, node.getId());
        assertEquals(CoreParseErrorReason.PLUGIN_EXCEPTION, node.getErrorReason());
        assertTrue(node.getCause().isPresent());
        assertInstanceOf(RuntimeException.class, node.getCause().get());
    }

    @Test
    void shouldPassMetadataWithCorrectBoundsToPlugin() {
        // Verifies that the plugin receives exactly the specified metadata bytes
        // and that the payload starts immediately after
        ByteBuffer payload = ByteBuffer.wrap(new byte[]{
                0x10,                            // ID=16
                0x03,                            // metaLen=3
                0x11, 0x22, 0x33,                // Metadata
                0x00, (byte) 0xAB, (byte) 0xCD   // Final + raw bytes
        });
        setupRawFrame(payload, 0, 0L);
        when(provider.get(16)).thenReturn(Optional.of(mockLayerParser));

        when(mockLayerParser.parse(any())).thenAnswer(invocation -> {
            LayerData data = invocation.getArgument(0);

            // Metadata must be exactly [11 22 33]
            assertEquals(3, data.metadata().remaining());
            byte[] meta = new byte[3];
            data.metadata().duplicate().get(meta);
            assertArrayEquals(new byte[]{0x11, 0x22, 0x33}, meta);

            // Payload must start at [00 AB CD]
            assertTrue(data.payload().hasRemaining());
            assertEquals(0x00, data.payload().duplicate().get() & 0xFF);

            return new LayerParseResult.Success(mockNode, data.payload().asReadOnlyBuffer());
        });

        LLPFrame frame = parser.parse(rawFrame);

        assertEquals(2, frame.chain().size());
        assertInstanceOf(FinalNode.class, frame.chain().asList().get(1));
    }

    /**
     * Helper method to stub the raw frame safely.
     */
    private void setupRawFrame(ByteBuffer payload, int crc, long timestamp) {
        when(rawFrame.payload()).thenReturn(payload);
        when(rawFrame.crc()).thenReturn(crc);
        when(rawFrame.timestamp()).thenReturn(timestamp);
    }
}