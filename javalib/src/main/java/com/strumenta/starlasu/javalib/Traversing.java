package com.strumenta.starlasu.javalib;

import com.strumenta.starlasu.model.ASTNode;
import com.strumenta.starlasu.model.Position;
import com.strumenta.starlasu.traversing.ProcessingByPosition;
import com.strumenta.starlasu.traversing.ProcessingStructurally;
import kotlin.jvm.internal.Reflection;
import kotlin.sequences.Sequence;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Traversing {

    private static <T> void consumeSequence(Sequence<T> sequence, Consumer<T> consumer) {
        for (Iterator<T> it = sequence.iterator(); it.hasNext();) {
            consumer.accept(it.next());
        }
    }

    private static <T> Stream<T> asStream(Sequence<T> sequence) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(sequence.iterator(),
                Spliterator.ORDERED), false);
    }

    public static Stream<ASTNode> walk(ASTNode node) {
        return asStream(ProcessingStructurally.walk(node));
    }

    /**
     * Performs a post-order (or leaves-first) node traversal starting with a given node.
     */
    public static Stream<ASTNode> walkLeavesFirst(ASTNode node) {
        return asStream(ProcessingStructurally.walkLeavesFirst(node));
    }

    public static Stream<ASTNode> walkAncestors(ASTNode node) {
        return asStream(ProcessingStructurally.walkAncestors(node));
    }

    public static Stream<ASTNode> walkDescendantsBreadthFirst(ASTNode node) {
        return asStream(ProcessingStructurally.walkDescendants(node, ProcessingStructurally::walk));
    }

    public static Stream<ASTNode> walkDescendantsLeavesFirst(ASTNode node) {
        return asStream(ProcessingStructurally.walkDescendants(node, ProcessingStructurally::walkLeavesFirst));
    }

    public static <N> Stream<N> walkDescendantsBreadthFirst(ASTNode node, Class<N> clazz) {
        return asStream(ProcessingStructurally.walkDescendants(node, Reflection.createKotlinClass(clazz), ProcessingStructurally::walk));
    }

    public static <N> Stream<N> walkDescendantsLeavesFirst(ASTNode node, Class<N> clazz) {
        return asStream(ProcessingStructurally.walkDescendants(node, Reflection.createKotlinClass(clazz), ProcessingStructurally::walkLeavesFirst));
    }

    public static void walk(ASTNode node, Consumer<ASTNode> consumer) {
        consumeSequence(ProcessingStructurally.walk(node), consumer);
    }

    /**
     * Performs a post-order (or leaves-first) node traversal starting with a given node.
     */
    public static void walkLeavesFirst(ASTNode node, Consumer<ASTNode> consumer) {
        consumeSequence(ProcessingStructurally.walkLeavesFirst(node), consumer);
    }

    public static void walkAncestors(ASTNode node, Consumer<ASTNode> consumer) {
        consumeSequence(ProcessingStructurally.walkAncestors(node), consumer);
    }

    public static void walkDescendantsBreadthFirst(ASTNode node, Consumer<ASTNode> consumer) {
        consumeSequence(ProcessingStructurally.walkDescendants(node, ProcessingStructurally::walk), consumer);
    }

    public static void walkDescendantsLeavesFirst(ASTNode node, Consumer<ASTNode> consumer) {
        consumeSequence(ProcessingStructurally.walkDescendants(node, ProcessingStructurally::walkLeavesFirst), consumer);
    }

    public static <N> void walkDescendantsBreadthFirst(ASTNode node, Class<N> clazz, Consumer<ASTNode> consumer) {
        consumeSequence(ProcessingStructurally.walkDescendants(node, Reflection.createKotlinClass(clazz), ProcessingStructurally::walk), consumer);
    }

    public static <N> void walkDescendantsLeavesFirst(ASTNode node, Class<N> clazz, Consumer<ASTNode> consumer) {
        consumeSequence(ProcessingStructurally.walkDescendants(node, Reflection.createKotlinClass(clazz), ProcessingStructurally::walkLeavesFirst), consumer);
    }

    /**
     * Walks the AST within the given position starting from the given node
     * and returns the result as sequence to consume.
     *
     * @param node     the node from which the walk should start
     * @param position the position within which the walk should remain
     */
    public static <N> void walkWithin(ASTNode node, Position position, Consumer<ASTNode> consumer) {
        consumeSequence(ProcessingByPosition.walkWithin(node, position), consumer);
    }

    /**
     * Walks the AST within the given position starting from each give node
     * and concatenates all results in a single sequence to consume.
     *
     * @param nodes    the nodes from which the walk should start
     * @param position the position within which the walk should remain
     */
    public static <N> void walkWithin(List<ASTNode> nodes, Position position, Consumer<ASTNode> consumer) {
        consumeSequence(ProcessingByPosition.walkWithin(nodes, position), consumer);
    }

    public static <T> T findAncestorOfType(ASTNode node, Class<T> clazz) {
        return ProcessingStructurally.findAncestorOfType(node, clazz);
    }
}