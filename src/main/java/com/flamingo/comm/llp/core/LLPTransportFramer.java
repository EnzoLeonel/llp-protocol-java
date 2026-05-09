package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.util.ByteWriter;
import com.flamingo.comm.llp.util.CRC16CCITT;

import java.util.Arrays;

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

    /**
     * Builds a complete LLP transport frame and writes it into the provided {@link ByteWriter}.
     *
     * <p>This method performs the full framing process including:
     * <ul>
     *     <li>Writing magic bytes (frame header)</li>
     *     <li>Encoding payload length (little-endian, with byte stuffing)</li>
     *     <li>Writing payload with byte stuffing</li>
     *     <li>Calculating and appending CRC16-CCITT (also stuffed)</li>
     * </ul>
     *
     * <p><b>Performance considerations:</b>
     * <ul>
     *     <li>No internal buffers are allocated</li>
     *     <li>No defensive copies are made over the provided payload</li>
     *     <li>The payload array is consumed directly for maximum efficiency</li>
     * </ul>
     *
     * <p><b>Thread-safety and immutability:</b>
     * <ul>
     *     <li>This method is <b>not thread-safe</b> by itself</li>
     *     <li>The provided {@code payload} array MUST NOT be modified while this method is executing</li>
     *     <li>No immutability guarantees are enforced internally</li>
     *     <li>If immutability or multi-threaded safety is required, it must be handled externally
     *         (e.g., by copying the payload or using a higher-level wrapper)</li>
     * </ul>
     *
     * <p><b>Important:</b> The caller is responsible for ensuring that the provided
     * {@link ByteWriter} has enough capacity to hold the resulting frame, including
     * worst-case byte stuffing expansion.
     *
     * @param payload the payload to encode (may be {@code null}, treated as empty)
     * @param writer  destination writer where the encoded frame will be written
     * @return the total number of bytes written to the writer
     */
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
     * Builds an LLP frame into a pre-allocated byte array.
     *
     * <p>This method encodes the given payload into a complete LLP transport frame
     * and writes it into the provided output buffer starting at the given offset.
     *
     * <p><b>Performance characteristics:</b>
     * <ul>
     *     <li>No internal allocations are performed</li>
     *     <li>No defensive copies are made over the provided payload</li>
     *     <li>Designed for high-performance and low-latency scenarios</li>
     * </ul>
     *
     * <p><b>Thread-safety and immutability:</b>
     * <ul>
     *     <li>This method is <b>not thread-safe</b></li>
     *     <li>The provided {@code payload} array MUST NOT be modified while this method is executing</li>
     *     <li>No immutability guarantees are enforced</li>
     *     <li>If thread-safety or immutability is required, it must be handled externally</li>
     * </ul>
     *
     * <p><b>Buffer requirements:</b>
     * <ul>
     *     <li>The {@code outBuffer} must have enough capacity for the worst-case frame size
     *         (including byte stuffing expansion)</li>
     *     <li>This method validates the capacity using {@link #estimateMaxSize(int)}</li>
     * </ul>
     *
     * @param payload   the raw payload to wrap (may be {@code null}, treated as empty)
     * @param outBuffer the destination buffer
     * @param offset    the starting index in the destination buffer
     * @return the total number of bytes written into {@code outBuffer}
     * @throws IllegalArgumentException if {@code outBuffer} is too small for the worst-case scenario
     */
    public static int build(byte[] payload, byte[] outBuffer, int offset) {

        int payloadLen = payload != null ? payload.length : 0;

        // Worst-case calculation
        int maxSize = offset + estimateMaxSize(payloadLen);

        if (outBuffer.length < maxSize) {
            throw new IllegalArgumentException(
                    "Output buffer too small. Required worst-case: " + maxSize + ", provided: " + outBuffer.length
            );
        }

        ArrayByteWriter writer = new ArrayByteWriter(outBuffer, offset);
        return buildInternal(payload, writer);
    }

    /**
     * Builds an LLP frame using a custom {@link ByteWriter}.
     *
     * <p>This method encodes the given payload into a complete LLP transport frame
     * and writes it through the provided {@link ByteWriter}. This allows integration
     * with different output targets such as {@code ByteBuffer}, streams, or custom
     * high-performance buffers.
     *
     * <p><b>Performance characteristics:</b>
     * <ul>
     *     <li>No internal allocations are performed</li>
     *     <li>No defensive copies are made over the provided payload</li>
     *     <li>Designed for zero-copy and high-throughput scenarios</li>
     * </ul>
     *
     * <p><b>Thread-safety and immutability:</b>
     * <ul>
     *     <li>This method is <b>not thread-safe</b></li>
     *     <li>The provided {@code payload} array MUST NOT be modified while this method is executing</li>
     *     <li>The thread-safety of this method depends entirely on the provided {@code ByteWriter}</li>
     *     <li>No immutability guarantees are enforced</li>
     *     <li>If thread-safety or immutability is required, it must be handled externally</li>
     * </ul>
     *
     * <p><b>Important:</b>
     * <ul>
     *     <li>The {@code ByteWriter} implementation is responsible for handling capacity, bounds,
     *         and any synchronization if required</li>
     * </ul>
     *
     * @param payload the raw payload (may be {@code null}, treated as empty)
     * @param writer  the destination writer
     * @return the total number of bytes written
     */
    public static int build(byte[] payload, ByteWriter writer) {
        return buildInternal(payload, writer);
    }

    /**
     * Builds an LLP frame in a safe and self-contained manner.
     *
     * <p>This method creates a new byte array containing the fully encoded LLP frame,
     * including header, payload, CRC, and byte stuffing. The returned array is sized
     * exactly to the number of bytes written.
     *
     * <p><b>Safety guarantees:</b>
     * <ul>
     *     <li>No internal buffers are exposed</li>
     *     <li>The returned array is independent and can be freely modified by the caller</li>
     *     <li>No shared mutable state is used</li>
     * </ul>
     *
     * <p><b>Thread-safety:</b>
     * <ul>
     *     <li>This method is thread-safe for typical use cases</li>
     *     <li>It does not rely on external mutable state</li>
     * </ul>
     *
     * <p><b>Performance considerations:</b>
     * <ul>
     *     <li>Allocates a new buffer for each invocation</li>
     *     <li>May perform an additional array copy to return a right-sized result</li>
     *     <li>Less efficient than {@link #build(byte[], byte[], int)} but safer and easier to use</li>
     * </ul>
     *
     * <p>The payload is defensively copied to prevent external modifications
     * during frame construction, ensuring consistency even in concurrent environments.
     *
     * @param payload the payload to encode (may be {@code null}, treated as empty)
     * @return a new byte array containing the encoded LLP frame
     */
    public static byte[] buildSafe(byte[] payload) {
        byte[] safePayload = payload != null ? payload.clone() : new byte[0];
        byte[] outBuffer = new byte[estimateMaxSize(safePayload.length)];
        int written = build(safePayload, outBuffer, 0);

        return Arrays.copyOf(outBuffer, written);
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
     * Returns the maximum frame size based on the payload in the worst-case scenario (where all its bytes are padded)
     *
     * @param payloadLen Payload size. Must be greater than or equal to 0
     * @return the size of the frame in the worst-case scenario.
     */
    public static int estimateMaxSize(int payloadLen) {
        if (payloadLen < 0) {
            throw new IllegalArgumentException("payloadLen must be a positive number");
        }
        // Worst-case calculation:
        // Magic(2) + StuffedLen(4) + StuffedPayload(len*2) + StuffedCRC(4)
        return 2 + 4 + (payloadLen * 2) + 4;
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