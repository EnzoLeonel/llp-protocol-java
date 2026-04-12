package com.flamingo.comm.llp.core;

import java.util.Optional;

/**
 * LLP Parser Error Codes
 */
public enum ErrorCode {
    OK((byte) 0x00, "No error"),
    CHECKSUM_INVALID((byte) 0x01, "CRC checksum mismatch"),
    PAYLOAD_LEN_INVALID((byte) 0x02, "Payload length exceeds maximum"),
    TIMEOUT((byte) 0x03, "Frame timeout - incomplete frame"),
    SYNC_ERROR((byte) 0x04, "Synchronization error"),
    BUFFER_FULL((byte) 0x05, "Buffer overflow");

    private final byte code;
    private final String description;

    ErrorCode(byte code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Retrieve the error code from a byte
     *
     * @param code byte received
     * @return an {@link Optional} containing the error code, or empty if the error code is not found
     */
    public static Optional<ErrorCode> fromCode(byte code) {
        for (ErrorCode err : values()) {
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