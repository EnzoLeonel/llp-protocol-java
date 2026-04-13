package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.util.ByteWriter;
import com.flamingo.comm.llp.util.CRC16CCITT;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    // ================= BYTE WRITER =================

    @Test
    void testBuildWithByteWriterSimple() {
        byte[] payload = {0x01, 0x02, 0x03};

        TestByteWriter writer = new TestByteWriter();

        int written = LLPTransportFramer.build(payload, writer);
        byte[] frame = writer.toByteArray();

        assertEquals(written, frame.length);
        assertEquals((byte) 0xAA, frame[0]);
        assertEquals((byte) 0x55, frame[1]);
    }

    @Test
    void testBuildWriterEqualsBufferBuild() {
        byte[] payload = {0x11, 0x22, 0x33};

        // Writer version
        TestByteWriter writer = new TestByteWriter();
        LLPTransportFramer.build(payload, writer);
        byte[] frame1 = writer.toByteArray();

        // Buffer version
        byte[] buffer = new byte[LLPTransportFramer.estimateMaxSize(payload.length)];
        int written = LLPTransportFramer.build(payload, buffer, 0);

        byte[] frame2 = Arrays.copyOf(buffer, written);

        assertArrayEquals(frame2, frame1);
    }

    @Test
    void testBuildWriterStuffing() {
        byte[] payload = {(byte) 0xAA};

        TestByteWriter writer = new TestByteWriter();
        LLPTransportFramer.build(payload, writer);

        byte[] frame = writer.toByteArray();

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
    void testBuildWriterNullPayload() {
        TestByteWriter writer = new TestByteWriter();

        int written = LLPTransportFramer.build(null, writer);
        byte[] frame = writer.toByteArray();

        assertEquals(written, frame.length);

        // Solo header + len + crc
        assertTrue(frame.length >= 6);
    }

    @Test
    void testWriteOrderStartsWithMagic() {
        RecordingWriter writer = new RecordingWriter();

        LLPTransportFramer.build(new byte[]{1, 2}, writer);

        assertEquals((byte) 0xAA, writer.getBytes().get(0));
        assertEquals((byte) 0x55, writer.getBytes().get(1));
    }

    @Test
    void testWriterFailurePropagates() {
        FailingWriter writer = new FailingWriter();

        assertThrows(RuntimeException.class, () ->
                LLPTransportFramer.build(new byte[]{1, 2, 3}, writer)
        );
    }

    @Test
    void testWriterFrameIsParsable() {
        byte[] payload = new byte[50];
        new Random().nextBytes(payload);

        TestByteWriter writer = new TestByteWriter();
        LLPTransportFramer.build(payload, writer);

        byte[] frame = writer.toByteArray();

        LLPTransportDeframer deframer = new LLPTransportDeframer();

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
    void testBuildReturnsCorrectWrittenCount() {
        // Payload that will force multiple stuffings
        byte[] payload = {(byte) 0xAA, 0x01, (byte) 0xAA, 0x02};

        TestByteWriter writer = new TestByteWriter(); // Assuming this tracks size
        int bytesWritten = LLPTransportFramer.build(payload, writer);

        byte[] frame = writer.toByteArray();

        assertEquals(frame.length, bytesWritten, "The returned written count should match the actual bytes emitted");
    }

    @Test
    void testBuildMaximumPayloadDoesNotCrash() {
        // Max unsigned short is 65535
        int maxPayloadSize = 65535;
        byte[] massivePayload = new byte[maxPayloadSize];

        // Fill with 0xAA to force maximum worst-case stuffing
        java.util.Arrays.fill(massivePayload, (byte) 0xAA);

        TestByteWriter writer = new TestByteWriter();

        // We just want to ensure it completes successfully without memory errors or index bounds
        assertDoesNotThrow(() -> {
            int written = LLPTransportFramer.build(massivePayload, writer);

            // 2 (Magic) + 4 (Len stuffed) + 131070 (Payload stuffed) + 4 (CRC stuffed)
            assertTrue(written > 131000, "Frame should be written successfully even at massive sizes");
        }, "Building a massive stuffed frame should not throw exceptions");
    }

    @Test
    void testWriterFailsDuringStuffingInjection() {
        byte[] payload = {(byte) 0xAA, 0x02, 0x03};

        ByteWriter boundaryFailingWriter = new ByteWriter() {
            private int count = 0;

            @Override
            public void write(byte b) {
                count++;
                // 1. MAGIC_1, 2. MAGIC_2, 3. LEN_L, 4. LEN_H
                // 5. payload[0] (0xAA).
                // 6. The stuffing byte (0x00) should trigger the failure!
                if (count == 6) {
                    throw new IllegalStateException("Socket disconnected during stuffing!");
                }
            }
        };

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            LLPTransportFramer.build(payload, boundaryFailingWriter);
        });

        assertEquals("Socket disconnected during stuffing!", exception.getMessage());
    }

    class TestByteWriter implements ByteWriter {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        @Override
        public void write(byte b) {
            out.write(b);
        }

        public byte[] toByteArray() {
            return out.toByteArray();
        }
    }

    class RecordingWriter implements ByteWriter {
        private final List<Byte> bytes = new ArrayList<>();

        @Override
        public void write(byte b) {
            bytes.add(b);
        }

        public List<Byte> getBytes() {
            return bytes;
        }
    }

    class FailingWriter implements ByteWriter {
        private int count = 0;

        @Override
        public void write(byte b) {
            if (++count > 5) {
                throw new RuntimeException("Writer failure");
            }
        }
    }
}
