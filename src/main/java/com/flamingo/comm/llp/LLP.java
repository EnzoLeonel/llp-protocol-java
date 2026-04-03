package com.flamingo.comm.llp;

/**
 * LLP Protocol facade.
 *
 * <p>This class provides a simplified and unified entry point for working with
 * the LLP (Lightweight Link Protocol) library.</p>
 *
 * <p>It allows creating parsers, building frames, and accessing protocol constants
 * without needing to interact directly with low-level classes.</p>
 *
 * <p>Typical usage:</p>
 * <pre>
 *     LLPParser parser = LLP.newParser();
 *
 *     byte[] frame = LLP.buildPing(1);
 *
 *     LLPFrameBuilder.Builder = LLP.frameBuilder()
 *         .type(LLP.MessageType.DATA)
 *         .id(10)
 *         .payload("Hello");
 * </pre>
 *
 * <p>This class is a utility facade and should not be instantiated.</p>
 */
public final class LLP {
    public static final byte PROTOCOL_VERSION = 0x02;

    /**
     * Private constructor to prevent instantiation.
     */
    private LLP() {
        // Utility class
    }

    /**
     * Creates a new parser with default configuration.
     *
     * @return a new {@link LLPParser} instance
     */
    public static LLPParser newParser() {
        return new LLPParser();
    }

    /**
     * Creates a new parser with a custom maximum payload size.
     *
     * @param maxPayload maximum payload size in bytes
     * @return a new {@link LLPParser} instance
     */
    public static LLPParser newParser(int maxPayload) {
        return new LLPParser(maxPayload);
    }

    /**
     * Creates a new parser with custom payload size and timeout.
     *
     * @param maxPayload maximum payload size in bytes
     * @param timeoutMs  frame timeout in milliseconds
     * @return a new {@link LLPParser} instance
     */
    public static LLPParser newParser(int maxPayload, long timeoutMs) {
        return new LLPParser(maxPayload, timeoutMs);
    }

    /**
     * Starts building a frame using a fluent API.
     *
     * @return a new {@link LLPFrameBuilder.Builder} instance
     */
    public static LLPFrameBuilder.Builder frameBuilder() {
        return new LLPFrameBuilder.Builder();
    }

    /**
     * Builds a raw LLP frame.
     *
     * @param type    message type
     * @param id      message identifier (0-65535)
     * @param payload payload data (nullable)
     * @return encoded frame as byte array
     */
    public static byte[] buildFrame(byte type, int id, byte[] payload) {
        return LLPFrameBuilder.build(type, id, payload);
    }

    /**
     * Builds a PING frame.
     *
     * @param id message identifier (0-65535)
     * @return encoded frame
     */
    public static byte[] buildPing(int id) {
        return buildFrame(LLPMessageType.PING.value(), id, null);
    }

    /**
     * Builds an ACK frame.
     *
     * @param id   message identifier (0-65535)
     * @param code acknowledgment code
     * @return encoded frame
     */
    public static byte[] buildAck(int id, byte code) {
        return buildFrame(LLPMessageType.ACK.value(), id, new byte[]{code});
    }

    /**
     * Builds a DATA frame.
     *
     * @param id   message identifier (0-65535)
     * @param data payload data
     * @return encoded frame
     */
    public static byte[] buildData(int id, byte[] data) {
        return buildFrame(LLPMessageType.DATA.value(), id, data);
    }

    /**
     * Message type constants for convenience.
     *
     * <p>These values mirror {@link LLPMessageType} but are provided as raw bytes
     * for quick usage without enums.</p>
     */
    public static final class MessageType {
        public static final byte PING = LLPMessageType.PING.value();
        public static final byte ACK = LLPMessageType.ACK.value();
        public static final byte NACK = LLPMessageType.NACK.value();
        public static final byte DATA = LLPMessageType.DATA.value();
        public static final byte CONFIG = LLPMessageType.CONFIG.value();
        public static final byte STATUS = LLPMessageType.STATUS.value();
        public static final byte COMMAND = LLPMessageType.COMMAND.value();
        public static final byte EVENT = LLPMessageType.EVENT.value();
        public static final byte ERROR = LLPMessageType.ERROR.value();

        private MessageType() {
        }
    }

    /**
     * Error code constants used in ACK/NACK responses.
     */
    public static final class ErrorCode {
        public static final byte OK = 0x00;
        public static final byte CHECKSUM = 0x01;
        public static final byte TYPE = 0x02;
        public static final byte PAYLOAD_LEN = 0x03;
        public static final byte TIMEOUT = 0x04;
        public static final byte SYNC = 0x05;
        public static final byte BUFFER_FULL = 0x06;

        private ErrorCode() {
        }
    }
}