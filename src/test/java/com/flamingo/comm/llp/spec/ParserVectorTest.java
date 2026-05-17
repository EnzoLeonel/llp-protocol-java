package com.flamingo.comm.llp.spec;

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

class ParserVectorTest {

    private static final HexFormat HEX = HexFormat.of();

    private record Event(String type, byte[] payload, TransportErrorCode errorCode) {}

    static List<TestVector> loadVectors() throws IOException {
        return VectorLoader.loadByNames("llp-vectors",
            "incremental.json", "fragmented.json", "recovery.json");
    }

    @ParameterizedTest
    @MethodSource("loadVectors")
    void testParser(TestVector vector) throws Exception {
        JsonNode chunks = vector.input().get("chunks_hex");
        JsonNode expectedEvents = vector.expected().get("events");

        boolean expectsTimeout = false;
        for (JsonNode ev : expectedEvents) {
            JsonNode ec = ev.get("error_code");
            if (ec != null && "TIMEOUT".equals(ec.asString())) {
                expectsTimeout = true;
                break;
            }
        }

        LLPTransportDeframer deframer = new LLPTransportDeframer();
        LLPFrameParser frameParser = LLP.frameParser()
            .parserProvider(id -> Optional.empty())
            .build();

        List<Event> actualEvents = new ArrayList<>();
        deframer.addListener(new LLPTransportDeframer.LLPFrameListener() {
            @Override
            public void onFrameReceived(LLPRawFrame rawFrame) {
                LLPFrame frame = frameParser.parse(rawFrame);
                actualEvents.add(new Event("FRAME", reconstructChain(frame), null));
            }

            @Override
            public void onFrameError(TransportErrorCode code) {
                actualEvents.add(new Event("ERROR", null, code));
            }
        });

        List<Integer> boundaries = new ArrayList<>();
        StringBuilder hexBuf = new StringBuilder();
        int cumBytes = 0;
        for (JsonNode chunk : chunks) {
            String hex = chunk.asString();
            hexBuf.append(hex);
            cumBytes += (hex.length() + 1) / 2;
            boundaries.add(cumBytes);
        }
        byte[] allData = parseHexLenient(hexBuf.toString());

        for (int i = 0; i < allData.length; i++) {
            if (expectsTimeout && boundaries.contains(i) && i > 0) {
                Thread.sleep(2100);
            }
            deframer.processByte(allData[i]);
        }

        assertEquals(expectedEvents.size(), actualEvents.size(),
            "[" + vector.name() + "] event count mismatch");

        for (int i = 0; i < expectedEvents.size(); i++) {
            JsonNode expected = expectedEvents.get(i);
            Event actual = actualEvents.get(i);
            String expectedType = expected.get("type").asString();

            assertEquals(expectedType, actual.type(),
                "[" + vector.name() + "] event " + i + " type mismatch");

            if ("FRAME".equals(expectedType)) {
                byte[] expectedPayload = parseHexLenient(
                    expected.get("payload_hex").asString());
                assertArrayEquals(expectedPayload, actual.payload(),
                    "[" + vector.name() + "] event " + i + " payload mismatch");
            } else if ("ERROR".equals(expectedType)) {
                String expectedError = expected.get("error_code").asString();
                assertEquals(expectedError, mapErrorCode(actual.errorCode()),
                    "[" + vector.name() + "] event " + i + " error mismatch");
            }
        }
    }

    private static byte[] parseHexLenient(String hex) {
        return HEX.parseHex(hex.length() % 2 == 0 ? hex : "0" + hex);
    }

    private static byte[] reconstructChain(LLPFrame frame) {
        FinalNode fn = (FinalNode) frame.chain().asList().getFirst();
        ByteBuffer payload = fn.getPayload();
        byte[] chain = new byte[1 + payload.remaining()];
        chain[0] = 0x00;
        payload.get(chain, 1, payload.remaining());
        return chain;
    }

    private static String mapErrorCode(TransportErrorCode code) {
        return switch (code) {
            case CHECKSUM_INVALID -> "CHECKSUM";
            case TIMEOUT -> "TIMEOUT";
            case SYNC_ERROR -> "SYNC_ERROR";
            case PAYLOAD_LEN_INVALID -> "PAYLOAD_LEN_INVALID";
            case BUFFER_FULL -> "BUFFER_FULL";
            default -> code.name();
        };
    }
}
