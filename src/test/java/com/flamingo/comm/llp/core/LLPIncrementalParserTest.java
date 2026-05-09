package com.flamingo.comm.llp.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class LLPIncrementalParserTest {

    // Shared provider that recognizes no layers — valid for transport-only tests
    private static final LayerParserProvider EMPTY_PROVIDER = id -> Optional.empty();

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds a complete, valid LLP transport frame whose LLP payload contains
     * a FinalNode marker (0x00) followed by the given raw bytes.
     * <p>
     * The result is suitable for feeding directly into an LLPIncrementalParser.
     */
    private static byte[] buildValidFrame(byte... rawData) {
        byte[] llpPayload = new byte[1 + rawData.length];
        llpPayload[0] = 0x00; // FinalNode marker
        System.arraycopy(rawData, 0, llpPayload, 1, rawData.length);
        return LLPTransportFramer.buildSafe(llpPayload);
    }

    /**
     * Builds a transport-level byte sequence that passes magic/length parsing
     * but carries a deliberately wrong CRC, triggering CHECKSUM_INVALID.
     * <p>
     * Payload is a single byte (0x42). No byte in the sequence equals 0xAA,
     * so no byte stuffing is needed and the structure is straightforward.
     */
    private static byte[] buildFrameWithBadCrc() {
        return new byte[]{
                (byte) 0xAA, 0x55,   // magic
                0x01, 0x00,           // length = 1
                0x42,                 // payload byte
                0x00, 0x00            // wrong CRC
        };
    }

    /**
     * Accesses the private FrameListener registered inside the parser's deframer
     * via reflection, allowing direct unit testing of the listener callbacks
     * without going through the full transport stack.
     */
    @SuppressWarnings("unchecked")
    private static LLPTransportDeframer.LLPFrameListener extractListener(
            LLPIncrementalParser parser) throws Exception {

        Field deframerField = LLPIncrementalParser.class.getDeclaredField("deframer");
        deframerField.setAccessible(true);
        LLPTransportDeframer deframer = (LLPTransportDeframer) deframerField.get(parser);

        Field listenersField = LLPTransportDeframer.class.getDeclaredField("listeners");
        listenersField.setAccessible(true);
        Queue<LLPTransportDeframer.LLPFrameListener> listeners =
                (Queue<LLPTransportDeframer.LLPFrameListener>) listenersField.get(deframer);

        assertFalse(listeners.isEmpty(), "FrameListener was not registered in the deframer");
        return listeners.peek();
    }

    // =========================================================================
    // Construction
    // =========================================================================

    @Test
    void shouldCreateParserSuccessfully() {
        assertNotNull(new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1));
    }

    @Test
    void shouldCreateParserWithCustomConfiguration() {
        assertNotNull(new LLPIncrementalParser(EMPTY_PROVIDER, 8192, 5000L));
    }

    // =========================================================================
    // Initial state
    // =========================================================================

    @Test
    void shouldReturnEmptyFramesWhenNothingWasFed() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        List<LLPFrame> frames = parser.pollFrames();
        assertNotNull(frames);
        assertTrue(frames.isEmpty());
    }

    @Test
    void shouldReturnEmptyErrorsWhenNothingWasFed() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        List<TransportErrorCode> errors = parser.pollErrors();
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldReturnImmutableFrameList() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        List<LLPFrame> frames = parser.pollFrames();
        assertThrows(UnsupportedOperationException.class, () -> frames.add(null));
    }

    @Test
    void shouldReturnImmutableErrorList() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        List<TransportErrorCode> errors = parser.pollErrors();
        assertThrows(UnsupportedOperationException.class,
                () -> errors.add(TransportErrorCode.CHECKSUM_INVALID));
    }

    // =========================================================================
    // feed() — basic contracts
    // =========================================================================

    @Test
    void feedSingleByteShouldNotThrow() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        assertDoesNotThrow(() -> parser.feed((byte) 0x01));
    }

    @Test
    void feedByteArrayShouldNotThrow() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        assertDoesNotThrow(() -> parser.feed(new byte[]{0x01, 0x02, 0x03}));
    }

    @Test
    void feedEmptyByteArrayShouldNotThrow() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        assertDoesNotThrow(() -> parser.feed(new byte[0]));
    }

    @Test
    void feedByteBufferShouldNotThrow() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        assertDoesNotThrow(() -> parser.feed(ByteBuffer.wrap(new byte[]{0x01, 0x02})));
    }

    @Test
    void feedEmptyByteBufferShouldNotThrow() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        assertDoesNotThrow(() -> parser.feed(ByteBuffer.allocate(0)));
    }

    @Test
    void feedByteBufferShouldConsumeAllRemainingBytes() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        ByteBuffer buffer = ByteBuffer.wrap(new byte[]{0x01, 0x02, 0x03});
        parser.feed(buffer);
        assertEquals(0, buffer.remaining());
    }

    @Test
    void feedNullByteArrayShouldThrowNullPointerException() {
        // LLPTransportDeframer.processBytes() iterates with enhanced-for,
        // which throws NPE for null input. This is an implicit contract.
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        assertThrows(NullPointerException.class, () -> parser.feed((byte[]) null));
    }

    @Test
    void feedNullByteBufferShouldThrowNullPointerException() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        assertThrows(NullPointerException.class, () -> parser.feed((ByteBuffer) null));
    }

    // =========================================================================
    // Integration — valid frame parsing
    // =========================================================================

    @Test
    void shouldProduceOneFrameWhenFedCompleteFrameAsByteArray() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);

        parser.feed(buildValidFrame((byte) 0x11, (byte) 0x22));

        List<LLPFrame> frames = parser.pollFrames();
        assertEquals(1, frames.size());
    }

    @Test
    void shouldProduceOneFrameWhenFedCompleteFrameByteByByte() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        byte[] frame = buildValidFrame((byte) 0x42);

        for (byte b : frame) {
            parser.feed(b);
        }

        assertEquals(1, parser.pollFrames().size());
    }

    @Test
    void shouldProduceOneFrameWhenFedCompleteFrameViaByteBuffer() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);

        parser.feed(ByteBuffer.wrap(buildValidFrame((byte) 0x33)));

        assertEquals(1, parser.pollFrames().size());
    }

    @Test
    void allFeedMethodsShouldProduceEquivalentFrames() {
        byte[] transportFrame = buildValidFrame((byte) 0x55);

        LLPIncrementalParser parserArray  = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        LLPIncrementalParser parserBuffer = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        LLPIncrementalParser parserBytes  = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);

        parserArray.feed(transportFrame);
        parserBuffer.feed(ByteBuffer.wrap(transportFrame));
        for (byte b : transportFrame) parserBytes.feed(b);

        LLPFrame fromArray  = parserArray.pollFrames().getFirst();
        LLPFrame fromBuffer = parserBuffer.pollFrames().getFirst();
        LLPFrame fromBytes  = parserBytes.pollFrames().getFirst();

        // CRC must be identical — it is computed by the transport layer from the raw bytes
        assertEquals(fromArray.crc(), fromBuffer.crc(),
                "CRC must match between byte[] and ByteBuffer feeds");
        assertEquals(fromArray.crc(), fromBytes.crc(),
                "CRC must match between byte[] and byte-by-byte feeds");

        // Chain structure must be identical
        assertEquals(fromArray.chain().size(), fromBuffer.chain().size(),
                "Chain size must match between byte[] and ByteBuffer feeds");
        assertEquals(fromArray.chain().size(), fromBytes.chain().size(),
                "Chain size must match between byte[] and byte-by-byte feeds");

        // All three must have produced a FinalNode
        assertInstanceOf(FinalNode.class, fromArray.chain().asList().getFirst());
        assertInstanceOf(FinalNode.class, fromBuffer.chain().asList().getFirst());
        assertInstanceOf(FinalNode.class, fromBytes.chain().asList().getFirst());

        // The raw payload bytes inside the FinalNode must be identical.
        // We compare content explicitly because LLPNode is an SPI contract —
        // external implementations are not guaranteed to provide value-based equals().
        byte[] payloadFromArray  = toByteArray(((FinalNode) fromArray.chain().asList().getFirst()).getPayload());
        byte[] payloadFromBuffer = toByteArray(((FinalNode) fromBuffer.chain().asList().getFirst()).getPayload());
        byte[] payloadFromBytes  = toByteArray(((FinalNode) fromBytes.chain().asList().getFirst()).getPayload());

        assertArrayEquals(payloadFromArray, payloadFromBuffer,
                "Payload bytes must match between byte[] and ByteBuffer feeds");
        assertArrayEquals(payloadFromArray, payloadFromBytes,
                "Payload bytes must match between byte[] and byte-by-byte feeds");
    }

    // Helper — extracts bytes from a read-only ByteBuffer without mutating it
    private static byte[] toByteArray(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }

    @Test
    void shouldNotProduceFrameWhenFedOnlyPartialFrame() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        byte[] fullFrame = buildValidFrame((byte) 0x01, (byte) 0x02, (byte) 0x03);

        // Feed only the first half
        byte[] partial = new byte[fullFrame.length / 2];
        System.arraycopy(fullFrame, 0, partial, 0, partial.length);
        parser.feed(partial);

        assertTrue(parser.pollFrames().isEmpty());
    }

    @Test
    void shouldProduceFrameAfterTwoPartialFeeds() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        byte[] fullFrame = buildValidFrame((byte) 0x01, (byte) 0x02, (byte) 0x03);
        int half = fullFrame.length / 2;

        byte[] firstHalf = new byte[half];
        byte[] secondHalf = new byte[fullFrame.length - half];
        System.arraycopy(fullFrame, 0, firstHalf, 0, half);
        System.arraycopy(fullFrame, half, secondHalf, 0, secondHalf.length);

        parser.feed(firstHalf);
        assertTrue(parser.pollFrames().isEmpty(), "No frame expected after partial feed");

        parser.feed(secondHalf);
        assertEquals(1, parser.pollFrames().size(), "Frame expected after complete feed");
    }

    @Test
    void shouldProduceTwoFramesWhenFedConcatenatedFrames() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);

        byte[] frame1 = buildValidFrame((byte) 0x11);
        byte[] frame2 = buildValidFrame((byte) 0x22);
        byte[] both = new byte[frame1.length + frame2.length];
        System.arraycopy(frame1, 0, both, 0, frame1.length);
        System.arraycopy(frame2, 0, both, frame1.length, frame2.length);

        parser.feed(both);

        assertEquals(2, parser.pollFrames().size());
    }

    @Test
    void shouldProduceTwoFramesWhenFedInSeparateCalls() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);

        parser.feed(buildValidFrame((byte) 0x11));
        parser.feed(buildValidFrame((byte) 0x22));

        assertEquals(2, parser.pollFrames().size());
    }

    @Test
    void parsedFrameShouldContainFinalNodeInChain() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);

        parser.feed(buildValidFrame((byte) 0xAB, (byte) 0xCD));

        LLPFrame frame = parser.pollFrames().get(0);
        assertEquals(1, frame.chain().size());
        assertInstanceOf(FinalNode.class, frame.chain().asList().get(0));
    }

    @Test
    void parsedFrameShouldHaveNonZeroCrc() {
        // The CRC is computed and validated by the transport layer.
        // A valid frame will never carry CRC 0 in practice.
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);

        parser.feed(buildValidFrame((byte) 0x01, (byte) 0x02));

        LLPFrame frame = parser.pollFrames().get(0);
        assertNotEquals(0, frame.crc());
    }

    // =========================================================================
    // Integration — transport errors
    // =========================================================================

    @Test
    void shouldAccumulateChecksumInvalidErrorOnBadCrc() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);

        parser.feed(buildFrameWithBadCrc());

        List<TransportErrorCode> errors = parser.pollErrors();
        assertEquals(1, errors.size());
        assertEquals(TransportErrorCode.CHECKSUM_INVALID, errors.get(0));
    }

    @Test
    void shouldAccumulateMultipleErrorsFromMultipleInvalidFrames() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);

        parser.feed(buildFrameWithBadCrc());
        parser.feed(buildFrameWithBadCrc());

        assertEquals(2, parser.pollErrors().size());
    }

    @Test
    void shouldAccumulateBothFramesAndErrorsIndependently() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);

        parser.feed(buildValidFrame((byte) 0x01));
        parser.feed(buildFrameWithBadCrc());
        parser.feed(buildValidFrame((byte) 0x02));

        assertEquals(2, parser.pollFrames().size());
        assertEquals(1, parser.pollErrors().size());
    }

    // =========================================================================
    // pollFrames() / pollErrors() — clearing behavior with real data
    // =========================================================================

    @Test
    void pollFramesShouldClearAccumulatedFrames() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        parser.feed(buildValidFrame((byte) 0x11));
        parser.feed(buildValidFrame((byte) 0x22));

        List<LLPFrame> firstPoll = parser.pollFrames();
        assertEquals(2, firstPoll.size(), "Both frames must be present in first poll");

        List<LLPFrame> secondPoll = parser.pollFrames();
        assertTrue(secondPoll.isEmpty(), "Second poll must be empty after clearing");
    }

    @Test
    void pollErrorsShouldClearAccumulatedErrors() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        parser.feed(buildFrameWithBadCrc());
        parser.feed(buildFrameWithBadCrc());

        List<TransportErrorCode> firstPoll = parser.pollErrors();
        assertEquals(2, firstPoll.size(), "Both errors must be present in first poll");

        List<TransportErrorCode> secondPoll = parser.pollErrors();
        assertTrue(secondPoll.isEmpty(), "Second poll must be empty after clearing");
    }

    @Test
    void pollShouldNotAffectFramesAccumulatedAfterClearing() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);

        parser.feed(buildValidFrame((byte) 0x01));
        parser.pollFrames(); // clears

        // Feed again after clearing
        parser.feed(buildValidFrame((byte) 0x02));
        assertEquals(1, parser.pollFrames().size());
    }

    @Test
    void pollFramesShouldReturnImmutableListEvenWhenNonEmpty() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        parser.feed(buildValidFrame((byte) 0x01));

        List<LLPFrame> frames = parser.pollFrames();
        assertThrows(UnsupportedOperationException.class, () -> frames.remove(0));
    }

    @Test
    void pollErrorsShouldReturnImmutableListEvenWhenNonEmpty() {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        parser.feed(buildFrameWithBadCrc());

        List<TransportErrorCode> errors = parser.pollErrors();
        assertThrows(UnsupportedOperationException.class, () -> errors.remove(0));
    }

    // =========================================================================
    // FrameListener — direct unit tests via reflection
    // =========================================================================

    @Test
    void frameListenerShouldBeRegisteredInDeframer() throws Exception {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        // extractListener() asserts internally that the listener is present
        assertNotNull(extractListener(parser));
    }

    @Test
    void frameListenerOnFrameReceivedShouldAddParsedFrameToCompletedFrames() throws Exception {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        LLPTransportDeframer.LLPFrameListener listener = extractListener(parser);

        // Feed a valid raw frame directly to the listener, bypassing transport
        LLPRawFrame rawFrame = new LLPRawFrame(new byte[]{0x00}, (byte) 0x1234);
        listener.onFrameReceived(rawFrame);

        List<LLPFrame> frames = parser.pollFrames();
        assertEquals(1, frames.size());
    }

    @Test
    void frameListenerOnFrameReceivedShouldProduceFrameWithExpectedCrc() throws Exception {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        LLPTransportDeframer.LLPFrameListener listener = extractListener(parser);

        LLPRawFrame rawFrame = new LLPRawFrame(new byte[]{0x00}, (byte) 0xABCD);
        listener.onFrameReceived(rawFrame);

        LLPFrame frame = parser.pollFrames().get(0);
        assertEquals((byte) 0xABCD, frame.crc());
    }

    @Test
    void frameListenerOnFrameReceivedShouldAccumulateMultipleFrames() throws Exception {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        LLPTransportDeframer.LLPFrameListener listener = extractListener(parser);

        listener.onFrameReceived(new LLPRawFrame(new byte[]{0x00}, (byte) 0x0001));
        listener.onFrameReceived(new LLPRawFrame(new byte[]{0x00}, (byte) 0x0002));
        listener.onFrameReceived(new LLPRawFrame(new byte[]{0x00}, (byte) 0x0003));

        assertEquals(3, parser.pollFrames().size());
    }

    @Test
    void frameListenerOnFrameErrorShouldAddErrorCodeToErrors() throws Exception {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        LLPTransportDeframer.LLPFrameListener listener = extractListener(parser);

        listener.onFrameError(TransportErrorCode.CHECKSUM_INVALID);

        List<TransportErrorCode> errors = parser.pollErrors();
        assertEquals(1, errors.size());
        assertEquals(TransportErrorCode.CHECKSUM_INVALID, errors.get(0));
    }

    @Test
    void frameListenerOnFrameErrorShouldAccumulateMultipleErrorCodes() throws Exception {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        LLPTransportDeframer.LLPFrameListener listener = extractListener(parser);

        listener.onFrameError(TransportErrorCode.CHECKSUM_INVALID);
        listener.onFrameError(TransportErrorCode.TIMEOUT);
        listener.onFrameError(TransportErrorCode.SYNC_ERROR);

        List<TransportErrorCode> errors = parser.pollErrors();
        assertEquals(3, errors.size());
        assertEquals(TransportErrorCode.CHECKSUM_INVALID, errors.get(0));
        assertEquals(TransportErrorCode.TIMEOUT, errors.get(1));
        assertEquals(TransportErrorCode.SYNC_ERROR, errors.get(2));
    }

    @Test
    void frameListenerOnFrameErrorShouldNotAffectFrameQueue() throws Exception {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        LLPTransportDeframer.LLPFrameListener listener = extractListener(parser);

        listener.onFrameError(TransportErrorCode.CHECKSUM_INVALID);

        assertTrue(parser.pollFrames().isEmpty());
    }

    @Test
    void frameListenerOnFrameReceivedShouldNotAffectErrorQueue() throws Exception {
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        LLPTransportDeframer.LLPFrameListener listener = extractListener(parser);

        listener.onFrameReceived(new LLPRawFrame(new byte[]{0x00}, 0));

        assertTrue(parser.pollErrors().isEmpty());
    }

    @Test
    void frameListenerShouldSurviveRawFrameWithEmptyPayload() throws Exception {
        // Verifies the listener delegates to the parser without crashing
        // even for a raw frame with no bytes — the parser should produce
        // an LLPFrame with an empty node chain.
        LLPIncrementalParser parser = new LLPIncrementalParser(EMPTY_PROVIDER, -1, -1);
        LLPTransportDeframer.LLPFrameListener listener = extractListener(parser);

        LLPRawFrame emptyRaw = new LLPRawFrame(new byte[0], 0);
        assertDoesNotThrow(() -> listener.onFrameReceived(emptyRaw));

        assertEquals(1, parser.pollFrames().size());
    }
}