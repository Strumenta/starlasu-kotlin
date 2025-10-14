package com.strumenta.kolasu.javalib;

import com.strumenta.kolasu.model.ASTNode;
import com.strumenta.kolasu.model.BaseASTNode;
import com.strumenta.kolasu.model.Processing;
import com.strumenta.kolasu.traversing.ProcessingStructurally;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.strumenta.kolasu.javalib.CompilationUnit.A;
import static com.strumenta.kolasu.javalib.CompilationUnit.B;
import static org.junit.Assert.assertEquals;


public class TraversingTest {

    private CompilationUnit cu = new CompilationUnit();
    private A a1 = new A();
    private B b1 = new B();
    private B b2 = new B();
    private A a2 = new A();
    private B b3 = new B();

    public TraversingTest() {
        cu.getAs().add(a1);
        cu.getAs().add(a2);
        a1.getBs().add(b1);
        a1.getBs().add(b2);
        a2.getBs().add(b3);
        Processing.assignParents(cu);
    }

    @Test
    public void testGetChildren() {
        assertEquals(Arrays.asList(a1, a2), ProcessingStructurally.getChildren(cu));
    }

    @Test
    public void testWalk() {
        List<ASTNode> nodes = Traversing.walk(cu).collect(Collectors.toList());
        assertEquals(Arrays.asList(cu, a1, b1, b2, a2, b3), nodes);
    }
}
