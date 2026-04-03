package com.flamingo.comm.llp;

import com.flamingo.comm.llp.crc.CRC16CCITT;
import com.flamingo.comm.llp.util.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * LLP frame parser based on a byte-oriented state machine.
 *
 * <p>This parser processes incoming data byte-by-byte and reconstructs valid LLP frames.
 * It is designed to work with unreliable or noisy transport layers (e.g., RF, UART, TCP streams),
 * providing resynchronization, timeout handling, CRC validation, and Byte Stuffing.</p>
 */
public class LLPParser {
    private static final Logger logger = LoggerFactory.getLogger(LLPParser.class);

    private static final byte MAGIC_1 = (byte) 0xAA;
    private static final byte MAGIC_2 = (byte) 0x55;
    private static final long DEFAULT_TIMEOUT_MS = 2000;

    private final byte[] headerBuf = new byte[8];
    private final byte[] payload;
    private final long timeoutMs;
    private final Queue<LLPFrame> frameQueue = new ConcurrentLinkedQueue<>();
    private final Statistics statistics = new Statistics();
    // Listeners
    private final Queue<LLPFrameListener> listeners = new ConcurrentLinkedQueue<>();

    private State state = State.WAIT_MAGIC1;
    private boolean escapePending = false;

    private int payloadLen = 0;
    private int payloadIdx = 0;
    private int crcReceived = 0;
    private int crcCalculated = 0xFFFF;
    private long lastByteTime = System.currentTimeMillis();

    /**
     * Creates a parser with default maximum payload size and timeout.
     */
    public LLPParser() {
        this(LLPFrame.DEFAULT_MAX_PAYLOAD);
    }

    /**
     * Creates a parser with a custom maximum payload size.
     *
     * @param maxPayload maximum payload size in bytes
     */
    public LLPParser(int maxPayload) {
        this(maxPayload, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Creates a parser with custom payload size and timeout.
     *
     * @param maxPayload maximum payload size in bytes
     * @param timeoutMs  frame timeout in milliseconds
     */
    public LLPParser(int maxPayload, long timeoutMs) {
        if (maxPayload < 1) {
            maxPayload = LLPFrame.DEFAULT_MAX_PAYLOAD;
        }

        if (timeoutMs < 1) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }

        this.payload = new byte[maxPayload];
        this.timeoutMs = timeoutMs;
    }

    /**
     * Processes a single byte from the input stream, resolving byte stuffing transparently.
     *
     * @param b incoming byte
     * @return a complete {@link LLPFrame} or {@code null} if not complete
     */
    public LLPFrame processByte(byte b) {
        // Timeout handling
        if (state != State.WAIT_MAGIC1) {
            if (System.currentTimeMillis() - lastByteTime > timeoutMs) {
                logger.warn("Frame timeout - resetting parser");
                statistics.recordTimeout();
                reset();
                notifyError(LLPErrorCode.TIMEOUT);

                // Allow a magic byte to restart the sequence immediately
                if (b == MAGIC_1) {
                    state = State.WAIT_MAGIC2;
                }
                return null;
            }
        }
        lastByteTime = System.currentTimeMillis();

        // ================= ESCAPE / STUFFING HANDLING =================
        // Only evaluate escape sequences if we are inside a frame
        if (state != State.WAIT_MAGIC1 && state != State.WAIT_MAGIC2) {
            if (escapePending) {
                escapePending = false;

                if (b == MAGIC_2) {
                    // OVERLAPPED FRAME DETECTED! (0xAA 0x55 sequence found in data)
                    logger.warn("Overlapped frame detected, aborting current and syncing new frame");
                    statistics.recordError();
                    notifyError(LLPErrorCode.SYNC_ERROR);

                    crcCalculated = 0xFFFF;
                    crcCalculated = CRC16CCITT.updateCRC(crcCalculated, MAGIC_1);
                    crcCalculated = CRC16CCITT.updateCRC(crcCalculated, MAGIC_2);
                    headerBuf[0] = MAGIC_1;
                    headerBuf[1] = MAGIC_2;
                    state = State.READ_TYPE;
                    return null; // Consumed as MAGIC_2

                } else if (b == 0x00) {
                    // Escaped data byte recovered. Restore to MAGIC_1.
                    b = MAGIC_1;
                } else {
                    // Invalid sequence
                    logger.error("Invalid sync sequence: 0xAA followed by 0x{}", Integer.toHexString(b & 0xFF));
                    statistics.recordError();
                    reset();
                    notifyError(LLPErrorCode.SYNC_ERROR);
                    return null;
                }
            } else if (b == MAGIC_1) {
                // Suspends processing to wait for the next byte to clarify the sequence
                escapePending = true;
                return null;
            }
        }
        // ==============================================================

        // Standard State Machine (Operates on unstuffed bytes)
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
                    state = State.READ_VERSION;
                } else if (b == MAGIC_1) {
                    // RF robustness: another MAGIC_1 received
                    state = State.WAIT_MAGIC2;
                } else {
                    state = State.WAIT_MAGIC1;
                }
                break;

            case READ_VERSION:
                headerBuf[2] = b;
                crcCalculated = CRC16CCITT.updateCRC(crcCalculated, b);

                if (b != LLP.PROTOCOL_VERSION) {
                    logger.warn("Different protocol version: received={}, expected={}",
                            b, LLP.PROTOCOL_VERSION);
                }

                state = State.READ_TYPE;
                break;

            case READ_TYPE:
                headerBuf[3] = b;
                crcCalculated = CRC16CCITT.updateCRC(crcCalculated, b);
                state = State.READ_ID_L;
                break;

            case READ_ID_L:
                headerBuf[4] = b;
                crcCalculated = CRC16CCITT.updateCRC(crcCalculated, b);
                state = State.READ_ID_H;
                break;

            case READ_ID_H:
                headerBuf[5] = b;
                crcCalculated = CRC16CCITT.updateCRC(crcCalculated, b);
                state = State.READ_LEN_L;
                break;

            case READ_LEN_L:
                headerBuf[6] = b;
                crcCalculated = CRC16CCITT.updateCRC(crcCalculated, b);
                state = State.READ_LEN_H;
                break;

            case READ_LEN_H:
                headerBuf[7] = b;
                crcCalculated = CRC16CCITT.updateCRC(crcCalculated, b);

                payloadLen = (headerBuf[6] & 0xFF) | ((headerBuf[7] & 0xFF) << 8);

                if (payloadLen > payload.length) {
                    logger.error("Payload length {} exceeds maximum {}", payloadLen, payload.length);
                    statistics.recordError();
                    reset();
                    notifyError(LLPErrorCode.PAYLOAD_LEN_INVALID);
                    return null;
                }

                payloadIdx = 0;

                if (payloadLen == 0) {
                    state = State.READ_CRC_L;
                } else {
                    state = State.READ_PAYLOAD;
                }
                break;

            case READ_PAYLOAD:
                payload[payloadIdx] = b;
                crcCalculated = CRC16CCITT.updateCRC(crcCalculated, b);
                payloadIdx++;

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
                    notifyError(LLPErrorCode.CHECKSUM_INVALID);
                    return null;
                }

                // Full frame
                LLPFrame frame = createFrame();
                statistics.recordSuccess();
                reset();
                notifySuccess(frame);
                frameQueue.offer(frame);
                return frame;
        }

        return null;
    }

    public List<LLPFrame> processBytes(byte[] data) {
        List<LLPFrame> frames = new ArrayList<>();
        for (byte b : data) {
            LLPFrame f = processByte(b);
            if (f != null) frames.add(f);
        }
        return frames;
    }

    private LLPFrame createFrame() {
        byte version = headerBuf[2];
        byte type = headerBuf[3];
        int id = (headerBuf[4] & 0xFF) | ((headerBuf[5] & 0xFF) << 8);
        byte[] payloadCopy = new byte[payloadLen];
        System.arraycopy(payload, 0, payloadCopy, 0, payloadLen);

        return new LLPFrame(type, id, version, payloadCopy, crcCalculated);
    }

    /**
     * Resets parser state to initial synchronization state.
     */
    private void reset() {
        state = State.WAIT_MAGIC1;
        payloadIdx = 0;
        crcCalculated = 0xFFFF;
        escapePending = false; // Reset escape flag
    }

    /**
     * Registers a frame listener.
     */
    public void addListener(LLPFrameListener listener) {
        listeners.offer(listener);
    }

    /**
     * Removes a frame listener.
     */
    public void removeListener(LLPFrameListener listener) {
        listeners.remove(listener);
    }

    // ============= LISTENER MANAGEMENT =============

    private void notifySuccess(LLPFrame frame) {
        for (LLPFrameListener listener : listeners) {
            try {
                listener.onFrameReceived(frame);
            } catch (Exception e) {
                logger.error("Listener error", e);
            }
        }
    }

    private void notifyError(LLPErrorCode errorCode) {
        for (LLPFrameListener listener : listeners) {
            try {
                listener.onFrameError(errorCode);
            } catch (Exception e) {
                logger.error("Listener error", e);
            }
        }
    }

    // ============= GETTERS =============

    /**
     * Returns parsed frames queue.
     */
    public Queue<LLPFrame> getFrameQueue() {
        return frameQueue;
    }

    /**
     * Returns parser statistics.
     */
    public Statistics getStatistics() {
        return statistics;
    }

    private enum State {
        WAIT_MAGIC1, WAIT_MAGIC2, READ_VERSION, READ_TYPE, READ_ID_L, READ_ID_H,
        READ_LEN_L, READ_LEN_H, READ_PAYLOAD, READ_CRC_L, READ_CRC_H
    }

    /**
     * Listener interface for receiving parser events.
     */
    public interface LLPFrameListener {

        /**
         * Called when a valid frame is received.
         */
        void onFrameReceived(LLPFrame frame);

        /**
         * Called when a frame error occurs.
         */
        void onFrameError(LLPErrorCode errorCode);
    }
}