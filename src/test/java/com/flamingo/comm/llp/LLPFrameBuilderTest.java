package com.flamingo.comm.llp;

import com.flamingo.comm.llp.crc.CRC16CCITT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LLPFrameBuilderTest {

    @Test
    void testBuildSimpleFrame() {
        byte[] frame = LLP.buildPing(123);

        assertNotNull(frame);
        assertEquals((byte) 0xAA, frame[0]);
        assertEquals((byte) 0x55, frame[1]);
        assertEquals(frame[3], LLPMessageType.PING.value());
    }

    @Test
    void testBuildFrameStructure() {
        byte[] payload = {0x01, 0x02};
        int id = 0x1234;

        byte[] frame = LLP.buildFrame(LLPMessageType.DATA.value(), id, payload);

        assertEquals((byte) 0xAA, frame[0]);
        assertEquals((byte) 0x55, frame[1]);

        assertEquals(LLP.PROTOCOL_VERSION, frame[2]);

        assertEquals(LLPMessageType.DATA.value(), frame[3]);

        // ID little endian
        assertEquals((byte) 0x34, frame[4]);
        assertEquals((byte) 0x12, frame[5]);

        // Length
        assertEquals((byte) 0x02, frame[6]);
        assertEquals((byte) 0x00, frame[7]);

        // Payload
        assertEquals(0x01, frame[8]);
        assertEquals(0x02, frame[9]);
    }

    @Test
    void testBuildDataFrame() {
        byte[] data = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
        byte[] frame = LLP.buildData(42, data);

        assertNotNull(frame);
        assertTrue(frame.length > data.length); // Overhead included
        assertEquals(frame[3], LLPMessageType.DATA.value());
    }

    @Test
    void testFluentBuilder() {
        byte[] frame = LLP.frameBuilder()
                .type(LLPMessageType.COMMAND)
                .id(999)
                .payload("HELLO")
                .build();

        assertNotNull(frame);
        assertTrue(frame.length > 0);
    }

    @Test
    void testPayloadTooLarge() {
        byte[] largePayload = new byte[513]; // > 512
        assertThrows(IllegalArgumentException.class, () ->
                LLP.buildFrame(LLPMessageType.DATA.value(), 1, largePayload)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 100, 512})
    void testVariousPayloadSizes(int size) {
        byte[] payload = new byte[size];
        byte[] frame = LLP.buildFrame(LLPMessageType.DATA.value(), 1, payload);

        assertNotNull(frame);
        assertTrue(frame.length >= 9 + size); // Min 9 bytes overhead
    }

    @Test
    void testCRCIsValid() {
        byte[] payload = {0x10, 0x20, 0x30};
        byte[] frame = LLP.buildFrame(LLPMessageType.DATA.value(), 1, payload);

        int crcExpected = CRC16CCITT.calculate(frame, 0, frame.length - 2);

        int crcFrame =
                (frame[frame.length - 2] & 0xFF) |
                        ((frame[frame.length - 1] & 0xFF) << 8);

        assertEquals(crcExpected, crcFrame);
    }

    @Test
    void testNullPayload() {
        byte[] frame = LLP.buildFrame(LLPMessageType.DATA.value(), 1, null);

        // Length = 0
        assertEquals(0, frame[6]);
        assertEquals(0, frame[7]);

        // Minimal Frame: 8 header + 2 CRC
        assertEquals(10, frame.length);
    }

    @Test
    void testBuilderEqualsDirectBuild() {
        byte[] payload = "HELLO".getBytes();

        byte[] f1 = LLP.buildFrame(LLPMessageType.DATA.value(), 10, payload);

        byte[] f2 = LLP.frameBuilder()
                .type(LLPMessageType.DATA)
                .id(10)
                .payload(payload)
                .build();

        assertArrayEquals(f1, f2);
    }

    @Test
    void testIdBoundaries() {
        byte[] frameMin = LLP.buildFrame(LLPMessageType.DATA.value(), 0, new byte[0]);
        byte[] frameMax = LLP.buildFrame(LLPMessageType.DATA.value(), 0xFFFF, new byte[0]);

        assertNotNull(frameMin);
        assertNotNull(frameMax);
    }

    @Test
    void testRandomPayload() {
        byte[] payload = new byte[100];
        new java.util.Random().nextBytes(payload);

        byte[] frame = LLP.buildFrame(LLPMessageType.DATA.value(), 1, payload);

        assertNotNull(frame);
    }

    @Test
    void testBuildVersion() {
        byte[] payload = new byte[]{
                0x11, (byte) 0xAA, 0x22, (byte) 0xAA, 0x33
        };
        byte[] frame = LLP.buildData(1, payload);

        byte frameVersion = frame[2];
        assertEquals(LLP.PROTOCOL_VERSION, frameVersion);
    }

    @Test
    void testStuffingSingleAA() {
        byte[] payload = {(byte) 0xAA};

        byte[] frame = LLP.buildFrame(LLPMessageType.DATA.value(), 1, payload);

        // Search for sequence AA 00 (stuffed)
        boolean found = false;
        for (int i = 2; i < frame.length - 1; i++) {
            if ((frame[i] == (byte) 0xAA) && (frame[i + 1] == 0x00)) {
                found = true;
                break;
            }
        }

        assertTrue(found, "Stuffed sequence AA 00 not found");
    }

    @Test
    void testStuffingMultipleAA() {
        byte[] payload = {(byte) 0xAA, (byte) 0xAA, (byte) 0xAA};

        byte[] frame = LLP.buildFrame(LLPMessageType.DATA.value(), 1, payload);

        int countAA = 0;
        int countStuffed = 0;

        for (int i = 2; i < frame.length; i++) {
            if (frame[i] == (byte) 0xAA) {
                countAA++;
                if (i + 1 < frame.length && frame[i + 1] == 0x00) {
                    countStuffed++;
                }
            }
        }

        assertEquals(countAA, countStuffed, "Every AA must be stuffed");
    }

    @Test
    void testNoFalseHeaderInsideFrame() {
        byte[] payload = new byte[100];
        new Random().nextBytes(payload);

        byte[] frame = LLP.buildFrame(LLPMessageType.DATA.value(), 1, payload);

        for (int i = 2; i < frame.length - 1; i++) {
            if (frame[i] == (byte) 0xAA && frame[i + 1] == (byte) 0x55) {
                fail("Found forbidden sequence AA 55 inside stuffed frame");
            }
        }
    }

    @Test
    void testConsistency() {
        for (int i = 0; i < 10000; i++) {
            byte[] payload = new byte[100];
            new Random().nextBytes(payload);

            byte[] frame = LLP.buildFrame(LLPMessageType.DATA.value(), 1, payload);
            List<LLPFrame> result = LLP.newParser().processBytes(frame);

            if (result.isEmpty()) {
                throw new RuntimeException("The generated frame could not be parsed again: " + HexFormat.of().formatHex(frame).toUpperCase(Locale.ROOT));
            }

            assertEquals(1, result.size(), "The number of parsed frames must be 1");
            assertEquals(LLPMessageType.DATA.value(), result.getFirst().messageType().orElseThrow(() -> new IllegalArgumentException("The message type does not match the original frame: " + HexFormat.of().formatHex(frame).toUpperCase(Locale.ROOT))).value());

            String originalHexPayload = HexFormat.of().formatHex(payload).toUpperCase(Locale.ROOT);
            String parsedHexPayload = HexFormat.of().formatHex(result.getFirst().payload()).toUpperCase(Locale.ROOT);
            assertEquals(originalHexPayload, parsedHexPayload);
        }
    }

    @Test
    void testBuildFrameWithStuffedCRCAndPayload() {
        byte[] payload = HexFormat.of().parseHex("bf4008211f8191eca98c2ee3d01985d9858689fd571a2df4df41545eba69838d12da79b79de4f425a4596e5dd8f0de04fee43a0d71b4f0fdebce1274a66cac08459a1159f395b642afabe6bd3684193c5d5fbe1560428c6527aa21aa53233dba8932467f");
        byte[] frame = LLP.buildFrame(LLPMessageType.DATA.value(), 1, payload);

        // Check that there is stuffing in the frame
        boolean foundStuff = false;
        for (int i = 2; i < frame.length - 1; i++) {
            if (frame[i] == (byte) 0xAA && frame[i + 1] == 0x00) {
                foundStuff = true;
                break;
            }
        }

        assertTrue(foundStuff, "Expected stuffed bytes not found");

        // Destuff
        byte[] unstuffed = destuff(frame);

        // Validate the CRC manually
        int crcExpected = CRC16CCITT.calculate(unstuffed, 0, unstuffed.length - 2);

        int crcFrame =
                (unstuffed[unstuffed.length - 2] & 0xFF) |
                        ((unstuffed[unstuffed.length - 1] & 0xFF) << 8);

        assertEquals(crcExpected, crcFrame);

        // Verify that there are NO fake headers
        for (int i = 2; i < frame.length - 1; i++) {
            assertFalse(
                    frame[i] == (byte) 0xAA && frame[i + 1] == (byte) 0x55,
                    "Found forbidden AA55 sequence"
            );
        }
    }

    private byte[] destuff(byte[] frame) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(frame.length);

        // Copy MAGIC
        out.write(frame, 0, 2);

        for (int i = 2; i < frame.length; i++) {
            byte b = frame[i];

            if (b == (byte) 0xAA) {
                if (i + 1 < frame.length && frame[i + 1] == 0x00) {
                    out.write(0xAA);
                    i++;
                    continue;
                }
            }

            out.write(b);
        }

        return out.toByteArray();
    }
}