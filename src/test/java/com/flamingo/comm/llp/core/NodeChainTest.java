package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPNode;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class NodeChainTest {

    @Test
    void testBuildAndSize() {
        NodeChain chain = new NodeChain.Builder()
                .add(new TestNode(1))
                .add(new TestNode(2))
                .build();

        assertEquals(2, chain.size());
    }

    @Test
    void testAsListIsImmutable() {
        NodeChain chain = new NodeChain.Builder()
                .add(new TestNode(1))
                .build();

        List<LLPNode> list = chain.asList();

        assertThrows(UnsupportedOperationException.class, () ->
                list.add(new TestNode(2))
        );
    }

    @Test
    void testOrderIsPreserved() {
        TestNode n1 = new TestNode(1);
        TestNode n2 = new TestNode(2);

        NodeChain chain = new NodeChain.Builder()
                .add(n1)
                .add(n2)
                .build();

        List<LLPNode> list = chain.asList();

        assertSame(n1, list.get(0));
        assertSame(n2, list.get(1));
    }

    @Test
    void testGetNodeById() {
        TestNode n1 = new TestNode(1);
        TestNode n2 = new TestNode(2);

        NodeChain chain = new NodeChain.Builder()
                .add(n1)
                .add(n2)
                .build();

        Optional<LLPNode> result = chain.getNode(2);

        assertTrue(result.isPresent());
        assertSame(n2, result.get());
    }

    @Test
    void testGetNodeByIdNotFound() {
        NodeChain chain = new NodeChain.Builder()
                .add(new TestNode(1))
                .build();

        assertTrue(chain.getNode(999).isEmpty());
    }

    @Test
    void testGetNodeByType() {
        TestNode normal = new TestNode(1);
        SpecialNode special = new SpecialNode(2);

        NodeChain chain = new NodeChain.Builder()
                .add(normal)
                .add(special)
                .build();

        Optional<SpecialNode> result = chain.getNode(SpecialNode.class);

        assertTrue(result.isPresent());
        assertSame(special, result.get());
    }

    @Test
    void testGetNodeByTypeNotFound() {
        NodeChain chain = new NodeChain.Builder()
                .add(new TestNode(1))
                .build();

        assertTrue(chain.getNode(SpecialNode.class).isEmpty());
    }

    @Test
    void testGetDeepestNode() {
        TestNode n1 = new TestNode(1);
        TestNode n2 = new TestNode(2);

        NodeChain chain = new NodeChain.Builder()
                .add(n1)
                .add(n2)
                .build();

        assertSame(n2, chain.getDeepestNode());
    }

    @Test
    void testGetDeepestNodeEmptyThrows() {
        NodeChain chain = new NodeChain.Builder().build();

        assertThrows(NoSuchElementException.class, chain::getDeepestNode);
    }

    @Test
    void testIterator() {
        TestNode n1 = new TestNode(1);
        TestNode n2 = new TestNode(2);

        NodeChain chain = new NodeChain.Builder()
                .add(n1)
                .add(n2)
                .build();

        Iterator<LLPNode> it = chain.iterator();

        assertTrue(it.hasNext());
        assertSame(n1, it.next());
        assertSame(n2, it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void testVisitCallsAllNodes() {
        List<LLPNode> visited = new ArrayList<>();

        NodeChain chain = new NodeChain.Builder()
                .add(new TestNode(1))
                .add(new TestNode(2))
                .build();

        chain.visit(visitor ->
                visitor.on(TestNode.class, visited::add)
        );

        assertEquals(2, visited.size());
    }

    @Test
    void testVisitOnlyMatchingType() {
        List<LLPNode> visited = new ArrayList<>();

        NodeChain chain = new NodeChain.Builder()
                .add(new TestNode(1))
                .add(new SpecialNode(2))
                .build();

        chain.visit(visitor ->
                visitor.on(SpecialNode.class, visited::add)
        );

        assertEquals(1, visited.size());
        assertInstanceOf(SpecialNode.class, visited.getFirst());
    }

    @Test
    void testVisitDoesNotMatchSuperclass() {
        List<LLPNode> visited = new ArrayList<>();

        NodeChain chain = new NodeChain.Builder()
                .add(new SpecialNode(1))
                .build();

        chain.visit(visitor ->
                visitor.on(TestNode.class, visited::add)
        );

        assertTrue(visited.isEmpty());
    }

    @Test
    void testVisitMultipleHandlers() {
        List<LLPNode> visited = new ArrayList<>();

        NodeChain chain = new NodeChain.Builder()
                .add(new TestNode(1))
                .add(new SpecialNode(2))
                .build();

        chain.visit(visitor -> visitor
                .on(TestNode.class, visited::add)
                .on(SpecialNode.class, visited::add)
        );

        assertEquals(2, visited.size());
    }

    @Test
    void testVisitOrderIsPreserved() {
        List<Integer> order = new ArrayList<>();

        NodeChain chain = new NodeChain.Builder()
                .add(new TestNode(1))
                .add(new TestNode(2))
                .build();

        chain.visit(visitor ->
                visitor.on(TestNode.class, node -> order.add(node.getId()))
        );

        assertEquals(List.of(1, 2), order);
    }

    @Test
    void testVisitPipelineStyle() {
        StringBuilder result = new StringBuilder();

        NodeChain chain = new NodeChain.Builder()
                .add(new TestNode(1))
                .add(new SpecialNode(2))
                .build();

        chain.visit(visitor -> visitor
                .on(TestNode.class, n -> result.append("T"))
                .on(SpecialNode.class, n -> result.append("S"))
        );

        assertEquals("TS", result.toString());
    }

    @Test
    void testBuilderFluentApi() {
        NodeChain.Builder builder = new NodeChain.Builder();

        NodeChain chain = builder
                .add(new TestNode(1))
                .add(new TestNode(2))
                .build();

        assertEquals(2, chain.size());
    }

    @Test
    void testBuilderDoesNotAffectBuiltChain() {
        NodeChain.Builder builder = new NodeChain.Builder();

        builder.add(new TestNode(1));
        NodeChain chain = builder.build();

        builder.add(new TestNode(2));

        assertEquals(1, chain.size());
    }

    @Test
    void testBuilderAddNullThrows() {
        NodeChain.Builder builder = new NodeChain.Builder();

        assertThrows(NullPointerException.class, () ->
                builder.add(null)
        );
    }

    @Test
    void testEmptyBuilderReturnsSingleton() {
        NodeChain chain1 = new NodeChain.Builder().build();
        NodeChain chain2 = new NodeChain.Builder().build();

        assertSame(chain1, chain2);
    }

    @Test
    void testEqualsSameContent() {
        // NOTE: They will only be equal if they contain the same LLPNode instances in the same order,
        // unless the “equals” method is overridden in the specific LLPNode implement

        LLPNode node1 = new TestNode(1);
        LLPNode node2 = new TestNode(2);

        NodeChain c1 = new NodeChain.Builder()
                .add(node1)
                .add(node2)
                .build();

        NodeChain c2 = new NodeChain.Builder()
                .add(node1)
                .add(node2)
                .build();

        NodeChain c3 = new NodeChain.Builder()
                .add(new TestNode(3))
                .add(new TestNode(4))
                .build();

        NodeChain c4 = new NodeChain.Builder()
                .add(new TestNode(3))
                .add(new TestNode(4))
                .build();

        NodeChain c5 = new NodeChain.Builder()
                .add(new SpecialNode(5))
                .add(new SpecialNode(6))
                .build();

        NodeChain c6 = new NodeChain.Builder()
                .add(new SpecialNode(5))
                .add(new SpecialNode(6))
                .build();

        // NodeChain with the same LLPNode instances
        assertNotSame(c1, c2);
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());

        // NodeChain with different LLPNode instances, but with the same internal value
        assertNotSame(c3, c4);
        assertNotEquals(c3, c4);
        assertNotEquals(c3.hashCode(), c4.hashCode());

        // NodeChain with different LLPNode instances that implement equals
        assertNotSame(c5, c6);
        assertEquals(c5, c6);
    }

    @Test
    void testEqualsDifferentOrder() {
        NodeChain c1 = new NodeChain.Builder()
                .add(new TestNode(1))
                .add(new TestNode(2))
                .build();

        NodeChain c2 = new NodeChain.Builder()
                .add(new TestNode(2))
                .add(new TestNode(1))
                .build();

        assertNotEquals(c1, c2);
    }

    @Test
    void testEqualsDifferentContent() {
        NodeChain c1 = new NodeChain.Builder()
                .add(new TestNode(1))
                .build();

        NodeChain c2 = new NodeChain.Builder()
                .add(new TestNode(2))
                .build();

        assertNotEquals(c1, c2);
    }

    @Test
    void testEmptyChainBehavior() {
        NodeChain empty = NodeChain.EMPTY;

        assertEquals(0, empty.size());
        assertTrue(empty.asList().isEmpty());
        assertTrue(empty.getNode(1).isEmpty());
        assertTrue(empty.getNode(TestNode.class).isEmpty());
    }

    @Test
    void testEmptyIdentity() {
        NodeChain chain = new NodeChain.Builder().build();

        assertSame(NodeChain.EMPTY, chain);
    }

    @Test
    void testBuilderListIsolation() {
        List<LLPNode> original = new ArrayList<>();
        original.add(new TestNode(1));

        NodeChain chain = new NodeChain.Builder()
                .add(original.getFirst())
                .build();

        original.clear();

        assertEquals(1, chain.size(), "Chain should not be affected by external list changes");
    }
}
