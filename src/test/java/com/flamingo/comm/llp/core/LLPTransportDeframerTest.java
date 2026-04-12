package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.LLP;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LLPTransportDeframerTest {

    private LLPTransportDeframer deframer;

    @BeforeEach
    void setUp() {
        deframer = new LLPTransportDeframer();
    }

    @Test
    void testSingleFrame() {
        byte[] payload = new byte[]{0x01, 0x02, 0x03};
        byte[] frame = LLP.buildData(1, payload);

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
    void testMultipleFramesBackToBack() {
        byte[] f1 = LLP.buildPing(1);
        byte[] f2 = LLP.buildPing(2);

        byte[] combined = new byte[f1.length + f2.length];
        System.arraycopy(f1, 0, combined, 0, f1.length);
        System.arraycopy(f2, 0, combined, f1.length, f2.length);

        int count = 0;

        for (byte b : combined) {
            if (deframer.processByte(b) != null) {
                count++;
            }
        }

        assertEquals(2, count);
    }

    @Test
    void testFragmentedFrame() {
        byte[] frame = LLP.buildPing(42);

        LLPRawFrame result = null;

        for (int i = 0; i < frame.length / 2; i++) {
            deframer.processByte(frame[i]);
        }

        for (int i = frame.length / 2; i < frame.length; i++) {
            LLPRawFrame f = deframer.processByte(frame[i]);
            if (f != null) result = f;
        }

        assertNotNull(result);
    }

    @Test
    void testNoiseBeforeFrame() {
        byte[] noise = new byte[]{0x00, 0x13, 0x7F, 0x55};
        byte[] frame = LLP.buildPing(7);

        for (byte b : noise) {
            deframer.processByte(b);
        }

        LLPRawFrame result = null;

        for (byte b : frame) {
            LLPRawFrame f = deframer.processByte(b);
            if (f != null) result = f;
        }

        assertNotNull(result);
    }

    @Test
    void testInvalidCRC() {
        byte[] frame = LLP.buildPing(1);

        // Corrupt CRC
        frame[frame.length - 1] ^= 0xFF;

        LLPRawFrame result = null;

        for (byte b : frame) {
            LLPRawFrame f = deframer.processByte(b);
            if (f != null) result = f;
        }

        assertNull(result);
        assertTrue(deframer.getStatistics().getFramesError() > 0);
    }

    @Test
    void testTimeoutResetsParser() throws InterruptedException {
        byte[] frame = LLP.buildPing(10);

        for (int i = 0; i < frame.length / 2; i++) {
            deframer.processByte(frame[i]);
        }

        Thread.sleep(2100);

        LLPRawFrame result = null;

        for (int i = frame.length / 2; i < frame.length; i++) {
            LLPRawFrame f = deframer.processByte(frame[i]);
            if (f != null) result = f;
        }

        assertNull(result);
        assertTrue(deframer.getStatistics().getTimeouts() > 0);
    }

    @Test
    void testMaxPayload() {
        byte[] payload = new byte[LLP.MAX_PAYLOAD_SIZE_BYTES];
        byte[] frame = LLP.buildData(1, payload);

        LLPRawFrame result = null;

        for (byte b : frame) {
            LLPRawFrame f = deframer.processByte(b);
            if (f != null) result = f;
        }

        assertNotNull(result);

        ByteBuffer buf = result.payload();
        assertEquals(payload.length, buf.remaining());
    }

    @Test
    void testStuffedPayload() {
        byte[] payload = new byte[]{
                0x11, (byte) 0xAA, 0x22, (byte) 0xAA, 0x33
        };

        byte[] frame = LLP.buildData(1, payload);

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
    void testInvalidEscapeSequence() {
        byte[] frame = LLP.buildPing(1);

        frame[5] = (byte) 0xAA;
        frame[6] = (byte) 0x99;

        for (byte b : frame) {
            deframer.processByte(b);
        }

        assertTrue(deframer.getStatistics().getFramesError() > 0);
    }

    @Test
    void testProcessBytesBatch() {
        byte[] f1 = LLP.buildPing(1);
        byte[] f2 = LLP.buildPing(2);

        byte[] combined = new byte[f1.length + f2.length];
        System.arraycopy(f1, 0, combined, 0, f1.length);
        System.arraycopy(f2, 0, combined, f1.length, f2.length);

        List<LLPRawFrame> frames = deframer.processBytes(combined);

        assertEquals(2, frames.size());
    }

    @Test
    void testRandomFrames() {
        Random random = new Random();

        for (int i = 0; i < 500; i++) {
            byte[] payload = new byte[32];
            random.nextBytes(payload);

            byte[] frame = LLP.buildData(i, payload);

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
}