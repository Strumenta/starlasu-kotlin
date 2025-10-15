package com.strumenta.starlasu.model

@NodeType
sealed interface CommonElement

/**
 * Used to mark nodes as statements (instructions used primarily for their side effects)
 */
interface Statement : CommonElement

/**
 * Used to mark nodes as expressions (descriptions of computations producing a value and, possibly, side effects)
 */
interface Expression : CommonElement

/**
 * This should be used for definitions of classes, interfaces, records, structures, and the like.
 */
interface EntityDeclaration : CommonElement

/**
 * This should be used for definitions of functions, methods, etc.
 */
interface BehaviorDeclaration : CommonElement

/**
 * Used to mark nodes as formal parameters (such as function/method parameters, type parameters, etc.)
 */
interface Parameter : CommonElement

/**
 * This should be used for documentation elements, such as docstrings and Javadoc-style comments
 */
interface Documentation : CommonElement

/**
 * This should be used for definitions of modules, packages, namespaces, and similar
 */
interface EntityGroupDeclaration : CommonElement

/**
 * This should be used for explicit type annotations (e.g. int, String, etc.)
 */
interface TypeAnnotation : CommonElement
