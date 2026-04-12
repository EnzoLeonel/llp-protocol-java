package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.util.ByteWriter;
import com.flamingo.comm.llp.util.CRC16CCITT;

/**
 * Pure logic transport framer for LLP protocol.
 * Handles magic bytes, byte stuffing, and CRC calculation without allocating intermediate buffers.
 */
public final class LLPTransportFramer {

    private static final byte MAGIC_1 = (byte) 0xAA;
    private static final byte MAGIC_2 = (byte) 0x55;

    private LLPTransportFramer() {
        // Prevent instantiation
    }

    private static int buildInternal(byte[] payload, ByteWriter writer) {
        if (payload == null) {
            payload = new byte[0];
        }

        int written = 0;

        // 1. Write Magic Bytes (Never stuffed)
        writer.write(MAGIC_1);
        written++;
        writer.write(MAGIC_2);
        written++;

        // 2. Calculate Length
        byte lenL = (byte) (payload.length & 0xFF);
        byte lenH = (byte) ((payload.length >> 8) & 0xFF);

        // 3. Write Length (Stuffed)
        written += writeStuffed(writer, lenL);
        written += writeStuffed(writer, lenH);

        // 4. Initialize and compute CRC
        int crc = 0xFFFF;
        crc = CRC16CCITT.updateCRC(crc, MAGIC_1);
        crc = CRC16CCITT.updateCRC(crc, MAGIC_2);
        crc = CRC16CCITT.updateCRC(crc, lenL);
        crc = CRC16CCITT.updateCRC(crc, lenH);

        // 5. Write Payload and update CRC
        for (byte b : payload) {
            crc = CRC16CCITT.updateCRC(crc, b);
            written += writeStuffed(writer, b);
        }

        // 6. Write CRC (Stuffed)
        byte crcL = (byte) (crc & 0xFF);
        byte crcH = (byte) ((crc >> 8) & 0xFF);

        written += writeStuffed(writer, crcL);
        written += writeStuffed(writer, crcH);

        return written;
    }

    /**
     * Builds the LLP frame into a pre-allocated byte array.
     *
     * @param payload   The raw payload to wrap (can be null for empty payload).
     * @param outBuffer The destination buffer.
     * @param offset    The starting index in the destination buffer.
     * @return The total number of bytes written to the outBuffer.
     * @throws IllegalArgumentException if the outBuffer is too small for the worst-case scenario.
     */
    public static int build(byte[] payload, byte[] outBuffer, int offset) {

        int payloadLen = payload != null ? payload.length : 0;

        // Worst-case calculation:
        // Magic(2) + StuffedLen(4) + StuffedPayload(len*2) + StuffedCRC(4)
        int maxSize = offset + 2 + 4 + (payloadLen * 2) + 4;

        if (outBuffer.length < maxSize) {
            throw new IllegalArgumentException(
                    "Output buffer too small. Required worst-case: " + maxSize + ", provided: " + outBuffer.length
            );
        }

        ArrayByteWriter writer = new ArrayByteWriter(outBuffer, offset);
        return buildInternal(payload, writer);
    }

    /**
     * Builds the LLP frame using a custom ByteWriter implementation.
     * Useful for direct writes to ByteBuffers or streams.
     *
     * @param payload The raw payload.
     * @param writer  The custom writer interface.
     * @return The total number of bytes written.
     */
    public static int build(byte[] payload, ByteWriter writer) {
        return buildInternal(payload, writer);
    }

    /**
     * Writes a byte and applies byte stuffing if it matches MAGIC_1.
     */
    private static int writeStuffed(ByteWriter writer, byte b) {
        writer.write(b);
        if (b == MAGIC_1) {
            writer.write((byte) 0x00);
            return 2;
        }
        return 1;
    }

    /**
     * Lightweight internal ByteWriter for byte arrays.
     */
    static final class ArrayByteWriter implements ByteWriter {

        private final byte[] buffer;
        private int idx;

        ArrayByteWriter(byte[] buffer, int offset) {
            this.buffer = buffer;
            this.idx = offset;
        }

        @Override
        public void write(byte b) {
            // Safety net added just in case logic changes in the future
            if (idx >= buffer.length) {
                throw new IndexOutOfBoundsException("Buffer overflow at index: " + idx);
            }
            buffer[idx++] = b;
        }
    }
}