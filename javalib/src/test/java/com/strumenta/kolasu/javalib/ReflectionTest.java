package com.strumenta.kolasu.javalib;

import com.strumenta.kolasu.model.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ReflectionTest {
    @Test
    public void testReflection() {
        Node1 node1 = new Node1();
        node1.setNode2(new Node2());
        node1.setNode2Ref(new ReferenceByName<>("", node1.getNode2()));
        assertEquals(
                Arrays.asList(
                        new PropertyDescription(
                                "name", Multiplicity.OPTIONAL, "", PropertyType.ATTRIBUTE,
                                false, JavaNode.kotlinType(String.class, true)),
                        new PropertyDescription(
                                "node2", Multiplicity.SINGULAR, node1.getNode2(), PropertyType.CONTAINMENT,
                                false, JavaNode.kotlinType(Node2.class, false)),
                        new PropertyDescription(
                                "node2Ref", Multiplicity.OPTIONAL, node1.getNode2Ref(), PropertyType.REFERENCE,
                                false, JavaNode.kotlinType(Node2.class, true))
                ),
                node1.getProperties());
        assertEquals(
                Arrays.asList(
                        new PropertyDescription(
                                "name", Multiplicity.OPTIONAL, "", PropertyType.ATTRIBUTE,
                                false, JavaNode.kotlinType(String.class, true)),
                        new PropertyDescription(
                                "nodeArray", Multiplicity.OPTIONAL, (Object) null, PropertyType.ATTRIBUTE,
                                false, JavaNode.kotlinType(Node2[].class, true)),
                        new PropertyDescription(
                                "objArray",  Multiplicity.OPTIONAL, (Object) null, PropertyType.ATTRIBUTE,
                                false, JavaNode.kotlinType(Object[].class, true)),
                        new PropertyDescription(
                                "primArray",Multiplicity.OPTIONAL, (Object) null, PropertyType.ATTRIBUTE,
                                false, JavaNode.kotlinType(int[].class, true))
                ),
                node1.getNode2().getProperties());
    }

    @Test
    public void testLazyValue() {
        class MyNode extends JavaNode {
            private int count = 0;
            public int getAttribute() {
                return count++;
            }
        }

        MyNode node = new MyNode();
        assertEquals(0, node.getAttribute());
        List<PropertyDescription> properties = node.getProperties();
        assertEquals(1, properties.size());
        properties.forEach(p -> assertEquals(1, p.getValue()));
        assertEquals(2, node.getAttribute());
        properties.forEach(p -> assertEquals(1, p.getValue()));
    }
}
