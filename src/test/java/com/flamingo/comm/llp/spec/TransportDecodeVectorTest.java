package com.flamingo.comm.llp.spec;

import com.flamingo.comm.llp.core.FailureNode;
import com.flamingo.comm.llp.core.LLP;
import com.flamingo.comm.llp.core.LLPFrame;
import com.flamingo.comm.llp.core.LLPFrameParser;
import com.flamingo.comm.llp.core.LLPRawFrame;
import com.flamingo.comm.llp.core.LLPTransportDeframer;
import com.flamingo.comm.llp.core.TransportErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TransportDecodeVectorTest {

    private static final List<String> LAYER_ERROR_CODES = List.of(
            "LAYER_MALFORMED", "TRANSFORM_NO_HANDLER");

    static List<TestVector> loadVectors() throws IOException {
        return VectorLoader.loadByType("llp-vectors", "decode");
    }

    @ParameterizedTest
    @MethodSource("loadVectors")
    void testDecode(TestVector vector) {
        if (!"decode".equals(vector.type())) return;

        String frameHex = vector.input().has("frame_hex")
            ? vector.input().get("frame_hex").asString()
            : "";
        byte[] frame = frameHex.isEmpty() ? new byte[0]
            : HexFormat.of().parseHex(frameHex);

        String outcome = vector.expected().has("outcome")
            ? vector.expected().get("outcome").asString()
            : "";

        if (isLayerError(vector)) {
            testLayerError(vector, frame, outcome);
            return;
        }

        LLPTransportDeframer deframer = new LLPTransportDeframer();
        List<TransportErrorCode> errors = new ArrayList<>();
        deframer.addListener(new LLPTransportDeframer.LLPFrameListener() {
            @Override
            public void onFrameReceived(LLPRawFrame f) {}

            @Override
            public void onFrameError(TransportErrorCode code) {
                errors.add(code);
            }
        });

        List<LLPRawFrame> frames = deframer.processBytes(frame);

        switch (outcome) {
            case "FRAME" -> {
                assertFalse(frames.isEmpty(), "[" + vector.name() + "] expected at least 1 frame");
                byte[] expectedPayload = HexFormat.of().parseHex(
                    vector.expected().get("payload_hex").asString());
                ByteBuffer buf = frames.getFirst().payload();
                byte[] extracted = new byte[buf.remaining()];
                buf.get(extracted);
                assertArrayEquals(expectedPayload, extracted,
                    "[" + vector.name() + "] payload mismatch");
                assertTrue(errors.isEmpty(),
                    "[" + vector.name() + "] expected no errors");
            }
            case "ERROR" -> {
                String errorCodeStr = vector.expected().get("error_code").asString();
                if ("TIMEOUT".equals(errorCodeStr)) {
                    assertTrue(frames.isEmpty(),
                        "[" + vector.name() + "] expected no frames (truncated)");
                } else {
                    assertTrue(frames.isEmpty(),
                        "[" + vector.name() + "] expected no frames");
                    assertEquals(1, errors.size(),
                        "[" + vector.name() + "] expected 1 error");
                    TransportErrorCode expectedError = mapErrorCode(errorCodeStr);
                    assertEquals(expectedError, errors.getFirst(),
                        "[" + vector.name() + "] error code mismatch");
                }
            }
            case "NONE" -> {
                assertTrue(frames.isEmpty(),
                    "[" + vector.name() + "] expected no frames");
                assertTrue(errors.isEmpty(),
                    "[" + vector.name() + "] expected no errors");
            }
            default -> throw new IllegalArgumentException(
                "Unknown outcome: " + outcome);
        }
    }

    private void testLayerError(TestVector vector, byte[] frame, String outcome) {
        LLPTransportDeframer deframer = new LLPTransportDeframer();
        LLPFrameParser frameParser = LLP.frameParser()
            .parserProvider(id -> Optional.empty())
            .build();
        List<LLPFrame> parsedFrames = new ArrayList<>();
        List<TransportErrorCode> transportErrors = new ArrayList<>();

        deframer.addListener(new LLPTransportDeframer.LLPFrameListener() {
            @Override
            public void onFrameReceived(LLPRawFrame rawFrame) {
                parsedFrames.add(frameParser.parse(rawFrame));
            }

            @Override
            public void onFrameError(TransportErrorCode code) {
                transportErrors.add(code);
            }
        });

        deframer.processBytes(frame);

        String errorCodeStr = vector.expected().get("error_code").asString();

        switch (errorCodeStr) {
            case "LAYER_MALFORMED" -> {
                assertFalse(parsedFrames.isEmpty(), "[" + vector.name() + "] expected at least 1 parsed frame");
                LLPFrame parsed = parsedFrames.getFirst();
                boolean hasFailure = parsed.chain().asList().stream()
                    .anyMatch(node -> node instanceof FailureNode);
                assertTrue(hasFailure,
                    "[" + vector.name() + "] expected FailureNode in parsed frame (LAYER_MALFORMED)");
                assertTrue(transportErrors.isEmpty(),
                    "[" + vector.name() + "] expected no transport errors");
            }
            case "TRANSFORM_NO_HANDLER" -> {
                assertFalse(parsedFrames.isEmpty(), "[" + vector.name() + "] expected at least 1 parsed frame");
                LLPFrame parsed = parsedFrames.getFirst();
                boolean hasFailure = parsed.chain().asList().stream()
                    .anyMatch(node -> node instanceof FailureNode);
                assertTrue(hasFailure,
                    "[" + vector.name() + "] expected FailureNode in parsed frame (TRANSFORM_NO_HANDLER)");
                assertTrue(transportErrors.isEmpty(),
                    "[" + vector.name() + "] expected no transport errors");
            }
            default -> throw new IllegalArgumentException(
                "Unhandled layer error code: " + errorCodeStr);
        }
    }

    private static boolean isLayerError(TestVector vector) {
        if (vector.expected() == null || !vector.expected().has("error_code")) {
            return false;
        }
        String ec = vector.expected().get("error_code").asString();
        return LAYER_ERROR_CODES.contains(ec);
    }

    private static TransportErrorCode mapErrorCode(String specCode) {
        return switch (specCode) {
            case "CHECKSUM" -> TransportErrorCode.CHECKSUM_INVALID;
            case "TIMEOUT" -> TransportErrorCode.TIMEOUT;
            case "SYNC_ERROR" -> TransportErrorCode.SYNC_ERROR;
            case "PAYLOAD_LEN_INVALID" -> TransportErrorCode.PAYLOAD_LEN_INVALID;
            case "BUFFER_FULL" -> TransportErrorCode.BUFFER_FULL;
            case "LAYER_MALFORMED" -> TransportErrorCode.LAYER_MALFORMED;
            case "TRANSFORM_NO_HANDLER" -> TransportErrorCode.TRANSFORM_NO_HANDLER;
            default -> throw new IllegalArgumentException(
                "Unknown spec error code: " + specCode);
        };
    }
}