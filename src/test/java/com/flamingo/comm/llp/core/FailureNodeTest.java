package com.flamingo.comm.llp.core;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FailureNodeTest {

    private byte[] extractMetadata(FailureNode node) {
        ByteBuffer buffer = node.getMetadata();
        byte[] extracted = new byte[buffer.remaining()];
        buffer.get(extracted);
        return extracted;
    }

    @Test
    void testConstructorAndGetId() {
        FailureNode node = new FailureNode(42, new byte[]{1, 2}, CoreParseErrorReason.MALFORMED_METADATA_LENGTH);

        assertEquals(42, node.getId());
    }

    @Test
    void testMetadataContent() {
        byte[] metadata = {10, 20, 30};

        FailureNode node = new FailureNode(1, metadata, CoreParseErrorReason.PAYLOAD_TOO_SHORT);

        byte[] extracted = extractMetadata(node);

        assertArrayEquals(metadata, extracted);
    }

    @Test
    void testMetadataIsDefensivelyCopiedInConstructor() {
        byte[] metadata = {1, 2, 3};

        FailureNode node = new FailureNode(1, metadata, CoreParseErrorReason.PAYLOAD_TOO_SHORT);

        metadata[0] = 99;

        byte[] extracted = extractMetadata(node);

        assertEquals(1, extracted[0], "Internal state should not be affected by external changes");
    }

    @Test
    void testMetadataIsDefensivelyCopiedInGetter() {
        byte[] metadata = {1, 2, 3};

        FailureNode node = new FailureNode(1, metadata, CoreParseErrorReason.PAYLOAD_TOO_SHORT);

        byte[] extracted1 = extractMetadata(node);
        byte[] extracted2 = extractMetadata(node);

        extracted1[0] = 99;

        assertEquals(1, extracted2[0], "Getter should return a defensive copy");
    }

    @Test
    void testNullMetadataBecomesEmptyArray() {
        FailureNode node = new FailureNode(1, null, CoreParseErrorReason.PAYLOAD_TOO_SHORT);

        byte[] metadata = extractMetadata(node);

        assertNotNull(metadata);
        assertEquals(0, metadata.length);
    }

    @Test
    void testEmptyMetadata() {
        FailureNode node = new FailureNode(1, new byte[0], CoreParseErrorReason.PAYLOAD_TOO_SHORT);

        byte[] metadata = extractMetadata(node);

        assertNotNull(metadata);
        assertEquals(0, metadata.length);
    }

    @Test
    void testErrorReasonIsStoredCorrectly() {
        FailureNode node = new FailureNode(1, null, CoreParseErrorReason.UNKNOWN_CRITICAL_LAYER);

        assertEquals(CoreParseErrorReason.UNKNOWN_CRITICAL_LAYER, node.getErrorReason());
    }

    @Test
    void testErrorReasonCannotBeNull() {
        assertThrows(NullPointerException.class, () ->
                new FailureNode(1, null, null)
        );
    }

    @Test
    void testCauseIsStored() {
        RuntimeException ex = new RuntimeException("boom");

        FailureNode node = new FailureNode(1, null, CoreParseErrorReason.PAYLOAD_TOO_SHORT, ex);

        assertSame(ex, node.getCause().orElseGet(() -> new IllegalStateException("The cause of the failure has not been saved")));
    }

    @Test
    void testCauseCanBeNull() {
        FailureNode node = new FailureNode(1, null, CoreParseErrorReason.PAYLOAD_TOO_SHORT, null);

        assertEquals(Optional.empty(), node.getCause());
    }

    @Test
    void testToStringContainsBasicInfo() {
        FailureNode node = new FailureNode(99, new byte[]{1, 2, 3}, CoreParseErrorReason.PAYLOAD_TOO_SHORT);

        String str = node.toString();

        assertTrue(str.contains("id=99"));
        assertTrue(str.contains("metadataLength=3"));
        assertTrue(str.contains("PAYLOAD_TOO_SHORT"));
    }

    @Test
    void testToStringIncludesCauseWhenPresent() {
        IllegalStateException ex = new IllegalStateException();

        FailureNode node = new FailureNode(1, null, CoreParseErrorReason.PAYLOAD_TOO_SHORT, ex);

        String str = node.toString();

        assertTrue(str.contains("IllegalStateException"));
    }

    @Test
    void testToStringWithoutCauseDoesNotFail() {
        FailureNode node = new FailureNode(1, null, CoreParseErrorReason.PAYLOAD_TOO_SHORT);

        String str = node.toString();

        assertNotNull(str);
    }

    @Test
    void testLargeMetadata() {
        byte[] metadata = new byte[1024];
        for (int i = 0; i < metadata.length; i++) {
            metadata[i] = (byte) i;
        }

        FailureNode node = new FailureNode(5, metadata, CoreParseErrorReason.PAYLOAD_TOO_SHORT);

        byte[] extracted = extractMetadata(node);

        assertArrayEquals(metadata, extracted);
    }

    @Test
    void testIdBoundaries() {
        FailureNode min = new FailureNode(0, new byte[0], CoreParseErrorReason.PAYLOAD_TOO_SHORT);
        FailureNode max = new FailureNode(255, new byte[0], CoreParseErrorReason.PAYLOAD_TOO_SHORT);

        assertEquals(0, min.getId());
        assertEquals(255, max.getId());
    }

    @Test
    void testGetMetadataReturnsReadOnlyBuffer() {
        FailureNode node = new FailureNode(1, new byte[]{1, 2, 3}, CoreParseErrorReason.PAYLOAD_TOO_SHORT);

        ByteBuffer buffer = node.getMetadata();

        assertTrue(buffer.isReadOnly());
        assertThrows(Exception.class, () -> buffer.put((byte) 1));
    }

    @Test
    void testGetMetadataReturnsDifferentBufferInstances() {
        FailureNode node = new FailureNode(1, new byte[]{1, 2, 3}, CoreParseErrorReason.PAYLOAD_TOO_SHORT);

        ByteBuffer b1 = node.getMetadata();
        ByteBuffer b2 = node.getMetadata();

        assertNotSame(b1, b2);
    }
}
