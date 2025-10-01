package com.strumenta.kolasu.javalib;

import com.strumenta.kolasu.model.Named;
import org.jetbrains.annotations.NotNull;

public class Node2 extends JavaNode implements Named {
    private int[] primArray;
    private Object[] objArray;
    private Node2[] nodeArray;

    public int[] getPrimArray() {
        return primArray;
    }

    public void setPrimArray(int[] primArray) {
        this.primArray = primArray;
    }

    public Object[] getObjArray() {
        return objArray;
    }

    public void setObjArray(Object[] objArray) {
        this.objArray = objArray;
    }

    public Node2[] getNodeArray() {
        return nodeArray;
    }

    public void setNodeArray(Node2[] nodeArray) {
        this.nodeArray = nodeArray;
    }

    @Override
    @NotNull
    public String getName() {
        return "";
    }
}
