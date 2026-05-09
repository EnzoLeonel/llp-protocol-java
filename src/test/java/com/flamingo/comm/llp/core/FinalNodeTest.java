package com.flamingo.comm.llp.core;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

import static org.junit.jupiter.api.Assertions.*;

class FinalNodeTest {

    @Test
    void testEmptySingleton() {
        FinalNode node1 = FinalNode.of((byte[]) null);
        FinalNode node2 = FinalNode.of(new byte[0]);
        FinalNode node3 = FinalNode.of((ByteBuffer) null);

        assertSame(FinalNode.EMPTY, node1);
        assertSame(FinalNode.EMPTY, node2);
        assertSame(FinalNode.EMPTY, node3);
    }

    @Test
    void testNonEmptyCreatesNewInstance() {
        FinalNode node = FinalNode.of(new byte[]{1, 2, 3});
        FinalNode node2 = FinalNode.of(ByteBuffer.wrap(new byte[]{4, 5, 6}));

        assertNotSame(FinalNode.EMPTY, node);
        assertNotSame(FinalNode.EMPTY, node2);
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
        FinalNode node = FinalNode.of(ByteBuffer.wrap(new byte[]{1, 2, 3}));

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

    @Test
    void testByteBufferModificationDoesNotAffectNode() {
        ByteBuffer original = ByteBuffer.wrap(new byte[]{1, 2, 3});

        FinalNode node = FinalNode.of(original);

        // Modify original buffer content
        original.put(0, (byte) 99);

        ByteBuffer buf = node.getPayload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertEquals(1, extracted[0], "Node payload must be independent from original buffer");
    }

    @Test
    void testByteBufferPositionLimitIndependence() {
        ByteBuffer original = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5});
        original.position(2); // remaining = {3,4,5}

        FinalNode node = FinalNode.of(original);

        // Change original state after creation
        original.position(0);
        original.limit(1);

        ByteBuffer buf = node.getPayload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertArrayEquals(new byte[]{3, 4, 5}, extracted,
                "Node must copy only remaining bytes at creation time");
    }

    @Test
    void testReadOnlySourceBufferDoesNotAffectNode() {
        ByteBuffer original = ByteBuffer.wrap(new byte[]{1, 2, 3}).asReadOnlyBuffer();

        FinalNode node = FinalNode.of(original);

        ByteBuffer buf = node.getPayload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertArrayEquals(new byte[]{1, 2, 3}, extracted);
    }

    @Test
    void testOriginalBufferConsumedAfterCreationDoesNotAffectNode() {
        ByteBuffer original = ByteBuffer.wrap(new byte[]{1, 2, 3});

        FinalNode node = FinalNode.of(original);

        // Consume original buffer completely
        while (original.hasRemaining()) {
            original.get();
        }

        ByteBuffer buf = node.getPayload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertArrayEquals(new byte[]{1, 2, 3}, extracted);
    }

    @Test
    void testSharedBackingArrayDoesNotAffectNode() {
        byte[] array = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.wrap(array);

        FinalNode node = FinalNode.of(buffer);

        // Modify underlying array directly
        array[1] = 99;

        ByteBuffer buf = node.getPayload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertEquals(2, extracted[1], "Node must not share backing array");
    }

    @Test
    void testSliceBufferIndependence() {
        ByteBuffer original = ByteBuffer.wrap(new byte[]{1, 2, 3, 4});
        original.position(1); // {2,3,4}
        ByteBuffer slice = original.slice();

        FinalNode node = FinalNode.of(slice);

        // Modify original array
        original.put(1, (byte) 99);

        ByteBuffer buf = node.getPayload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertArrayEquals(new byte[]{2, 3, 4}, extracted);
    }

    @Test
    void testDuplicateBufferIndependence() {
        ByteBuffer original = ByteBuffer.wrap(new byte[]{1, 2, 3});
        ByteBuffer duplicate = original.duplicate();

        FinalNode node = FinalNode.of(duplicate);

        original.put(0, (byte) 99);

        ByteBuffer buf = node.getPayload();
        byte[] extracted = new byte[buf.remaining()];
        buf.get(extracted);

        assertEquals(1, extracted[0]);
    }
}
