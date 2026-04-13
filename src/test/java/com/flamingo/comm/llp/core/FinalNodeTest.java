package com.flamingo.comm.llp.core;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

import static org.junit.jupiter.api.Assertions.*;

class FinalNodeTest {

    @Test
    void testEmptySingleton() {
        FinalNode node1 = FinalNode.of(null);
        FinalNode node2 = FinalNode.of(new byte[0]);

        assertSame(FinalNode.EMPTY, node1);
        assertSame(FinalNode.EMPTY, node2);
    }

    @Test
    void testNonEmptyCreatesNewInstance() {
        FinalNode node = FinalNode.of(new byte[]{1, 2, 3});

        assertNotSame(FinalNode.EMPTY, node);
    }

    @Test
    void testPayloadContent() {
        byte[] payload = {1, 2, 3};

        FinalNode node = FinalNode.of(payload);

        ByteBuffer buf = node.getPayload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertArrayEquals(payload, extracted);
    }

    @Test
    void testPayloadIsReadOnly() {
        FinalNode node = FinalNode.of(new byte[]{1, 2, 3});

        ByteBuffer buf = node.getPayload();

        assertTrue(buf.isReadOnly());
        assertThrows(ReadOnlyBufferException.class, () -> buf.put((byte) 0xFF));
    }

    @Test
    void testPayloadReturnsNewBufferInstance() {
        FinalNode node = FinalNode.of(new byte[]{1, 2, 3});

        ByteBuffer b1 = node.getPayload();
        ByteBuffer b2 = node.getPayload();

        assertNotSame(b1, b2);
    }

    @Test
    void testOriginalArrayModificationDoesNotAffectNode() {
        byte[] payload = {1, 2, 3};

        FinalNode node = FinalNode.of(payload);

        // Modify original array
        payload[0] = 99;

        ByteBuffer buf = node.getPayload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertNotEquals(99, extracted[0]);
    }

    @Test
    void testIdIsZero() {
        FinalNode node = FinalNode.of(new byte[]{1});

        assertEquals(0, node.getId());
    }

    @Test
    void testToStringContainsHexPayload() {
        byte[] payload = {0x0A, 0x0B};

        FinalNode node = FinalNode.of(payload);

        String str = node.toString();

        assertTrue(str.contains("0A0B"));
    }
}
