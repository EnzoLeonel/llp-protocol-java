package com.flamingo.comm.llp;

import com.flamingo.comm.llp.crc.CRC16CCITT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * LLP Frame Builder.
 *
 * <p>This class provides utilities to construct valid LLP protocol frames
 * ready for transmission over any transport layer (TCP, UART, RF, etc).</p>
 *
 * <p>Frame format:</p>
 * <pre>
 * [MAGIC1][MAGIC2][TYPE][ID_L][ID_H][LEN_L][LEN_H][PAYLOAD...][CRC_L][CRC_H]
 * </pre>
 *
 * <p>The CRC16-CCITT is calculated over all bytes except the CRC itself.</p>
 */
public class LLPFrameBuilder {

    private static final Logger logger = LoggerFactory.getLogger(LLPFrameBuilder.class);

    private static final byte MAGIC_1 = (byte) 0xAA;
    private static final byte MAGIC_2 = (byte) 0x55;

    /**
     * Builds a frame using the default maximum payload size.
     *
     * @param type    message type
     * @param id      message identifier (0-65535)
     * @param payload payload data (nullable)
     * @return byte array containing the encoded frame
     * @throws IllegalArgumentException if payload size or id is invalid
     */
    public static byte[] build(byte type, int id, byte[] payload) {
        return build(type, id, payload, LLPFrame.DEFAULT_MAX_PAYLOAD);
    }

    /**
     * Builds a frame with a custom maximum payload size.
     *
     * @param type       message type
     * @param id         message identifier (0-65535)
     * @param payload    payload data (nullable)
     * @param maxPayload maximum allowed payload size
     * @return byte array containing the encoded frame
     */
    public static byte[] build(byte type, int id, byte[] payload, int maxPayload) {

        if (id < 0 || id > 0xFFFF) {
            throw new IllegalArgumentException("ID must be between 0 and 65535");
        }

        if (payload == null) {
            payload = new byte[0];
        }

        if (payload.length > maxPayload) {
            throw new IllegalArgumentException(
                    String.format("Payload size %d exceeds maximum %d", payload.length, maxPayload)
            );
        }

        byte[] frame = new byte[7 + payload.length + 2];
        int idx = 0;

        // Magic
        frame[idx++] = MAGIC_1;
        frame[idx++] = MAGIC_2;

        // Type
        frame[idx++] = type;

        // ID (Little Endian)
        frame[idx++] = (byte) (id & 0xFF);
        frame[idx++] = (byte) ((id >> 8) & 0xFF);

        // Length (Little Endian)
        frame[idx++] = (byte) (payload.length & 0xFF);
        frame[idx++] = (byte) ((payload.length >> 8) & 0xFF);

        // Payload
        if (payload.length > 0) {
            System.arraycopy(payload, 0, frame, idx, payload.length);
            idx += payload.length;
        }

        // CRC
        int crc = CRC16CCITT.calculate(frame, 0, idx);

        frame[idx++] = (byte) (crc & 0xFF);
        frame[idx++] = (byte) ((crc >> 8) & 0xFF);

        logger.debug("Built frame: type=0x{}, id={}, payload_len={}, total_len={}",
                Integer.toHexString(type & 0xFF), id, payload.length, frame.length);

        return frame;
    }

    /**
     * Fluent builder for constructing LLP frames.
     */
    public static class Builder {

        private byte type;
        private int id = 0;
        private byte[] payload = new byte[0];
        private int maxPayload = LLPFrame.DEFAULT_MAX_PAYLOAD;

        /**
         * Sets message type.
         */
        public Builder type(byte type) {
            this.type = type;
            return this;
        }

        /**
         * Sets message type using enum.
         */
        public Builder type(LLPMessageType type) {
            this.type = type.value();
            return this;
        }

        /**
         * Sets message ID (0-65535).
         */
        public Builder id(int id) {
            this.id = id;
            return this;
        }

        /**
         * Sets payload bytes.
         */
        public Builder payload(byte[] payload) {
            this.payload = (payload != null) ? Arrays.copyOf(payload, payload.length) : new byte[0];
            return this;
        }

        /**
         * Sets payload from string (UTF-8 encoded).
         */
        public Builder payload(String payload) {
            this.payload = (payload != null)
                    ? payload.getBytes(StandardCharsets.UTF_8)
                    : new byte[0];
            return this;
        }

        /**
         * Sets maximum payload size.
         */
        public Builder maxPayload(int maxPayload) {
            this.maxPayload = maxPayload;
            return this;
        }

        /**
         * Builds raw frame bytes.
         */
        public byte[] build() {
            return LLPFrameBuilder.build(type, id, payload, maxPayload);
        }

        /**
         * Builds an {@link LLPFrame} object representation.
         */
        public LLPFrame buildFrame() {
            byte[] data = build();
            int crc = (data[data.length - 2] & 0xFF) |
                    ((data[data.length - 1] & 0xFF) << 8);

            return new LLPFrame(type, id, payload, crc);
        }
    }
}