package com.flamingo.comm.llp.core;

class SpecialNode extends TestNode {
    SpecialNode(int id) {
        super(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpecialNode that)) return false;
        return getId() == that.getId();
    }
}
