package com.flamingo.comm.llp.core;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Represents a raw LLP frame extracted at the transport layer.
 *
 * <p>This object is produced by the {@link LLPTransportDeframer} after successful
 * synchronization, byte unstuffing, and CRC validation.</p>
 *
 * <p>It contains only transport-level information and does not interpret
 * the payload structure or protocol layers.</p>
 *
 * <p>This class is immutable and thread-safe.</p>
 */
public final class LLPRawFrame {
    private static final byte[] EMPTY_ARRAY = new byte[0];
    private final byte[] payload;
    private final int crc;
    private final long timestamp;

    /**
     * Creates a new raw frame with the current system timestamp.
     *
     * @param payload raw payload bytes (contains encoded layers)
     * @param crc     validated CRC value
     */
    LLPRawFrame(byte[] payload, int crc) {
        this(payload, crc, System.currentTimeMillis());
    }

    /**
     * Creates a new raw frame.
     *
     * @param payload   raw payload bytes (contains encoded layers)
     * @param crc       validated CRC value
     * @param timestamp creation timestamp in milliseconds
     */
    LLPRawFrame(byte[] payload, int crc, long timestamp) {
        this.crc = crc;
        this.timestamp = timestamp;

        if (payload == null || payload.length == 0) {
            this.payload = EMPTY_ARRAY;
        } else {
            this.payload = payload.clone();
        }
    }

    /**
     * Creates a new raw frame.
     *
     * @param payload    raw payload bytes (contains encoded layers)
     * @param payloadLen length of payload
     * @param crc        validated CRC value
     * @param timestamp  creation timestamp in milliseconds
     * @throws IllegalArgumentException if payloadLen is larger than the payload buffer
     */
    LLPRawFrame(byte[] payload, int payloadLen, int crc, long timestamp) throws IllegalArgumentException {
        this.crc = crc;
        this.timestamp = timestamp;

        if (payload == null || payloadLen <= 0) {
            this.payload = EMPTY_ARRAY;
        } else if (payloadLen > payload.length) {
            throw new IllegalArgumentException(
                    "Invalid payloadLen: exceeds actual payload size"
            );
        } else {
            this.payload = Arrays.copyOf(payload, payloadLen);
        }
    }

    /**
     * Returns a read-only view of the payload.
     *
     * <p>The returned buffer is a duplicate with independent position/limit,
     * but shares the same underlying data.</p>
     *
     * @return read-only ByteBuffer containing payload data
     */
    public ByteBuffer payload() {
        return ByteBuffer.wrap(payload).asReadOnlyBuffer();
    }

    /**
     * Returns the validated CRC value.
     *
     * @return CRC value
     */
    public int crc() {
        return crc;
    }

    /**
     * Returns the timestamp when the frame was created.
     *
     * @return timestamp in milliseconds
     */
    public long timestamp() {
        return timestamp;
    }
}