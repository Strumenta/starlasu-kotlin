package com.strumenta.kolasu.javalib;

import com.strumenta.kolasu.model.Multiplicity;
import com.strumenta.kolasu.model.PropertyDescription;
import com.strumenta.kolasu.model.PropertyType;
import com.strumenta.kolasu.model.ReferenceByName;
import org.junit.Test;

import java.util.Arrays;

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
                                "name", false, Multiplicity.OPTIONAL, "", PropertyType.ATTRIBUTE,
                                false, JavaNode.kotlinType(String.class, true)),
                        new PropertyDescription(
                                "node2", true, Multiplicity.SINGULAR, node1.getNode2(), PropertyType.CONTAINMENT,
                                false, JavaNode.kotlinType(Node2.class, false)),
                        new PropertyDescription(
                                "node2Ref", false, Multiplicity.OPTIONAL, node1.getNode2Ref(), PropertyType.REFERENCE,
                                false, JavaNode.kotlinType(Node2.class, true))
                ),
                node1.getProperties());
        assertEquals(
                Arrays.asList(
                        new PropertyDescription(
                                "name", false, Multiplicity.OPTIONAL, "", PropertyType.ATTRIBUTE,
                                false, JavaNode.kotlinType(String.class, true)),
                        new PropertyDescription(
                                "nodeArray", false, Multiplicity.OPTIONAL, null, PropertyType.ATTRIBUTE,
                                false, JavaNode.kotlinType(Node2[].class, true)),
                        new PropertyDescription(
                                "objArray", false, Multiplicity.OPTIONAL, null, PropertyType.ATTRIBUTE,
                                false, JavaNode.kotlinType(Object[].class, true)),
                        new PropertyDescription(
                                "primArray", false, Multiplicity.OPTIONAL, null, PropertyType.ATTRIBUTE,
                                false, JavaNode.kotlinType(int[].class, true))
                ),
                node1.getNode2().getProperties());
    }
}
