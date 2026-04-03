package com.flamingo.comm.llp;

import java.util.Arrays;
import java.util.Optional;

/**
 * This represents a fully received and validated LLP frame.
 * <p>
 * Immutable and thread-safe.
 */
public class LLPFrame {
    public static final int DEFAULT_MAX_PAYLOAD = 512;

    private final byte type;
    private final int id;
    private final byte version;
    private final byte[] payload;
    private final int crc;
    private final long timestamp;

    public LLPFrame(byte type, int id, byte version, byte[] payload, int crc) {
        this(type, id, version, payload, crc, System.currentTimeMillis());
    }

    public LLPFrame(byte type, int id, byte version, byte[] payload, int crc, long timestamp) {
        this.type = type;
        this.id = id;
        this.version = version;
        this.payload = payload != null ? payload.clone() : new byte[0];
        this.crc = crc;
        this.timestamp = timestamp;
    }

    public byte type() {
        return type;
    }

    public Optional<LLPMessageType> messageType() {
        return LLPMessageType.fromValue(type);
    }

    public int id() {
        return id;
    }

    public byte version() {
        return version;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public int payloadLength() {
        return payload.length;
    }

    public int crc() {
        return crc;
    }

    public long timestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format(
                "LLPFrame{type=0x%02X, id=%d, version=0x%02X, payloadLen=%d, crc=0x%04X, timestamp=%d}",
                type, id, version, payload.length, crc, timestamp
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LLPFrame frame = (LLPFrame) o;
        return type == frame.type &&
                id == frame.id &&
                version == frame.version &&
                crc == frame.crc &&
                Arrays.equals(payload, frame.payload);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, id, version, crc, Arrays.hashCode(payload));
    }
}
