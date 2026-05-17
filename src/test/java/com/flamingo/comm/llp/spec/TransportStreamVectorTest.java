package com.flamingo.comm.llp.spec;

import com.flamingo.comm.llp.core.LLPRawFrame;
import com.flamingo.comm.llp.core.LLPTransportDeframer;
import com.flamingo.comm.llp.core.TransportErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TransportStreamVectorTest {

    private record Event(String type, byte[] payload, TransportErrorCode errorCode) {}

    private static final HexFormat HEX = HexFormat.of();

    static List<TestVector> loadVectors() throws IOException {
        return VectorLoader.loadByType("llp-vectors", "stream");
    }

    @ParameterizedTest
    @MethodSource("loadVectors")
    void testStream(TestVector vector) {
        JsonNode chunks = vector.input().get("chunks_hex");
        List<byte[]> chunkList = new ArrayList<>();
        for (JsonNode chunk : chunks) {
            chunkList.add(HEX.parseHex(chunk.asString()));
        }

        JsonNode expectedEvents = vector.expected().get("events");

        LLPTransportDeframer deframer = new LLPTransportDeframer();
        List<Event> actualEvents = new ArrayList<>();
        deframer.addListener(new LLPTransportDeframer.LLPFrameListener() {
            @Override
            public void onFrameReceived(LLPRawFrame frame) {
                ByteBuffer buf = frame.payload();
                byte[] payload = new byte[buf.remaining()];
                buf.get(payload);
                actualEvents.add(new Event("FRAME", payload, null));
            }

            @Override
            public void onFrameError(TransportErrorCode code) {
                actualEvents.add(new Event("ERROR", null, code));
            }
        });

        for (byte[] chunk : chunkList) {
            deframer.processBytes(chunk);
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
                byte[] expectedPayload = HEX.parseHex(
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
