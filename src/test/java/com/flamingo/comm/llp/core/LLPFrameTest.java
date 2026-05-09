package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LLPFrameTest {

    // ----------- Helper Node -----------

    static class TestNode implements LLPNode {
        private final int id;

        TestNode(int id) {
            this.id = id;
        }

        @Override
        public int getId() {
            return id;
        }
    }

    private NodeChain createChain(int... ids) {
        NodeChain.Builder builder = new NodeChain.Builder();
        for (int id : ids) {
            builder.add(new TestNode(id));
        }
        return builder.build();
    }

    // ----------- Basic behavior -----------

    @Test
    void testConstructorAndGetters() {
        NodeChain chain = createChain(1, 2, 3);

        LLPFrame frame = new LLPFrame(chain, 1234, 999L);

        assertEquals(1234, frame.crc());
        assertEquals(999L, frame.timestamp());
        assertSame(chain, frame.chain());
    }

    @Test
    void testConstructorWithCurrentTimestamp() {
        NodeChain chain = createChain(1);

        long before = System.currentTimeMillis();
        LLPFrame frame = new LLPFrame(chain, 55);
        long after = System.currentTimeMillis();

        assertTrue(frame.timestamp() >= before);
        assertTrue(frame.timestamp() <= after);
    }

    // ----------- toString -----------

    @Test
    void testToStringContainsImportantData() {
        NodeChain chain = createChain(1, 2);

        LLPFrame frame = new LLPFrame(chain, 999, 123L);

        String str = frame.toString();

        assertTrue(str.contains("crc=999"));
        assertTrue(str.contains("timestamp=123"));
        assertTrue(str.contains("nodes=2"));
    }

    // ----------- equals / hashCode -----------

    @Test
    void testEqualsSameInstance() {
        NodeChain chain = createChain(1);

        LLPFrame frame = new LLPFrame(chain, 10);
        LLPFrame frame2 = new LLPFrame(chain, 10);

        assertEquals(frame, frame2);
    }

    @Test
    void testEqualsDifferentObjectsSameContent() {
        NodeChain chain1 = new NodeChain.Builder()
                .add(new SpecialNode(1))
                .add(new SpecialNode(2))
                .build();

        NodeChain chain2 = new NodeChain.Builder()
                .add(new SpecialNode(1))
                .add(new SpecialNode(2))
                .build();

        LLPFrame f1 = new LLPFrame(chain1, 100, 1L);
        LLPFrame f2 = new LLPFrame(chain2, 100, 999L); // different timestamp

        assertEquals(f1, f2, "Timestamp should be ignored in equals");
        assertEquals(f1.hashCode(), f2.hashCode());
    }

    @Test
    void testNotEqualsDifferentCRC() {
        NodeChain chain = createChain(1, 2);

        LLPFrame f1 = new LLPFrame(chain, 100);
        LLPFrame f2 = new LLPFrame(chain, 200);

        assertNotEquals(f1, f2);
    }

    @Test
    void testNotEqualsDifferentNodeChain() {
        NodeChain chain1 = createChain(1, 2);
        NodeChain chain2 = createChain(1, 3);

        LLPFrame f1 = new LLPFrame(chain1, 100);
        LLPFrame f2 = new LLPFrame(chain2, 100);

        assertNotEquals(f1, f2);
    }

    @Test
    void testNotEqualsNull() {
        NodeChain chain = createChain(1);

        LLPFrame frame = new LLPFrame(chain, 10);

        assertNotEquals(frame, null);
    }

    @Test
    void testNotEqualsDifferentType() {
        NodeChain chain = createChain(1);

        LLPFrame frame = new LLPFrame(chain, 10);

        assertNotEquals(frame, "not a frame");
    }

    // ----------- HashCode consistency -----------

    @Test
    void testHashCodeConsistency() {
        NodeChain chain = createChain(1, 2);

        LLPFrame frame = new LLPFrame(chain, 123);

        int h1 = frame.hashCode();
        int h2 = frame.hashCode();

        assertEquals(h1, h2);
    }

    // ----------- Chain reference behavior -----------

    @Test
    void testChainReferenceIsSameInstance() {
        NodeChain chain = createChain(1, 2);

        LLPFrame frame = new LLPFrame(chain, 1);

        assertSame(chain, frame.chain(), "Frame should keep reference to NodeChain");
    }

    // ----------- Edge cases -----------

    @Test
    void testEmptyChain() {
        NodeChain chain = new NodeChain.Builder().build();

        LLPFrame frame = new LLPFrame(chain, 0);

        assertEquals(0, frame.chain().size());
    }

    @Test
    void testLargeChain() {
        NodeChain.Builder builder = new NodeChain.Builder();

        for (int i = 0; i < 1000; i++) {
            builder.add(new TestNode(i));
        }

        LLPFrame frame = new LLPFrame(builder.build(), 123);

        assertEquals(1000, frame.chain().size());
    }
}