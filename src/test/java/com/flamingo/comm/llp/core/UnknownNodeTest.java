package com.flamingo.comm.llp.core;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class UnknownNodeTest {

    private byte[] extractData(UnknownNode node) {
        ByteBuffer buffer = node.getMetadata();
        byte[] extracted = new byte[buffer.remaining()];
        buffer.get(extracted);
        return extracted;
    }

    @Test
    void testConstructorAndGetId() {
        UnknownNode node = new UnknownNode(42, new byte[]{1, 2, 3});

        assertEquals(42, node.getId());
    }

    @Test
    void testMetadataContent() {
        byte[] metadata = {10, 20, 30};

        UnknownNode node = new UnknownNode(1, metadata);

        byte[] extracted = extractData(node);

        assertArrayEquals(metadata, extracted);
    }

    @Test
    void testMetadataIsDefensivelyCopiedInConstructor() {
        byte[] metadata = {1, 2, 3};

        UnknownNode node = new UnknownNode(1, metadata);

        // Modify original array
        metadata[0] = 99;

        byte[] extracted = extractData(node);

        assertEquals(1, extracted[0], "Internal state should not be affected by external changes");
    }

    @Test
    void testMetadataIsDefensivelyCopiedInGetter() {
        byte[] metadata = {1, 2, 3};

        UnknownNode node = new UnknownNode(1, metadata);

        byte[] extracted1 = extractData(node);
        byte[] extracted2 = extractData(node);

        // Modify returned array
        extracted1[0] = 99;

        // Ensure second call is unaffected
        assertEquals(1, extracted2[0], "Getter should return a defensive copy");
    }

    @Test
    void testNullMetadataBecomesEmptyArray() {
        UnknownNode node = new UnknownNode(1, null);

        byte[] metadata = extractData(node);

        assertNotNull(metadata);
        assertEquals(0, metadata.length);
    }

    @Test
    void testEmptyMetadata() {
        UnknownNode node = new UnknownNode(1, new byte[0]);

        byte[] metadata = extractData(node);

        assertNotNull(metadata);
        assertEquals(0, metadata.length);
    }

    @Test
    void testToStringContainsIdAndLength() {
        byte[] metadata = {1, 2, 3};

        UnknownNode node = new UnknownNode(99, metadata);

        String str = node.toString();

        assertTrue(str.contains("id=99"));
        assertTrue(str.contains("metadataLength=3"));
    }

    @Test
    void testLargeMetadata() {
        byte[] metadata = new byte[1024];
        for (int i = 0; i < metadata.length; i++) {
            metadata[i] = (byte) i;
        }

        UnknownNode node = new UnknownNode(5, metadata);

        byte[] extracted = extractData(node);

        assertArrayEquals(metadata, extracted);
    }

    @Test
    void testIdBoundaries() {
        UnknownNode min = new UnknownNode(0, new byte[0]);
        UnknownNode max = new UnknownNode(255, new byte[0]);

        assertEquals(0, min.getId());
        assertEquals(255, max.getId());
    }
}
