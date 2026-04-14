package com.flamingo.comm.llp.core;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

import static org.junit.jupiter.api.Assertions.*;

class LLPRawFrameTest {

    @Test
    void testPayloadContent() {
        byte[] payload = {1, 2, 3};

        LLPRawFrame frame = new LLPRawFrame(payload, 0x1234);

        ByteBuffer buf = frame.payload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertArrayEquals(payload, extracted);
    }

    @Test
    void testPayloadIsReadOnly() {
        LLPRawFrame frame = new LLPRawFrame(new byte[]{1, 2, 3}, 0);

        ByteBuffer buf = frame.payload();

        assertTrue(buf.isReadOnly());
        assertThrows(ReadOnlyBufferException.class, () -> buf.put((byte) 0xFF));
    }

    @Test
    void testPayloadReturnsDuplicateBuffer() {
        LLPRawFrame frame = new LLPRawFrame(new byte[]{1, 2, 3}, 0);

        ByteBuffer b1 = frame.payload();
        ByteBuffer b2 = frame.payload();

        assertNotSame(b1, b2);
    }

    @Test
    void testBufferPositionIndependence() {
        LLPRawFrame frame = new LLPRawFrame(new byte[]{1, 2, 3}, 0);

        ByteBuffer b1 = frame.payload();
        ByteBuffer b2 = frame.payload();

        b1.get(); // move position

        assertEquals(1, b1.position());
        assertEquals(0, b2.position(), "Buffers should have independent positions");
    }

    @Test
    void testOriginalArrayModificationDoesNotAffectFrame() {
        byte[] payload = {1, 2, 3};

        LLPRawFrame frame = new LLPRawFrame(payload, 0);

        // Modify original array AFTER construction
        payload[0] = 99;

        ByteBuffer buf = frame.payload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertEquals(1, extracted[0], "Internal payload must be protected from external changes");
    }

    @Test
    void testPayloadLengthRespected() {
        byte[] payload = {1, 2, 3, 4, 5};

        LLPRawFrame frame = new LLPRawFrame(payload, 3, 0, 0);

        ByteBuffer buf = frame.payload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertArrayEquals(new byte[]{1, 2, 3}, extracted);
    }

    @Test
    void testNullPayloadBecomesEmpty() {
        LLPRawFrame frame = new LLPRawFrame(null, 0);

        ByteBuffer buf = frame.payload();

        assertNotNull(buf);
        assertEquals(0, buf.remaining());
    }

    @Test
    void testPayloadParameterConstructors() {
        byte[] validPayload = {1, 2, 3, 4, 5};

        LLPRawFrame normalFrame = new LLPRawFrame(validPayload, 0);
        LLPRawFrame framePayloadNull = new LLPRawFrame(null, 10, 0, System.currentTimeMillis());
        LLPRawFrame frameWithBadLength = new LLPRawFrame(validPayload, -2, 0, System.currentTimeMillis());
        LLPRawFrame framePayloadEmpty = new LLPRawFrame(new byte[0], 0);

        ByteBuffer buf1 = normalFrame.payload();
        ByteBuffer buf2 = framePayloadNull.payload();
        ByteBuffer buf3 = frameWithBadLength.payload();
        ByteBuffer buf4 = framePayloadEmpty.payload();

        // EMPTY cases → empty payload
        assertEquals(0, buf2.remaining());
        assertEquals(0, buf3.remaining());
        assertEquals(0, buf4.remaining());

        // NORMAL case → payload present
        assertEquals(validPayload.length, buf1.remaining());

        // Different buffers (defensive)
        assertNotSame(buf2, buf3);
        assertNotSame(buf1, buf2);
        assertNotSame(buf1, buf4);
    }

    @Test
    void testCrcValue() {
        LLPRawFrame frame = new LLPRawFrame(new byte[]{1}, 0xABCD);

        assertEquals(0xABCD, frame.crc());
    }

    @Test
    void testTimestampAutoAssigned() {
        long before = System.currentTimeMillis();

        LLPRawFrame frame = new LLPRawFrame(new byte[]{1}, 0);

        long after = System.currentTimeMillis();

        assertTrue(frame.timestamp() >= before);
        assertTrue(frame.timestamp() <= after);
    }

    @Test
    void testTimestampCustom() {
        long ts = 123456789L;

        LLPRawFrame frame = new LLPRawFrame(new byte[]{1}, 0, ts);

        assertEquals(ts, frame.timestamp());
    }

    @Test
    void testLargePayload() {
        byte[] payload = new byte[1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }

        LLPRawFrame frame = new LLPRawFrame(payload, 0);

        ByteBuffer buf = frame.payload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertArrayEquals(payload, extracted);
    }
}