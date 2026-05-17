package com.flamingo.comm.llp.spec;

import com.flamingo.comm.llp.core.LLPTransportFramer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TransportEncodeVectorTest {

    static List<TestVector> loadVectors() throws IOException {
        return VectorLoader.loadByType("llp-vectors", "encode");
    }

    @ParameterizedTest
    @MethodSource("loadVectors")
    void testEncode(TestVector vector) {
        byte[] llpPayload = HexFormat.of().parseHex(
            vector.input().get("llp_payload_hex").asString());
        byte[] expectedFrame = HexFormat.of().parseHex(
            vector.expected().get("frame_hex").asString());

        byte[] actualFrame = LLPTransportFramer.buildSafe(llpPayload);

        assertArrayEquals(expectedFrame, actualFrame,
            "[" + vector.name() + "] frame mismatch");
    }
}
