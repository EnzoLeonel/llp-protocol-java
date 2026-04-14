package com.flamingo.comm.llp.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    void testFromCodeValidValues() {
        for (ErrorCode error : ErrorCode.values()) {
            Optional<ErrorCode> result = ErrorCode.fromCode(error.code());

            assertTrue(result.isPresent(), "Expected code to be found: " + error);
            assertEquals(error, result.get(), "Returned enum should match original");
        }
    }

    @Test
    void testFromCodeInvalidValue() {
        byte invalidCode = (byte) 0x7F;

        Optional<ErrorCode> result = ErrorCode.fromCode(invalidCode);

        assertTrue(result.isEmpty(), "Invalid code should return empty Optional");
    }

    @Test
    void testFromCodeBoundaryValues() {
        // Extreme byte values
        assertTrue(ErrorCode.fromCode(Byte.MIN_VALUE).isEmpty());
        assertTrue(ErrorCode.fromCode(Byte.MAX_VALUE).isEmpty());
    }

    @Test
    void testCodeGetter() {
        assertEquals((byte) 0x00, ErrorCode.OK.code());
        assertEquals((byte) 0x01, ErrorCode.CHECKSUM_INVALID.code());
        assertEquals((byte) 0x02, ErrorCode.PAYLOAD_LEN_INVALID.code());
        assertEquals((byte) 0x03, ErrorCode.TIMEOUT.code());
        assertEquals((byte) 0x04, ErrorCode.SYNC_ERROR.code());
        assertEquals((byte) 0x05, ErrorCode.BUFFER_FULL.code());
    }

    @Test
    void testDescriptionGetter() {
        assertEquals("No error", ErrorCode.OK.description());
        assertEquals("CRC checksum mismatch", ErrorCode.CHECKSUM_INVALID.description());
        assertEquals("Payload length exceeds maximum", ErrorCode.PAYLOAD_LEN_INVALID.description());
        assertEquals("Frame timeout - incomplete frame", ErrorCode.TIMEOUT.description());
        assertEquals("Synchronization error", ErrorCode.SYNC_ERROR.description());
        assertEquals("Buffer overflow", ErrorCode.BUFFER_FULL.description());
    }

    @Test
    void testCodesAreUnique() {
        for (ErrorCode e1 : ErrorCode.values()) {
            for (ErrorCode e2 : ErrorCode.values()) {
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
        byte code = ErrorCode.TIMEOUT.code();

        Optional<ErrorCode> r1 = ErrorCode.fromCode(code);
        Optional<ErrorCode> r2 = ErrorCode.fromCode(code);

        assertEquals(r1, r2, "fromCode should be deterministic");
    }

    @Test
    void testEnumCoverage() {
        // Force the execution of `values()` and ensure that there are elements
        assertTrue(ErrorCode.values().length > 0);
    }

    @Test
    void testToStringNotNull() {
        for (ErrorCode error : ErrorCode.values()) {
            assertNotNull(error.toString());
        }
    }
}
