package com.strumenta.starlasu.javalib;

import com.strumenta.starlasu.model.PossiblyNamed;
import com.strumenta.starlasu.model.ReferenceByName;

public class Node1 extends JavaNode implements PossiblyNamed {
    private Node2 node2;
    private ReferenceByName<Node2> node2Ref;

    public @Mandatory Node2 getNode2() {
        return node2;
    }

    public void setNode2(Node2 node2) {
        this.node2 = node2;
    }

    public ReferenceByName<Node2> getNode2Ref() {
        return node2Ref;
    }

    public void setNode2Ref(ReferenceByName<Node2> node2Ref) {
        this.node2Ref = node2Ref;
    }

    @Override
    public String getName() {
        return node2 != null ? node2.getName() : null;
    }
}
