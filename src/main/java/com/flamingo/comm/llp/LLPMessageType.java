package com.flamingo.comm.llp;

import java.util.Optional;

/**
 * Message types supported by the LLP Protocol.
 * <p>
 * Range 0x00-0x15: base types of the protocol
 * Range 0x16-0xFF: available for custom applications
 */
public enum LLPMessageType {
    PING((byte) 0x01),
    ACK((byte) 0x02),
    NACK((byte) 0x03),
    DATA((byte) 0x10),
    CONFIG((byte) 0x11),
    STATUS((byte) 0x12),
    COMMAND((byte) 0x13),
    EVENT((byte) 0x14),
    ERROR((byte) 0x15);

    private final byte value;

    LLPMessageType(byte value) {
        this.value = value;
    }

    /**
     * Retrieves the message type from a byte value.
     *
     * @return an {@link Optional} containing the message type, or empty if the message type is not found
     */
    public static Optional<LLPMessageType> fromValue(byte value) {
        for (LLPMessageType type : values()) {
            if (type.value == value) {
                return Optional.of(type);
            }
        }
        return Optional.empty(); // Custom type
    }

    public byte value() {
        return value;
    }
}