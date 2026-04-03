package com.flamingo.comm.llp;

import java.util.Optional;

/**
 * LLP Parser Error Codes
 */
public enum LLPErrorCode {
    OK((byte) 0x00, "No error"),
    CHECKSUM_INVALID((byte) 0x01, "CRC checksum mismatch"),
    TYPE_INVALID((byte) 0x02, "Invalid message type"),
    PAYLOAD_LEN_INVALID((byte) 0x03, "Payload length exceeds maximum"),
    TIMEOUT((byte) 0x04, "Frame timeout - incomplete frame"),
    SYNC_ERROR((byte) 0x05, "Synchronization error"),
    BUFFER_FULL((byte) 0x06, "Buffer overflow"),
    UNSUPPORTED_VERSION((byte) 0x07, "Unknown or unsupported version");

    private final byte code;
    private final String description;

    LLPErrorCode(byte code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Retrieve the error code from a byte
     *
     * @param code byte received
     * @return an {@link Optional} containing the error code, or empty if the error code is not found
     */
    public static Optional<LLPErrorCode> fromCode(byte code) {
        for (LLPErrorCode err : values()) {
            if (err.code == code) {
                return Optional.of(err);
            }
        }
        return Optional.empty();
    }

    public byte code() {
        return code;
    }

    public String description() {
        return description;
    }
}