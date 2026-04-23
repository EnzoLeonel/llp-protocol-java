package com.flamingo.comm.llp.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TransportErrorCodeTest {

    @Test
    void testFromCodeValidValues() {
        for (TransportErrorCode error : TransportErrorCode.values()) {
            Optional<TransportErrorCode> result = TransportErrorCode.fromCode(error.code());

            assertTrue(result.isPresent(), "Expected code to be found: " + error);
            assertEquals(error, result.get(), "Returned enum should match original");
        }
    }

    @Test
    void testFromCodeInvalidValue() {
        byte invalidCode = (byte) 0x7F;

        Optional<TransportErrorCode> result = TransportErrorCode.fromCode(invalidCode);

        assertTrue(result.isEmpty(), "Invalid code should return empty Optional");
    }

    @Test
    void testFromCodeBoundaryValues() {
        // Extreme byte values
        assertTrue(TransportErrorCode.fromCode(Byte.MIN_VALUE).isEmpty());
        assertTrue(TransportErrorCode.fromCode(Byte.MAX_VALUE).isEmpty());
    }

    @Test
    void testCodeGetter() {
        assertEquals((byte) 0x00, TransportErrorCode.OK.code());
        assertEquals((byte) 0x01, TransportErrorCode.CHECKSUM_INVALID.code());
        assertEquals((byte) 0x02, TransportErrorCode.PAYLOAD_LEN_INVALID.code());
        assertEquals((byte) 0x03, TransportErrorCode.TIMEOUT.code());
        assertEquals((byte) 0x04, TransportErrorCode.SYNC_ERROR.code());
        assertEquals((byte) 0x05, TransportErrorCode.BUFFER_FULL.code());
    }

    @Test
    void testDescriptionGetter() {
        assertEquals("No error", TransportErrorCode.OK.description());
        assertEquals("CRC checksum mismatch", TransportErrorCode.CHECKSUM_INVALID.description());
        assertEquals("Payload length exceeds maximum", TransportErrorCode.PAYLOAD_LEN_INVALID.description());
        assertEquals("Frame timeout - incomplete frame", TransportErrorCode.TIMEOUT.description());
        assertEquals("Synchronization error", TransportErrorCode.SYNC_ERROR.description());
        assertEquals("Buffer overflow", TransportErrorCode.BUFFER_FULL.description());
    }

    @Test
    void testCodesAreUnique() {
        for (TransportErrorCode e1 : TransportErrorCode.values()) {
            for (TransportErrorCode e2 : TransportErrorCode.values()) {
                if (e1 != e2) {
                    assertNotEquals(
                            e1.code(),
                            e2.code(),
                            "Duplicate error code found between " + e1 + " and " + e2
                    );
                }
            }
        }
    }

    @Test
    void testFromCodeIsDeterministic() {
        byte code = TransportErrorCode.TIMEOUT.code();

        Optional<TransportErrorCode> r1 = TransportErrorCode.fromCode(code);
        Optional<TransportErrorCode> r2 = TransportErrorCode.fromCode(code);

        assertEquals(r1, r2, "fromCode should be deterministic");
    }

    @Test
    void testEnumCoverage() {
        // Force the execution of `values()` and ensure that there are elements
        assertTrue(TransportErrorCode.values().length > 0);
    }

    @Test
    void testToStringNotNull() {
        for (TransportErrorCode error : TransportErrorCode.values()) {
            assertNotNull(error.toString());
        }
    }
}
