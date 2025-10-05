package com.strumenta.kolasu.model

val RESERVED_FEATURE_NAMES = setOf("parent", "position")

fun <N : BaseASTNode> N.withPosition(position: Position?): N {
    this.position = position
    return this
}

fun <N : BaseASTNode> N.withOrigin(origin: Origin?): N {
    this.origin =
        if (origin == this) {
            null
        } else {
            origin
        }
    return this
}

fun <N : Node> N.withDestination(destination: Destination): N {
    this.destination =
        if (destination == this) {
            null
        } else {
            destination
        }
    return this
}

fun <N : Node> N.withDroppedDestination(): N = withDestination(DroppedDestination)

/**
 * Use this to mark properties that are internal, i.e., they are used for bookkeeping and are not part of the model,
 * so that they will not be considered branches of the AST.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Internal

/**
 * Use this to mark all relations which are secondary, i.e., they are calculated from other relations,
 * so that they will not be considered branches of the AST.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Derived

/**
 * Use this to mark all the properties that return a Node or a list of Nodes which are not
 * contained by the Node having the properties. In other words: they are just references.
 * This will prevent them from being considered branches of the AST.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Link

/**
 * Use this to mark something that does not inherit from Node as a node, so it will be included in the AST.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class NodeType

fun checkFeatureName(featureName: String) {
    require(featureName !in RESERVED_FEATURE_NAMES) { "$featureName is not a valid feature name" }
}

/**
 * Use this to mark a type representing an AST Root.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ASTRoot(
    val canBeNotRoot: Boolean = false,
)
