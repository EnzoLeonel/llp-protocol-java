package com.flamingo.comm.llp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LLPParserTest {

    private LLPParser parser;

    @BeforeEach
    void setUp() {
        parser = LLP.newParser();
    }

    @Test
    void testParsePingFrame() {
        byte[] frame = LLP.buildPing(42);

        LLPFrame result = null;
        int count = 0;

        for (byte b : frame) {
            LLPFrame f = parser.processByte(b);
            if (f != null) {
                result = f;
                count++;
            }
        }

        assertEquals(1, count);

        assertNotNull(result);
        assertEquals(result.type(), LLPMessageType.PING.value());
        assertEquals(42, result.id());
        assertEquals(0, result.payloadLength());
    }

    @Test
    void testParseDataFrame() {
        byte[] data = new byte[]{0x01, 0x02, 0x03};
        byte[] frame = LLP.buildData(123, data);

        LLPFrame result = null;
        int count = 0;

        for (byte b : frame) {
            LLPFrame f = parser.processByte(b);
            if (f != null) {
                result = f;
                count++;
            }
        }

        assertEquals(1, count);

        assertNotNull(result);
        assertEquals(result.type(), LLPMessageType.DATA.value());
        assertEquals(123, result.id());
        assertArrayEquals(result.payload(), data);
    }

    @Test
    void testParseInvalidCRC() {
        byte[] frame = LLP.buildPing(1);

        // Corrupt CRC
        frame[frame.length - 1] ^= 0xFF;

        LLPFrame result = null;

        int count = 0;

        for (byte b : frame) {
            LLPFrame f = parser.processByte(b);
            if (f != null) {
                result = f;
                count++;
            }
        }

        assertEquals(0, count);

        assertNull(result);
        assertEquals(1, parser.getStatistics().getFramesError());
    }

    @Test
    void testStatistics() {
        // Process 3 valid frames
        for (int i = 0; i < 3; i++) {
            byte[] frame = LLP.buildPing(i);
            for (byte b : frame) {
                parser.processByte(b);
            }
        }

        assertEquals(3, parser.getStatistics().getFramesOk());
        assertEquals(3, parser.getStatistics().getTotalFrames());
        assertEquals(100.0, parser.getStatistics().getSuccessRate());
    }

    @Test
    void testFragmentedFrame() {
        byte[] frame = LLP.buildPing(55);

        LLPFrame result = null;

        // Send in 2 parts
        for (int i = 0; i < frame.length / 2; i++) {
            parser.processByte(frame[i]);
        }

        for (int i = frame.length / 2; i < frame.length; i++) {
            LLPFrame f = parser.processByte(frame[i]);
            if (f != null) result = f;
        }

        assertNotNull(result);
        assertEquals(55, result.id());
    }

    @Test
    void testMultipleFramesBackToBack() {
        byte[] f1 = LLP.buildPing(1);
        byte[] f2 = LLP.buildPing(2);

        byte[] combined = new byte[f1.length + f2.length];
        System.arraycopy(f1, 0, combined, 0, f1.length);
        System.arraycopy(f2, 0, combined, f1.length, f2.length);

        int count = 0;

        for (byte b : combined) {
            LLPFrame f = parser.processByte(b);
            if (f != null) count++;
        }

        assertEquals(2, count);
    }

    @Test
    void testNoiseBeforeFrame() {
        byte[] frame = LLP.buildPing(77);

        byte[] noise = new byte[]{0x00, 0x13, 0x7F, 0x55};

        LLPFrame result = null;

        for (byte b : noise) {
            parser.processByte(b);
        }

        for (byte b : frame) {
            LLPFrame f = parser.processByte(b);
            if (f != null) result = f;
        }

        assertNotNull(result);
        assertEquals(77, result.id());
    }

    @Test
    void testTimeoutResetsParser() throws InterruptedException {
        byte[] frame = LLP.buildPing(10);

        // Send half the frame
        for (int i = 0; i < frame.length / 2; i++) {
            parser.processByte(frame[i]);
        }

        // Wait longer than the timeout
        Thread.sleep(2100);

        // Send remainder
        LLPFrame result = null;
        for (int i = frame.length / 2; i < frame.length; i++) {
            LLPFrame f = parser.processByte(frame[i]);
            if (f != null) result = f;
        }

        assertNull(result);
        assertTrue(parser.getStatistics().getTimeouts() > 0);
    }

    @Test
    void testMaxPayload() {
        byte[] payload = new byte[LLPFrame.DEFAULT_MAX_PAYLOAD];
        byte[] frame = LLP.buildData(1, payload);

        LLPFrame result = null;

        for (byte b : frame) {
            LLPFrame f = parser.processByte(b);
            if (f != null) result = f;
        }

        assertNotNull(result);
        assertEquals(payload.length, result.payloadLength());
    }

    @Test
    void testParseStuffedPayload() {
        byte[] payload = new byte[]{
                0x11, (byte) 0xAA, 0x22, (byte) 0xAA, 0x33
        };

        byte[] frame = LLP.buildData(1, payload);

        LLPFrame result = null;

        for (byte b : frame) {
            LLPFrame f = parser.processByte(b);
            if (f != null) result = f;
        }

        assertNotNull(result);
        assertArrayEquals(payload, result.payload());
    }

    @Test
    void testStuffingAcrossEntireFrame() {
        byte type = (byte) 0xAA; // force stuffing
        int id = 0xAA55;
        byte[] payload = new byte[]{(byte) 0xAA, (byte) 0xAA};

        byte[] frame = LLP.buildFrame(type, id, payload);

        LLPFrame result = null;

        for (byte b : frame) {
            LLPFrame f = parser.processByte(b);
            if (f != null) result = f;
        }

        assertNotNull(result);
        assertEquals(type, result.type());
        assertEquals(id, result.id());
        assertArrayEquals(payload, result.payload());
    }

    @Test
    void testDoubleAASequence() {
        byte[] payload = new byte[]{
                (byte) 0xAA, (byte) 0xAA, (byte) 0xAA
        };

        byte[] frame = LLP.buildData(1, payload);

        LLPFrame result = null;

        for (byte b : frame) {
            LLPFrame f = parser.processByte(b);
            if (f != null) result = f;
        }

        assertNotNull(result);
        assertArrayEquals(payload, result.payload());
    }

    @Test
    void testFakeHeaderInsidePayload() {
        byte[] payload = new byte[]{
                0x10,
                (byte) 0xAA, 0x55, // It looks like a header, but it must be escaped
                0x20
        };

        byte[] frame = LLP.buildData(1, payload);

        LLPFrame result = null;

        for (byte b : frame) {
            LLPFrame f = parser.processByte(b);
            if (f != null) result = f;
        }

        assertNotNull(result);
        assertArrayEquals(payload, result.payload());
    }

    @Test
    void testInvalidEscapeSequence() {
        byte[] frame = LLP.buildPing(1);

        // We injected an invalid sequence: AA 99
        frame[5] = (byte) 0xAA;
        frame[6] = (byte) 0x99;

        for (byte b : frame) {
            parser.processByte(b);
        }

        assertTrue(parser.getStatistics().getFramesError() > 0);
    }

    @Test
    void testRandomFramesWithStuffing() {
        Random random = new Random();

        for (int i = 0; i < 1000; i++) {
            byte[] payload = new byte[50];
            random.nextBytes(payload);

            byte[] frame = LLP.buildData(i, payload);

            LLPFrame result = null;

            for (byte b : frame) {
                LLPFrame f = parser.processByte(b);
                if (f != null) result = f;
            }

            assertNotNull(result);
            assertArrayEquals(payload, result.payload());
        }
    }
}