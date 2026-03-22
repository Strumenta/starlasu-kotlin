package com.strumenta.kolasu.lionweb

import com.strumenta.kolasu.model.ASTRoot
import com.strumenta.kolasu.model.Node

// ---------------------------------------------------------------------------
// Minimal AST node hierarchy shared by all lionweb JMH benchmarks.
// ---------------------------------------------------------------------------

/** Leaf node carrying a string payload. */
data class BenchLeafNode(val value: String) : Node()

/** Intermediate container holding a flat list of leaves. */
data class BenchContainerNode(
    val label: String,
    val leaves: MutableList<BenchLeafNode> = mutableListOf()
) : Node()

/** Root of the benchmark tree. Must be annotated so StructuralNodeIdProvider accepts it. */
@ASTRoot
data class BenchRoot(
    val containers: MutableList<BenchContainerNode> = mutableListOf()
) : Node()
