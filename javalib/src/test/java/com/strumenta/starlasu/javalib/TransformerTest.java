package com.strumenta.starlasu.javalib;

import com.strumenta.starlasu.model.*;
import com.strumenta.starlasu.transformation.TransformationContext;
import com.strumenta.starlasu.validation.Issue;
import org.junit.Test;

import java.util.ArrayList;

import static com.strumenta.starlasu.javalib.ASTTransformer.setter;
import static com.strumenta.starlasu.testing.Testing.assertASTsAreEqual;
import static org.junit.Assert.*;

public class TransformerTest {
    @Test
    public void testJavaNodes() {
        ArrayList<Issue> issues = new ArrayList<>();
        ASTTransformer t = new ASTTransformer( false);
        t.registerNodeFactory(Node1.class, Node1.class)
                .withChild(Node1::getNode2, setter(Node1::setNode2), "node2");
        t.registerNodeFactory(Node2.class, Node2.class);
        Node1 node1 = new Node1();
        node1.setNode2(new Node2());
        ASTNode transformed = t.transform(node1, new TransformationContext(issues));
        assertTrue(issues.toString(), issues.isEmpty());
        assertASTsAreEqual(node1, transformed);
    }
}

