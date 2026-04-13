package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.util.CRC16CCITT;
import com.flamingo.comm.llp.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Transport-layer state machine responsible for deframing LLP byte streams.
 *
 * <p>This component processes a continuous stream of bytes and extracts valid LLP frames by:
 * <ul>
 *     <li>Synchronizing using magic bytes</li>
 *     <li>Handling byte unstuffing (escape sequences)</li>
 *     <li>Validating frame integrity using CRC16-CCITT</li>
 * </ul>
 *
 * <p>The deframer is stateful and not thread-safe for concurrent byte ingestion,
 * but supports concurrent listener notification and frame consumption.</p>
 *
 * <p>Valid frames are emitted as {@link LLPRawFrame} instances.</p>
 */
public final class LLPTransportDeframer {

    private static final Logger logger = LoggerFactory.getLogger(LLPTransportDeframer.class);

    private static final byte MAGIC_1 = (byte) 0xAA;
    private static final byte MAGIC_2 = (byte) 0x55;
    private static final long DEFAULT_TIMEOUT_MS = 2000;
    private static final int DEFAULT_MAX_PAYLOAD_SIZE_BYTES = 1024 * 1024; // 1 MB

    private final byte[] headerBuf = new byte[4];
    private final byte[] payload;
    private final long timeoutMs;

    private final Queue<LLPFrameListener> listeners = new ConcurrentLinkedQueue<>();
    private final Statistics statistics = new Statistics();

    private State state = State.WAIT_MAGIC1;
    private boolean escapePending = false;

    private int payloadLen = 0;
    private int payloadIdx = 0;
    private int crcReceived = 0;
    private int crcCalculated = 0xFFFF;

    private long lastByteTime = System.currentTimeMillis();

    /**
     * Creates a deframer with default configuration.
     */
    public LLPTransportDeframer() {
        this(DEFAULT_MAX_PAYLOAD_SIZE_BYTES, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Creates a deframer with a custom maximum payload size.
     *
     * @param maxPayloadBytes maximum allowed payload size in bytes
     */
    public LLPTransportDeframer(int maxPayloadBytes) {
        this(maxPayloadBytes, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Creates a deframer with custom configuration.
     *
     * @param maxPayloadBytes maximum allowed payload size in bytes
     * @param timeoutMs  timeout in milliseconds between bytes before resetting the parser
     */
    public LLPTransportDeframer(int maxPayloadBytes, long timeoutMs) {
        if (maxPayloadBytes < 1) {
            maxPayloadBytes = DEFAULT_MAX_PAYLOAD_SIZE_BYTES;
        }

        if (timeoutMs < 1) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }

        this.payload = new byte[maxPayloadBytes];
        this.timeoutMs = timeoutMs;
    }

    /**
     * Processes a single byte from the input stream.
     *
     * <p>This method advances the internal state machine and may produce a complete frame.</p>
     *
     * @param b incoming byte
     * @return a completed {@link LLPRawFrame}, or {@code null} if the frame is not yet complete
     */
    public LLPRawFrame processByte(byte b) {

        // Timeout handling
        if (state != State.WAIT_MAGIC1) {
            if (System.currentTimeMillis() - lastByteTime > timeoutMs) {
                logger.warn("Frame timeout - resetting parser");
                statistics.recordTimeout();
                reset();
                notifyError(ErrorCode.TIMEOUT);

                // Allow immediate resync if current byte starts a new frame
                if (b == MAGIC_1) {
                    state = State.WAIT_MAGIC2;
                }
                return null;
            }
        }

        lastByteTime = System.currentTimeMillis();

        // ================= ESCAPE / BYTE UNSTUFFING =================
        if (state != State.WAIT_MAGIC1 && state != State.WAIT_MAGIC2) {

            if (escapePending) {
                escapePending = false;

                if (b == MAGIC_2) {
                    // Overlapped frame detected (0xAA 0x55 inside payload)
                    logger.warn("Overlapped frame detected, resynchronizing");
                    statistics.recordError();
                    notifyError(ErrorCode.SYNC_ERROR);

                    crcCalculated = 0xFFFF;
                    crcCalculated = CRC16CCITT.updateCRC(crcCalculated, MAGIC_1);
                    crcCalculated = CRC16CCITT.updateCRC(crcCalculated, MAGIC_2);

                    headerBuf[0] = MAGIC_1;
                    headerBuf[1] = MAGIC_2;

                    state = State.READ_LEN_L;
                    return null;

                } else if (b == 0x00) {
                    // Escaped MAGIC_1 restored
                    b = MAGIC_1;

                } else {
                    logger.error("Invalid escape sequence: 0xAA followed by 0x{}",
                            Integer.toHexString(b & 0xFF));
                    statistics.recordError();
                    reset();
                    notifyError(ErrorCode.SYNC_ERROR);
                    return null;
                }

            } else if (b == MAGIC_1) {
                escapePending = true;
                return null;
            }
        }
        // ============================================================

        switch (state) {

            case WAIT_MAGIC1:
                if (b == MAGIC_1) {
                    headerBuf[0] = b;
                    state = State.WAIT_MAGIC2;
                }
                break;

            case WAIT_MAGIC2:
                if (b == MAGIC_2) {
                    headerBuf[1] = b;

                    crcCalculated = 0xFFFF;
                    crcCalculated = CRC16CCITT.updateCRC(crcCalculated, MAGIC_1);
                    crcCalculated = CRC16CCITT.updateCRC(crcCalculated, MAGIC_2);

                    state = State.READ_LEN_L;

                } else if (b == MAGIC_1) {
                    // Stay in sync (robustness against repeated MAGIC_1)
                    state = State.WAIT_MAGIC2;

                } else {
                    state = State.WAIT_MAGIC1;
                }
                break;

            case READ_LEN_L:
                headerBuf[2] = b;
                crcCalculated = CRC16CCITT.updateCRC(crcCalculated, b);
                state = State.READ_LEN_H;
                break;

            case READ_LEN_H:
                headerBuf[3] = b;
                crcCalculated = CRC16CCITT.updateCRC(crcCalculated, b);

                payloadLen = (headerBuf[2] & 0xFF) | ((headerBuf[3] & 0xFF) << 8);

                if (payloadLen > payload.length) {
                    logger.error("Payload length {} exceeds maximum {}", payloadLen, payload.length);
                    statistics.recordError();
                    reset();
                    notifyError(ErrorCode.PAYLOAD_LEN_INVALID);
                    return null;
                }

                payloadIdx = 0;
                state = (payloadLen == 0) ? State.READ_CRC_L : State.READ_PAYLOAD;
                break;

            case READ_PAYLOAD:
                payload[payloadIdx++] = b;
                crcCalculated = CRC16CCITT.updateCRC(crcCalculated, b);

                if (payloadIdx == payloadLen) {
                    state = State.READ_CRC_L;
                }
                break;

            case READ_CRC_L:
                crcReceived = (b & 0xFF);
                state = State.READ_CRC_H;
                break;

            case READ_CRC_H:
                crcReceived |= ((b & 0xFF) << 8);

                if (crcReceived != crcCalculated) {
                    logger.error("CRC mismatch: received=0x{}, calculated=0x{}",
                            Integer.toHexString(crcReceived),
                            Integer.toHexString(crcCalculated));
                    statistics.recordError();
                    reset();
                    notifyError(ErrorCode.CHECKSUM_INVALID);
                    return null;
                }

                LLPRawFrame frame = new LLPRawFrame(payload, payloadLen, crcCalculated, System.currentTimeMillis());

                statistics.recordSuccess();
                reset();

                notifySuccess(frame);

                return frame;
        }

        return null;
    }

    /**
     * Processes multiple bytes from the input stream.
     *
     * @param data input byte array
     * @return list of completed frames (possibly empty)
     */
    public List<LLPRawFrame> processBytes(byte[] data) {
        List<LLPRawFrame> frames = new ArrayList<>();
        for (byte b : data) {
            LLPRawFrame frame = processByte(b);
            if (frame != null) {
                frames.add(frame);
            }
        }
        return frames;
    }

    /**
     * Resets the internal state machine to its initial synchronization state.
     */
    private void reset() {
        state = State.WAIT_MAGIC1;
        payloadIdx = 0;
        crcCalculated = 0xFFFF;
        escapePending = false;
    }

    /**
     * Registers a listener to receive frame events.
     *
     * @param listener listener to add
     */
    public void addListener(LLPFrameListener listener) {
        listeners.offer(listener);
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener listener to remove
     */
    public void removeListener(LLPFrameListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns runtime statistics of the deframer.
     *
     * @return statistics instance
     */
    public Statistics getStatistics() {
        return statistics;
    }

    private void notifySuccess(LLPRawFrame frame) {
        for (LLPFrameListener listener : listeners) {
            try {
                listener.onFrameReceived(frame);
            } catch (Exception e) {
                logger.error("Listener error", e);
            }
        }
    }

    private void notifyError(ErrorCode errorCode) {
        for (LLPFrameListener listener : listeners) {
            try {
                listener.onFrameError(errorCode);
            } catch (Exception e) {
                logger.error("Listener error", e);
            }
        }
    }

    private enum State {
        WAIT_MAGIC1,
        WAIT_MAGIC2,
        READ_LEN_L,
        READ_LEN_H,
        READ_PAYLOAD,
        READ_CRC_L,
        READ_CRC_H
    }

    /**
     * Listener interface for receiving deframer events.
     */
    public interface LLPFrameListener {

        /**
         * Invoked when a valid frame is successfully parsed.
         *
         * @param frame parsed frame
         */
        void onFrameReceived(LLPRawFrame frame);

        /**
         * Invoked when a frame parsing error occurs.
         *
         * @param errorCode error type
         */
        void onFrameError(ErrorCode errorCode);
    }
}