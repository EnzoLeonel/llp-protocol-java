package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.BuildErrorReason;
import com.flamingo.comm.llp.spi.LLPLayerBuilder;
import com.flamingo.comm.llp.spi.LayerBuildPayload;
import com.flamingo.comm.llp.spi.LayerBuildResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ByteArrayFrameBuilderTest {

    private LLPLayerBuilder layer1;
    private LLPLayerBuilder layer2;

    private ByteArrayFrameBuilder builder;

    @BeforeEach
    void setup() {
        layer1 = mock(LLPLayerBuilder.class);
        layer2 = mock(LLPLayerBuilder.class);
    }

    @Test
    void shouldThrowExceptionWhenPayloadIsNull() {
        builder = new ByteArrayFrameBuilder(List.of());

        assertThrows(IllegalArgumentException.class, () -> builder.build(null));
    }

    @Test
    void shouldBuildFrameWithNoLayers() {
        builder = new ByteArrayFrameBuilder(List.of());

        ByteBuffer payload = ByteBuffer.wrap(new byte[]{0x11, 0x22});

        byte[] result = builder.build(payload);

        // Expected: [FINAL=0x00][payload]
        assertArrayEquals(new byte[]{0x00, 0x11, 0x22}, result);
    }

    @Test
    void shouldBuildSingleLayerUnmodifiedPayload() {
        when(layer1.getLayerId()).thenReturn(1);

        when(layer1.build(any())).thenAnswer(invocation -> {
            LayerBuildPayload p = invocation.getArgument(0);

            return new LayerBuildResult.Success.UnmodifiedPayload(
                    ByteBuffer.wrap(new byte[]{0x0A, 0x0B})
            );
        });

        builder = new ByteArrayFrameBuilder(List.of(layer1));

        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x55}));

        // [ID=1][LEN=2][0A 0B][FINAL][55]
        assertArrayEquals(new byte[]{
                0x01, 0x02, 0x0A, 0x0B,
                0x00,
                0x55
        }, result);
    }

    @Test
    void shouldBuildMultipleLayersUnmodified() {
        when(layer1.getLayerId()).thenReturn(1);
        when(layer2.getLayerId()).thenReturn(2);

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(new byte[]{0x11}))
        );

        when(layer2.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(new byte[]{0x22}))
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1, layer2));

        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x33}));

        // Outer layer should be layer2
        // [2][1][22][1][1][11][0][33]
        assertArrayEquals(new byte[]{
                0x02, 0x01, 0x22,
                0x01, 0x01, 0x11,
                0x00,
                0x33
        }, result);
    }

    @Test
    void shouldResetHeadersWhenPayloadIsTransformed() {
        when(layer1.getLayerId()).thenReturn(1);
        when(layer2.getLayerId()).thenReturn(2);

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(new byte[]{0x11}))
        );

        when(layer2.build(any())).thenReturn(
                new LayerBuildResult.Success.TransformedPayload(
                        ByteBuffer.wrap(new byte[]{0x22}),
                        ByteBuffer.wrap(new byte[]{0x66})
                )
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1, layer2));

        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x33}));

        // layer2 resets previous headers
        // [2][1][22][0][66]
        assertArrayEquals(new byte[]{
                0x02, 0x01, 0x22,
                0x00,
                0x66
        }, result);
    }

    @Test
    void shouldThrowFrameBuildExceptionOnFailure() {
        when(layer1.getLayerId()).thenReturn(1);

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Failure(TestBuildErrorReason.TEST_ERROR)
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1));

        FrameBuildException ex = assertThrows(
                FrameBuildException.class,
                () -> builder.build(ByteBuffer.wrap(new byte[]{0x01}))
        );

        assertEquals(1, ex.getLayerId());
        assertTrue(ex.getErrorReason().isPresent());
        assertEquals(TestBuildErrorReason.TEST_ERROR, ex.getErrorReason().get());
    }

    @Test
    void shouldHandleLargeMetadataUsingExtendedLength() {
        when(layer1.getLayerId()).thenReturn(1);

        byte[] meta = new byte[300];
        for (int i = 0; i < meta.length; i++) {
            meta[i] = (byte) i;
        }

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(meta))
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1));

        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x01}));

        // Check header manually
        assertEquals(0x01, result[0]); // ID
        assertEquals((byte) 0xFF, result[1]); // extended flag

        int len = ((result[2] & 0xFF) << 8) | (result[3] & 0xFF);
        assertEquals(300, len);
    }

    @Test
    void shouldPreservePayloadOrder() {
        when(layer1.getLayerId()).thenReturn(1);

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(new byte[]{0x01}))
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1));

        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x10, 0x20, 0x30}));

        assertArrayEquals(new byte[]{
                0x01, 0x01, 0x01,
                0x00,
                0x10, 0x20, 0x30
        }, result);
    }

    @Test
    void shouldPassPayloadToLayer() {
        when(layer1.getLayerId()).thenReturn(1);

        when(layer1.build(any())).thenAnswer(invocation -> {
            LayerBuildPayload payload = invocation.getArgument(0);

            ByteBuffer buffer = payload.payload();
            ByteBuffer dup = buffer.duplicate();
            byte[] data = new byte[dup.remaining()];
            dup.get(data);

            assertArrayEquals(new byte[]{0x55}, data);

            return new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.allocate(0));
        });

        builder = new ByteArrayFrameBuilder(List.of(layer1));

        builder.build(ByteBuffer.wrap(new byte[]{0x55}));
    }

    @Test
    void shouldSupportEmptyMetadata() {
        when(layer1.getLayerId()).thenReturn(1);

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.allocate(0))
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1));

        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x01}));

        assertArrayEquals(new byte[]{
                0x01, 0x00,
                0x00,
                0x01
        }, result);
    }

    @Test
    void shouldHandleMetadataLengthExactly254() {
        // Boundary test: Maximum length before extended flag
        when(layer1.getLayerId()).thenReturn(1);

        byte[] meta = new byte[254];
        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(meta))
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1));
        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x01}));

        assertEquals(0x01, result[0]); // ID
        assertEquals((byte) 254, result[1]); // normal length marker
        // Total size = 1 (ID) + 1 (LEN) + 254 (META) + 1 (FINAL) + 1 (PAYLOAD) = 258
        assertEquals(258, result.length);
    }

    @Test
    void shouldHandleMetadataLengthExactly255() {
        // Boundary test: Minimum length requiring extended flag
        when(layer1.getLayerId()).thenReturn(1);

        byte[] meta = new byte[255];
        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(meta))
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1));
        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x01}));

        assertEquals(0x01, result[0]); // ID
        assertEquals((byte) 0xFF, result[1]); // extended flag
        assertEquals((byte) 0x00, result[2]); // High byte (255 >> 8)
        assertEquals((byte) 0xFF, result[3]); // Low byte (255 & 0xFF)
        // Total size = 1 (ID) + 3 (LEN) + 255 (META) + 1 (FINAL) + 1 (PAYLOAD) = 261
        assertEquals(261, result.length);
    }

    @Test
    void shouldRespectByteBufferPositionAndRemaining() {
        when(layer1.getLayerId()).thenReturn(1);

        // Metadata buffer with offset
        ByteBuffer metaBuffer = ByteBuffer.wrap(new byte[]{0x00, (byte) 0xAA, (byte) 0xBB, 0x00});
        metaBuffer.position(1);
        metaBuffer.limit(3); // Only exposes [0xAA, 0xBB]

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(metaBuffer)
        );

        // Payload buffer with offset
        ByteBuffer payloadBuffer = ByteBuffer.wrap(new byte[]{(byte) 0xFF, 0x11, 0x22, 0x33, (byte) 0xFF});
        payloadBuffer.position(1);
        payloadBuffer.limit(4); // Only exposes [0x11, 0x22, 0x33]

        int initialPayloadPos = payloadBuffer.position();

        builder = new ByteArrayFrameBuilder(List.of(layer1));
        byte[] result = builder.build(payloadBuffer);

        assertArrayEquals(new byte[]{
                0x01, 0x02, (byte) 0xAA, (byte) 0xBB, // Layer 1
                0x00,                                 // Final
                0x11, 0x22, 0x33                      // Payload
        }, result);

        // Assert that the builder did not consume/mutate the original buffer's position
        assertEquals(initialPayloadPos, payloadBuffer.position());
    }

    @Test
    void shouldHandleMixedTransformationsCorrectly() {
        // Layer 1 (Inner): Just metadata
        LLPLayerBuilder layer3 = mock(LLPLayerBuilder.class);

        when(layer1.getLayerId()).thenReturn(1);
        when(layer2.getLayerId()).thenReturn(2);
        when(layer3.getLayerId()).thenReturn(3);

        // Layer 1 prepends [0x11]
        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(new byte[]{0x11}))
        );

        // Layer 2 modifies payload (Simulates encryption, squashing Layer 1)
        when(layer2.build(any())).thenReturn(
                new LayerBuildResult.Success.TransformedPayload(
                        ByteBuffer.wrap(new byte[]{0x22}), // its own metadata
                        ByteBuffer.wrap(new byte[]{(byte) 0x99})  // The mutated payload (which conceptually contains layer1+payload)
                )
        );

        // Layer 3 prepends [0x33] to the new payload
        when(layer3.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(new byte[]{0x33}))
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1, layer2, layer3));

        // Original payload is 0x00, but it gets eaten/mutated by layer 2
        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x00}));

        // Expected Outer-to-Inner representation:
        // [Layer 3 Header][Layer 2 Header][Final][Mutated Payload]
        // Note: Layer 1 is GONE from the final headers because Layer 2 "ate" it.
        assertArrayEquals(new byte[]{
                0x03, 0x01, 0x33, // Layer 3
                0x02, 0x01, 0x22, // Layer 2
                0x00,             // Final
                (byte) 0x99       // The Transformed Payload from Layer 2
        }, result);
    }

    @Test
    void shouldBuildFrameWithEmptyPayloadAndNoLayers() {
        builder = new ByteArrayFrameBuilder(List.of());

        byte[] result = builder.build(ByteBuffer.allocate(0));

        // Only the final marker, without payload
        assertArrayEquals(new byte[]{0x00}, result);
    }

    @Test
    void shouldNotMutateOriginalLayersListAfterConstruction() {
        // Verify that List.copyOf() isolates the builder from external modifications
        List<LLPLayerBuilder> mutableList = new java.util.ArrayList<>();
        builder = new ByteArrayFrameBuilder(mutableList);

        // Add a layer AFTER building the builder
        when(layer1.getLayerId()).thenReturn(1);
        mutableList.add(layer1);

        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x42}));

        // The builder should not have processed layer1
        assertArrayEquals(new byte[]{0x00, 0x42}, result);
        verify(layer1, never()).build(any());
    }

    @Test
    void shouldProduceSameOutputOnMultipleBuilds() {
        // The builder should be reusable
        when(layer1.getLayerId()).thenReturn(5);
        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(new byte[]{0x0A}))
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1));
        ByteBuffer input = ByteBuffer.wrap(new byte[]{0x55});

        byte[] first  = builder.build(input.duplicate());
        byte[] second = builder.build(input.duplicate());

        assertArrayEquals(first, second);
    }

    @Test
    void shouldThrowFrameBuildExceptionWithCorrectLayerIdWhenSecondLayerFails() {
        // Verify that the layerId in the exception corresponds to the failed layer,
        // not always the first one
        when(layer1.getLayerId()).thenReturn(1);
        when(layer2.getLayerId()).thenReturn(99);

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(new byte[]{0x11}))
        );
        when(layer2.build(any())).thenReturn(
                new LayerBuildResult.Failure(TestBuildErrorReason.TEST_ERROR)
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1, layer2));

        FrameBuildException ex = assertThrows(
                FrameBuildException.class,
                () -> builder.build(ByteBuffer.wrap(new byte[]{0x01}))
        );

        assertEquals(99, ex.getLayerId()); // should be layer2, not layer1
    }

    @Test
    void shouldCallEachLayerExactlyOnce() {
        when(layer1.getLayerId()).thenReturn(1);
        when(layer2.getLayerId()).thenReturn(2);
        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.allocate(0))
        );
        when(layer2.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.allocate(0))
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1, layer2));
        builder.build(ByteBuffer.wrap(new byte[]{0x01}));

        verify(layer1, times(1)).build(any());
        verify(layer2, times(1)).build(any());
    }

    @Test
    void shouldPassReadOnlyOrDuplicatePayloadToLayer() {
        // A plugin MUST NOT be able to mutate or consume the builder's currentPayload
        when(layer1.getLayerId()).thenReturn(1);
        when(layer1.build(any())).thenAnswer(invocation -> {
            LayerBuildPayload p = invocation.getArgument(0);
            ByteBuffer buf = p.payload();

            // Attempt to consume the buffer
            while (buf.hasRemaining()) buf.get();

            return new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.allocate(0));
        });

        builder = new ByteArrayFrameBuilder(List.of(layer1));

        // If the builder does not protect currentPayload, the frame will have no payload
        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x10, 0x20}));

        // The final payload must be present even if the plugin consumed its view
        byte[] expected = new byte[]{0x01, 0x00, 0x00, 0x10, 0x20};
        assertArrayEquals(expected, result);
    }

    @Test
    void shouldHandleFirstLayerAsTransformedPayload() {
        // TransformedPayload as the first (and only) layer
        when(layer1.getLayerId()).thenReturn(128);
        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.TransformedPayload(
                        ByteBuffer.wrap(new byte[]{(byte) 0xAA, (byte) 0xBB}), // metadata
                        ByteBuffer.wrap(new byte[]{(byte) 0xFF, (byte) 0xEE})  // transformed payload
                )
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1));

        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x01, 0x02, 0x03}));

        // [ID=128][LEN=2][AA BB][FINAL][FF EE]
        assertArrayEquals(new byte[]{
                (byte) 0x80, 0x02, (byte) 0xAA, (byte) 0xBB,
                0x00,
                (byte) 0xFF, (byte) 0xEE
        }, result);
    }

    @Test
    void shouldHandleTwoConsecutiveTransformedPayloadLayers() {
        // Two consecutive transforming layers — the second discards the first one's header
        when(layer1.getLayerId()).thenReturn(130);
        when(layer2.getLayerId()).thenReturn(131);

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.TransformedPayload(
                        ByteBuffer.wrap(new byte[]{0x01}),   // layer1 metadata
                        ByteBuffer.wrap(new byte[]{(byte) 0xEE})    // encrypted payload
                )
        );
        when(layer2.build(any())).thenReturn(
                new LayerBuildResult.Success.TransformedPayload(
                        ByteBuffer.wrap(new byte[]{0x02}),   // layer2 metadata
                        ByteBuffer.wrap(new byte[]{(byte) 0xFF})    // compressed payload
                )
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1, layer2));

        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x42}));

        // Layer2 discards layer1's header, just as layer1 discarded the previous header
        // [ID=131][LEN=1][02][FINAL][FF]
        assertArrayEquals(new byte[]{
                (byte) 0x83, 0x01, 0x02,
                0x00,
                (byte) 0xFF
        }, result);
    }

    @Test
    void shouldHandleTransformedPayloadWithEmptyNewPayload() {
        when(layer1.getLayerId()).thenReturn(128);
        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.TransformedPayload(
                        ByteBuffer.wrap(new byte[]{(byte) 0xAA}),
                        ByteBuffer.allocate(0) // empty transformed payload (edge case)
                )
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1));

        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{(byte) 0x99}));

        // [ID=128][LEN=1][AA][FINAL] — no payload
        assertArrayEquals(new byte[]{(byte) 0x80, 0x01, (byte) 0xAA, 0x00}, result);
    }

    @Test
    void shouldHandleMaxExtendedMetadataLength() {
        // 65535 bytes metadata (maximum of the 2-byte extended field)
        when(layer1.getLayerId()).thenReturn(1);

        byte[] meta = new byte[65535];
        Arrays.fill(meta, (byte) 0x7A);

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(meta))
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1));

        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x01}));

        // Verify the extended header
        assertEquals(0x01, result[0] & 0xFF);          // ID
        assertEquals(0xFF, result[1] & 0xFF);          // extended flag
        assertEquals(0xFF, result[2] & 0xFF);          // high byte of 65535
        assertEquals(0xFF, result[3] & 0xFF);          // low byte of 65535

        // Verify total size: 1(ID) + 3(LEN_EXT) + 65535(META) + 1(FINAL) + 1(PAYLOAD)
        assertEquals(65541, result.length);

        // Verify that metadata content is correct
        for (int i = 4; i < 4 + 65535; i++) {
            assertEquals((byte) 0x7A, result[i], "Metadata byte mismatch at index " + i);
        }
    }

    @Test
    void shouldPassTransformedPayloadToNextLayer() {
        // Verify that the next layer receives the transformed payload, not the original one
        LLPLayerBuilder layer3 = mock(LLPLayerBuilder.class);

        when(layer1.getLayerId()).thenReturn(128);
        when(layer2.getLayerId()).thenReturn(10);
        when(layer3.getLayerId()).thenReturn(11);

        byte[] transformedBytes = {(byte) 0xBE, (byte) 0xEF};

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.TransformedPayload(
                        ByteBuffer.wrap(new byte[]{0x01}),
                        ByteBuffer.wrap(transformedBytes)
                )
        );

        when(layer2.build(any())).thenAnswer(invocation -> {
            LayerBuildPayload p = invocation.getArgument(0);
            byte[] received = new byte[p.payload().remaining()];
            p.payload().duplicate().get(received);

            // Layer2 must receive the payload transformed by layer1
            assertArrayEquals(transformedBytes, received,
                    "layer2 must receive the transformed payload, not the original");

            return new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(new byte[]{0x02}));
        });

        when(layer3.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(new byte[]{0x03}))
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1, layer2, layer3));
        builder.build(ByteBuffer.wrap(new byte[]{0x42}));

        verify(layer2, times(1)).build(any());
    }

    @Test
    void shouldCorrectlyOutputLayerOrderWithMixedUnmodifiedTransformed() {
        // Full trace: verify exact position of each byte in the output
        // layers: [routing(unmod), encryption(transform), compression(unmod)]
        LLPLayerBuilder compressionLayer = mock(LLPLayerBuilder.class);

        when(layer1.getLayerId()).thenReturn(45);   // routing passthrough
        when(layer2.getLayerId()).thenReturn(130);  // encryption transform
        when(compressionLayer.getLayerId()).thenReturn(20); // compression passthrough

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(
                        ByteBuffer.wrap(new byte[]{0x01, 0x02}) // routing meta
                )
        );
        when(layer2.build(any())).thenReturn(
                new LayerBuildResult.Success.TransformedPayload(
                        ByteBuffer.wrap(new byte[]{0x10}),      // encryption meta
                        ByteBuffer.wrap(new byte[]{(byte) 0xC1, (byte) 0xC2}) // encrypted blob
                )
        );
        when(compressionLayer.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(
                        ByteBuffer.wrap(new byte[]{0x30}) // compression meta
                )
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1, layer2, compressionLayer));

        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{(byte) 0xFF}));

        // layer1 header was discarded by layer2's transformation
        // Outer → inner: compression → encryption → FINAL → encrypted blob
        assertArrayEquals(new byte[]{
                0x14, 0x01, 0x30,               // compression (ID=20, LEN=1, meta=[30])
                (byte) 0x82, 0x01, 0x10,        // encryption  (ID=130, LEN=1, meta=[10])
                0x00,                           // FINAL
                (byte) 0xC1, (byte) 0xC2        // encrypted payload
        }, result);
    }

    @Test
    void shouldHandleMetadataLengthExactly256WithExtendedFormat() {
        // 256 = first value requiring high byte != 0x00 in extended
        when(layer1.getLayerId()).thenReturn(1);

        byte[] meta = new byte[256];
        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(meta))
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1));
        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x01}));

        assertEquals(0x01, result[0] & 0xFF);  // ID
        assertEquals(0xFF, result[1] & 0xFF);  // extended flag
        assertEquals(0x01, result[2] & 0xFF);  // high byte of 256 (0x01)
        assertEquals(0x00, result[3] & 0xFF);  // low byte of 256 (0x00)

        // Total: 1 + 3 + 256 + 1 + 1 = 262
        assertEquals(262, result.length);
    }

    @Test
    void shouldNotInvokeAnyLayerWhenLayerListIsEmpty() {
        // With an empty list, no builder should be called
        builder = new ByteArrayFrameBuilder(List.of());

        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{0x42}));

        assertArrayEquals(new byte[]{0x00, 0x42}, result);
        // No mocks to verify, but the test confirms no exception is thrown
    }

    @Test
    void shouldStopAtFirstFailureWithoutCallingSubsequentLayers() {
        when(layer1.getLayerId()).thenReturn(1);
        when(layer2.getLayerId()).thenReturn(2);

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Failure(TestBuildErrorReason.TEST_ERROR)
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1, layer2));

        assertThrows(FrameBuildException.class,
                () -> builder.build(ByteBuffer.wrap(new byte[]{0x01}))
        );

        // layer2 should never have been called
        verify(layer2, never()).build(any());
    }

    @Test
    void shouldHandleLayerWithExactly254ByteMetadataAndVerifyRoundTrip() {
        // 254 is the last value without extended flag — test that the byte is not confused with 0xFF
        when(layer1.getLayerId()).thenReturn(1);

        byte[] meta = new byte[254];
        for (int i = 0; i < 254; i++) meta[i] = (byte) (i & 0xFF);

        when(layer1.build(any())).thenReturn(
                new LayerBuildResult.Success.UnmodifiedPayload(ByteBuffer.wrap(meta))
        );

        builder = new ByteArrayFrameBuilder(List.of(layer1));
        byte[] result = builder.build(ByteBuffer.wrap(new byte[]{(byte) 0xAB}));

        assertEquals(0x01,  result[0] & 0xFF); // ID
        assertEquals(254,   result[1] & 0xFF); // LEN without extended
        // Verify that byte 1 is NOT 0xFF (which would trigger extended in the parser)
        assertNotEquals(0xFF, result[1] & 0xFF);

        // Total: 1 + 1 + 254 + 1 + 1 = 258
        assertEquals(258, result.length);

        // Verify metadata content
        for (int i = 0; i < 254; i++) {
            assertEquals((byte)(i & 0xFF), result[2 + i]);
        }
    }

    private enum TestBuildErrorReason implements BuildErrorReason {
        TEST_ERROR("test-error");

        private final String reason;

        TestBuildErrorReason(String reason) {
            this.reason = reason;
        }

        @Override
        public String reason() {
            return this.reason;
        }
    }
}