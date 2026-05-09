package com.flamingo.comm.llp.core;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Incremental LLP frame parser designed for streaming transports.
 *
 * <p>This parser allows LLP frames to be processed progressively as bytes
 * arrive from a transport such as TCP, serial ports, UART, Bluetooth,
 * RF modules, or any other byte-oriented communication channel.</p>
 *
 * <p>The parser internally performs:</p>
 * <ol>
 *     <li>Transport deframing using {@link LLPTransportDeframer}</li>
 *     <li>Layer parsing using {@link LLPFrameParser}</li>
 * </ol>
 *
 * <p>Parsed frames and transport errors are accumulated internally and can be
 * retrieved using the polling methods:</p>
 * <ul>
 *     <li>{@link #pollFrames()}</li>
 *     <li>{@link #pollErrors()}</li>
 * </ul>
 *
 * <p>This class follows a <b>pull-based model</b>:</p>
 * <ul>
 *     <li>Input bytes are pushed into the parser using {@code feed(...)} methods</li>
 *     <li>Completed frames and errors are later retrieved by polling</li>
 * </ul>
 *
 * <p>Instances of this class are not thread-safe.</p>
 */
public final class LLPIncrementalParser {

    private final LLPTransportDeframer deframer;
    private final LLPFrameParser parser;

    private final List<LLPFrame> completedFrames = new ArrayList<>();
    private final List<TransportErrorCode> errors = new ArrayList<>();

    /**
     * Creates a new incremental LLP parser.
     *
     * @param provider   provider used to resolve layer parsers
     * @param maxPayload maximum allowed transport payload size in bytes,
     *                   or a negative value to use the transport default
     * @param timeoutMs  timeout in milliseconds between received bytes before
     *                   the transport parser resets, or a negative value to disable timeout handling
     */
    LLPIncrementalParser(LayerParserProvider provider,
                         int maxPayload,
                         long timeoutMs) {

        this.deframer = new LLPTransportDeframer(maxPayload, timeoutMs);
        this.parser = new SimpleFrameParser(provider);

        this.deframer.addListener(new FrameListener());
    }

    // =====================
    // INPUT
    // =====================

    /**
     * Feeds transport bytes into the parser.
     *
     * <p>The provided byte array may contain:</p>
     * <ul>
     *     <li>A partial LLP frame</li>
     *     <li>A complete LLP frame</li>
     *     <li>Multiple concatenated LLP frames</li>
     * </ul>
     *
     * <p>Any completed frames can later be retrieved using
     * {@link #pollFrames()}.</p>
     *
     * @param data transport bytes to process
     */
    public void feed(byte[] data) {
        deframer.processBytes(data);
    }

    /**
     * Feeds bytes from the provided {@link ByteBuffer} into the parser.
     *
     * <p>Bytes are consumed from the buffer starting at its current position
     * until no remaining bytes are available.</p>
     *
     * @param buffer buffer containing transport bytes
     */
    public void feed(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            deframer.processByte(buffer.get());
        }
    }

    /**
     * Feeds a single transport byte into the parser.
     *
     * <p>This method is useful for highly incremental or interrupt-driven
     * transports where bytes arrive individually.</p>
     *
     * @param b transport byte to process
     */
    public void feed(byte b) {
        deframer.processByte(b);
    }

    // =====================
    // OUTPUT (pull model)
    // =====================

    /**
     * Returns all completed LLP frames accumulated since the previous poll.
     *
     * <p>After this method returns, the internal completed-frame queue
     * is cleared.</p>
     *
     * @return immutable list of completed parsed frames;
     * never {@code null}
     */
    public List<LLPFrame> pollFrames() {
        List<LLPFrame> out = List.copyOf(completedFrames);
        completedFrames.clear();
        return out;
    }

    /**
     * Returns all transport errors accumulated since the previous poll.
     *
     * <p>After this method returns, the internal error queue is cleared.</p>
     *
     * @return immutable list of transport error codes;
     * never {@code null}
     */
    public List<TransportErrorCode> pollErrors() {
        List<TransportErrorCode> out = List.copyOf(errors);
        errors.clear();
        return out;
    }

    // =====================
    // CALLBACKS
    // =====================

    /**
     * Internal listener used to receive callbacks from the transport deframer.
     */
    private class FrameListener implements LLPTransportDeframer.LLPFrameListener {

        /**
         * Called when a complete transport frame has been successfully received.
         *
         * @param rawFrame deframed transport frame
         */
        @Override
        public void onFrameReceived(LLPRawFrame rawFrame) {
            LLPFrame frame = parser.parse(rawFrame);
            completedFrames.add(frame);
        }

        /**
         * Called when a transport-level error occurs while processing bytes.
         *
         * @param code transport error code
         */
        @Override
        public void onFrameError(TransportErrorCode code) {
            errors.add(code);
        }
    }
}