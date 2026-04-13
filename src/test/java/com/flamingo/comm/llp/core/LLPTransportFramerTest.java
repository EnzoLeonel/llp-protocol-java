package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.util.CRC16CCITT;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LLPTransportFramerTest {

    private byte[] buildFrame(byte[] payload) {
        byte[] buffer = new byte[LLPTransportFramer.estimateMaxSize(payload != null ? payload.length : 0)];
        int len = LLPTransportFramer.build(payload, buffer, 0);
        return java.util.Arrays.copyOf(buffer, len);
    }

    // ================= BASIC =================

    @Test
    void testHeaderIsCorrect() {
        byte[] frame = buildFrame(new byte[]{1, 2, 3});

        assertEquals((byte) 0xAA, frame[0]);
        assertEquals((byte) 0x55, frame[1]);
    }

    @Test
    void testNullPayload() {
        byte[] frame = buildFrame(null);

        // Length = 0
        assertEquals(0, frame[2]);
        assertEquals(0, frame[3]);

        // CRC only frame
        assertTrue(frame.length >= 6);
    }

    @Test
    void testVariousPayloadSizes() {
        for (int size : new int[]{0, 1, 10, 100, 512}) {
            byte[] payload = new byte[size];
            byte[] frame = buildFrame(payload);

            assertNotNull(frame);
            assertTrue(frame.length >= 4 + size);
        }
    }

    // ================= CRC =================

    @Test
    void testCRCIsValidAfterDestuff() {
        byte[] payload = {0x10, 0x20, 0x30};
        byte[] frame = buildFrame(payload);

        byte[] unstuffed = destuff(frame);

        int crcExpected = CRC16CCITT.calculate(unstuffed, 0, unstuffed.length - 2);

        int crcFrame =
                (unstuffed[unstuffed.length - 2] & 0xFF) |
                        ((unstuffed[unstuffed.length - 1] & 0xFF) << 8);

        assertEquals(crcExpected, crcFrame);
    }

    // ================= STUFFING =================

    @Test
    void testStuffingSingleAA() {
        byte[] payload = {(byte) 0xAA};

        byte[] frame = buildFrame(payload);

        boolean found = false;
        for (int i = 2; i < frame.length - 1; i++) {
            if (frame[i] == (byte) 0xAA && frame[i + 1] == 0x00) {
                found = true;
                break;
            }
        }

        assertTrue(found);
    }

    @Test
    void testStuffingMultipleAA() {
        byte[] payload = {(byte) 0xAA, (byte) 0xAA, (byte) 0xAA};

        byte[] frame = buildFrame(payload);

        int stuffedCount = 0;

        for (int i = 2; i < frame.length - 1; i++) {
            if (frame[i] == (byte) 0xAA && frame[i + 1] == 0x00) {
                stuffedCount++;
            }
        }

        assertTrue(stuffedCount >= 3, "Should have at least 3 stuffed bytes");
    }

    @Test
    void testNoFakeHeaderInsideFrame() {
        byte[] payload = new byte[100];
        new Random().nextBytes(payload);

        byte[] frame = buildFrame(payload);

        for (int i = 2; i < frame.length - 1; i++) {
            assertFalse(
                    frame[i] == (byte) 0xAA && frame[i + 1] == (byte) 0x55,
                    "Forbidden AA55 sequence found"
            );
        }
    }

    // ================= INTEGRATION =================

    @Test
    void testFrameCanBeParsedByDeframer() {
        LLPTransportDeframer deframer = new LLPTransportDeframer();

        byte[] payload = new byte[50];
        new Random().nextBytes(payload);

        byte[] frame = buildFrame(payload);

        LLPRawFrame result = null;

        for (byte b : frame) {
            LLPRawFrame f = deframer.processByte(b);
            if (f != null) result = f;
        }

        assertNotNull(result);

        ByteBuffer buf = result.payload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertArrayEquals(payload, extracted);
    }

    @Test
    void testRandomPayloads() {
        LLPTransportDeframer deframer = new LLPTransportDeframer();
        Random random = new Random();

        for (int i = 0; i < 1000; i++) {
            byte[] payload = new byte[32];
            random.nextBytes(payload);

            byte[] frame = buildFrame(payload);

            LLPRawFrame result = null;

            for (byte b : frame) {
                LLPRawFrame f = deframer.processByte(b);
                if (f != null) result = f;
            }

            assertNotNull(result);

            ByteBuffer buf = result.payload();
            byte[] extracted = new byte[buf.remaining()];
            buf.get(extracted);

            assertArrayEquals(payload, extracted);
        }
    }

    // ================= SAFE API =================

    @Test
    void testBuildSafe() {
        byte[] payload = new byte[]{1, 2, 3};

        byte[] frame = LLPTransportFramer.buildSafe(payload);

        assertNotNull(frame);
        assertTrue(frame.length > payload.length);
    }

    // ================= UTILS =================

    private byte[] destuff(byte[] frame) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(frame.length);

        // Copy header
        out.write(frame, 0, 2);

        for (int i = 2; i < frame.length; i++) {
            byte b = frame[i];

            if (b == (byte) 0xAA && i + 1 < frame.length && frame[i + 1] == 0x00) {
                out.write(0xAA);
                i++;
            } else {
                out.write(b);
            }
        }

        return out.toByteArray();
    }

    // ================= EDGE CASES & MEMORY =================

    @Test
    void testBufferOffsetIsRespected() {
        byte[] payload = {1, 2, 3};
        // Create a buffer larger than needed, filled with dummy data
        byte[] outBuffer = new byte[50];
        java.util.Arrays.fill(outBuffer, (byte) 0xFF);

        int offset = 10;
        int written = LLPTransportFramer.build(payload, outBuffer, offset);

        // 1. Verify bytes before offset are untouched
        for (int i = 0; i < offset; i++) {
            assertEquals((byte) 0xFF, outBuffer[i], "Bytes before offset should not be modified");
        }

        // 2. Verify the frame started exactly at the offset
        assertEquals((byte) 0xAA, outBuffer[offset], "Magic byte 1 should be at offset");
        assertEquals((byte) 0x55, outBuffer[offset + 1], "Magic byte 2 should be after magic 1");

        // 3. Verify bytes after the written frame are untouched
        for (int i = offset + written; i < outBuffer.length; i++) {
            assertEquals((byte) 0xFF, outBuffer[i], "Bytes after frame should not be modified");
        }
    }

    @Test
    void testBufferTooSmallThrowsException() {
        byte[] payload = new byte[10];

        // Intentionally create a buffer that is too small for the worst-case scenario
        // Worst case:
        //      2 (magic, not stuffed)
        //      + up to 4 (length, fully stuffed)
        //      + payloadLen * 2 (payload worst case)
        //      + 4 (CRC fully stuffed)
        byte[] smallBuffer = new byte[29];

        assertThrows(IllegalArgumentException.class, () -> {
            LLPTransportFramer.build(payload, smallBuffer, 0);
        }, "Should throw exception if buffer cannot hold the worst-case stuffed frame");
    }

    @Test
    void testAbsoluteWorstCaseStuffingSize() {
        // 170 bytes of 0xAA.
        // Length will be 170 (0xAA). Length High will be 0x00.
        // This forces maximum stuffing on both Length Low and Payload.
        byte[] payload = new byte[170];
        java.util.Arrays.fill(payload, (byte) 0xAA);

        byte[] frame = buildFrame(payload);

        // Verify frame was built successfully
        assertNotNull(frame);

        // Verify that every 0xAA is stuffed
        for (int i = 2; i < frame.length - 1; i++) {
            if (frame[i] == (byte) 0xAA) {
                assertEquals(0x00, frame[i + 1], "Every 0xAA must be stuffed");
            }
        }
    }

    // ================= ENDIANNESS =================

    @Test
    void testLengthIsLittleEndian() {
        // Create a payload of exactly 258 bytes (0x0102 in hex)
        // Length Low should be 0x02, Length High should be 0x01
        byte[] payload = new byte[258];

        byte[] frame = buildFrame(payload);

        // MAGIC_1(0), MAGIC_2(1), LEN_L(2), LEN_H(3)
        assertEquals((byte) 0x02, frame[2], "Length Low byte should be 0x02");
        assertEquals((byte) 0x01, frame[3], "Length High byte should be 0x01");
    }
}
