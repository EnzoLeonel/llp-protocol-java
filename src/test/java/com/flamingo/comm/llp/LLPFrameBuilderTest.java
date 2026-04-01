package com.flamingo.comm.llp;

import com.flamingo.comm.llp.crc.CRC16CCITT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LLPFrameBuilderTest {

    @Test
    void testBuildSimpleFrame() {
        byte[] frame = LLP.buildPing(123);

        assertNotNull(frame);
        assertEquals((byte) 0xAA, frame[0]);
        assertEquals((byte) 0x55, frame[1]);
        assertEquals(frame[2], LLPMessageType.PING.value());
    }

    @Test
    void testBuildFrameStructure() {
        byte[] payload = {0x01, 0x02};
        int id = 0x1234;

        byte[] frame = LLP.buildFrame(LLPMessageType.DATA.value(), id, payload);

        assertEquals((byte) 0xAA, frame[0]);
        assertEquals((byte) 0x55, frame[1]);

        assertEquals(LLPMessageType.DATA.value(), frame[2]);

        // ID little endian
        assertEquals((byte) 0x34, frame[3]);
        assertEquals((byte) 0x12, frame[4]);

        // Length
        assertEquals((byte) 0x02, frame[5]);
        assertEquals((byte) 0x00, frame[6]);

        // Payload
        assertEquals(0x01, frame[7]);
        assertEquals(0x02, frame[8]);
    }

    @Test
    void testBuildDataFrame() {
        byte[] data = new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
        byte[] frame = LLP.buildData(42, data);

        assertNotNull(frame);
        assertTrue(frame.length > data.length); // Overhead included
        assertEquals(frame[2], LLPMessageType.DATA.value());
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
        assertEquals(0, frame[5]);
        assertEquals(0, frame[6]);

        // Frame mínimo: 7 header + 2 CRC
        assertEquals(9, frame.length);
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
}