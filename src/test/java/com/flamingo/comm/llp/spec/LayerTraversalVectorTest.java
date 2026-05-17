package com.flamingo.comm.llp.spec;

import com.flamingo.comm.llp.core.FailureNode;
import com.flamingo.comm.llp.core.FinalNode;
import com.flamingo.comm.llp.core.LLP;
import com.flamingo.comm.llp.core.LLPFrame;
import com.flamingo.comm.llp.core.LLPFrameParser;
import com.flamingo.comm.llp.core.LLPRawFrame;
import com.flamingo.comm.llp.core.LLPTransportDeframer;
import com.flamingo.comm.llp.core.TransportErrorCode;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LayerTraversalVectorTest {

    private static final HexFormat HEX = HexFormat.of();

    static List<TestVector> loadVectors() throws IOException {
        return VectorLoader.loadByNames("llp-vectors", "traversal.json");
    }

    @ParameterizedTest
    @MethodSource("loadVectors")
    void testTraversal(TestVector vector) throws Exception {
        String frameHex = vector.input().get("frame_hex").asString();
        byte[] frame = HEX.parseHex(frameHex);

        JsonNode expected = vector.expected();
        String outcome = expected.get("outcome").asString();
        String expectedFinalHex = expected.has("final_payload_hex")
            ? expected.get("final_payload_hex").asString()
            : "";

        LLPTransportDeframer deframer = new LLPTransportDeframer();
        LLPFrameParser frameParser = LLP.frameParser()
            .parserProvider(id -> Optional.empty())
            .build();

        List<LLPRawFrame> rawFrames = new ArrayList<>();
        List<TransportErrorCode> transportErrors = new ArrayList<>();
        deframer.addListener(new LLPTransportDeframer.LLPFrameListener() {
            @Override
            public void onFrameReceived(LLPRawFrame rawFrame) {
                rawFrames.add(rawFrame);
            }

            @Override
            public void onFrameError(TransportErrorCode code) {
                transportErrors.add(code);
            }
        });

        deframer.processBytes(frame);

        if ("FRAME".equals(outcome)) {
            assertFalse(rawFrames.isEmpty(), "[" + vector.name() + "] expected at least 1 raw frame");
            assertTrue(transportErrors.isEmpty(),
                "[" + vector.name() + "] expected no transport errors");

            LLPFrame parsed = frameParser.parse(rawFrames.getFirst());
            byte[] finalPayload = extractFinalPayload(parsed);
            byte[] expectedFinal = expectedFinalHex.isEmpty()
                ? new byte[0]
                : HEX.parseHex(expectedFinalHex);
            assertArrayEquals(expectedFinal, finalPayload,
                "[" + vector.name() + "] final payload mismatch");
        } else if ("ERROR".equals(outcome)) {
            String expectedErrorCode = expected.get("error_code").asString();

            if ("TRANSFORM_NO_HANDLER".equals(expectedErrorCode)) {
                assertFalse(rawFrames.isEmpty(), "[" + vector.name() + "] expected at least 1 raw frame");
                assertTrue(transportErrors.isEmpty(),
                    "[" + vector.name() + "] expected no transport errors");

                LLPFrame parsed = frameParser.parse(rawFrames.getFirst());
                boolean hasFailureNode = parsed.chain().asList().stream()
                    .anyMatch(node -> node instanceof FailureNode);
                assertTrue(hasFailureNode,
                    "[" + vector.name() + "] expected FailureNode for TRANSFORM_NO_HANDLER");
            } else {
                assertFalse(transportErrors.isEmpty(), "[" + vector.name() + "] expected at least 1 transport error");
                TransportErrorCode expectedError = mapErrorCode(expectedErrorCode);
                assertEquals(expectedError, transportErrors.getFirst(),
                    "[" + vector.name() + "] error code mismatch");
            }
        }
    }

    private static byte[] extractFinalPayload(LLPFrame frame) {
        List<?> nodes = frame.chain().asList();
        FinalNode fn = null;
        for (Object node : nodes) {
            if (node instanceof FinalNode) {
                fn = (FinalNode) node;
                break;
            }
        }
        if (fn == null) {
            return new byte[0];
        }
        ByteBuffer buf = fn.getPayload();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
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