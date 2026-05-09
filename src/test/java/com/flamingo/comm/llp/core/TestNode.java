package com.flamingo.comm.llp.core;

import com.flamingo.comm.llp.spi.LLPNode;

class TestNode implements LLPNode {
    private final int id;

    TestNode(int id) {
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }
}
