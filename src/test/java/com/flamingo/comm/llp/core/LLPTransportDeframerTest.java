package com.flamingo.comm.llp.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LLPTransportDeframerTest {

    private LLPTransportDeframer deframer;

    @BeforeEach
    void setUp() {
        deframer = new LLPTransportDeframer(1024);
    }

    private byte[] buildFrame(byte[] payload) {
        byte[] buffer = new byte[LLPTransportFramer.estimateMaxSize(payload.length)];
        int len = LLPTransportFramer.build(payload, buffer, 0);
        return Arrays.copyOf(buffer, len);
    }

    @Test
    void testSingleFrame() {
        byte[] payload = new byte[]{0x01, 0x02, 0x03};
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
    void testMultipleFramesBackToBack() {
        byte[] f1 = buildFrame(new byte[]{0x01});
        byte[] f2 = buildFrame(new byte[]{0x02});

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
        byte[] frame = buildFrame(new byte[]{42});

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
        byte[] frame = buildFrame(new byte[]{7});

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
        byte[] frame = buildFrame(new byte[]{1});

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
        byte[] frame = buildFrame(new byte[]{10});

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
    void testPayloadExceedsMaximum() {
        byte[] payload = new byte[1025]; // max is 1024
        byte[] frame = buildFrame(payload);

        AtomicInteger payloadErrors = new AtomicInteger();

        LLPTransportDeframer.LLPFrameListener listener = new LLPTransportDeframer.LLPFrameListener() {
            @Override
            public void onFrameReceived(LLPRawFrame frame) {
                // ignored
            }

            @Override
            public void onFrameError(ErrorCode errorCode) {
                if (errorCode == ErrorCode.PAYLOAD_LEN_INVALID) {
                    payloadErrors.incrementAndGet();
                }
            }
        };

        deframer.addListener(listener);

        try {
            LLPRawFrame result = null;

            for (byte b : frame) {
                LLPRawFrame f = deframer.processByte(b);
                if (f != null) result = f;
            }

            assertNull(result);
            assertEquals(1, payloadErrors.get());
            assertEquals(1, deframer.getStatistics().getFramesError());

        } finally {
            deframer.removeListener(listener);
        }
    }

    @Test
    void testRecoveryAfterPayloadOverflow() {
        byte[] invalid = buildFrame(new byte[1025]);
        byte[] valid = buildFrame(new byte[]{1, 2, 3});

        LLPRawFrame result = null;

        for (byte b : invalid) {
            deframer.processByte(b);
        }

        for (byte b : valid) {
            LLPRawFrame f = deframer.processByte(b);
            if (f != null) result = f;
        }

        assertNotNull(result);
    }

    @Test
    void testStuffedPayload() {
        byte[] payload = new byte[]{
                0x11, (byte) 0xAA, 0x22, (byte) 0xAA, 0x33
        };

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
    void testInvalidEscapeSequence() {
        byte[] frame = buildFrame(new byte[]{1});

        frame[5] = (byte) 0xAA;
        frame[6] = (byte) 0x99;

        for (byte b : frame) {
            deframer.processByte(b);
        }

        assertTrue(deframer.getStatistics().getFramesError() > 0);
    }

    @Test
    void testProcessBytesBatch() {
        byte[] f1 = buildFrame(new byte[]{1});
        byte[] f2 = buildFrame(new byte[]{2});

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
}