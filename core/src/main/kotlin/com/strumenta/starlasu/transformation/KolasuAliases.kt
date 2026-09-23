package com.strumenta.starlasu.transformation

import com.strumenta.starlasu.model.ASTNode

/*
 * Kolasu 1.5 names of the transformation types. They are kept only to ease the migration of 1.5 language modules
 * to Starlasu 1.7 (see docs/migration-1.5-to-1.7.md) and will be removed in a future release.
 *
 * Note that the constructor of [TransformationRule] takes a [TransformationContext], so code creating
 * `NodeFactory` instances directly still needs to be adapted.
 */

@Deprecated("Renamed in Starlasu 1.7", ReplaceWith("TransformationRule<Source, Output>"))
typealias NodeFactory<Source, Output> = TransformationRule<Source, Output>

@Deprecated("Renamed in Starlasu 1.7", ReplaceWith("ChildTransformationRule<Source, Target, Child>"))
typealias ChildNodeFactory<Source, Target, Child> = ChildTransformationRule<Source, Target, Child>

@Suppress("unused", "DEPRECATION")
private fun <Source, Output : ASTNode> boundsCheck(factory: NodeFactory<Source, Output>) = factory
