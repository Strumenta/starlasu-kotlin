package com.strumenta.starlasu.ids

/**
 * Coordinates indicate where a Node is stored with an AST. A root node will have RootCoordinates, a node that is not
 * a root node will have InternalCoordinates. InternalCoordinates indicates the parent (or its id), the containment
 * property and index.
 *
 * For example, if I have this:
 *
 * ```
 * class ClassDeclaration(val name: String, val members: List<Member>) : Node()
 * abstract class Member: Node()
 * class FieldDeclaration(val name: String): Node()
 *
 * val f1 = FieldDeclaration("F1")
 * val f2 = FieldDeclaration("F2")
 * val f3 = FieldDeclaration("F3")
 * val myClass = ClassDeclaration("foo", listOf(f1, f2, f3))
 * ```
 * Then:
 * - myClass will have RootCoordinates
 * - F1 will have InternalCoordinates(parent=myClass, property="members", index=0)
 * - F2 will have InternalCoordinates(parent=myClass, property="members", index=1)
 * - F3 will have InternalCoordinates(parent=myClass, property="members", index=2)
 */
sealed class Coordinates

/**
 * Coordinates indicating the root of the AST.
 */
object RootCoordinates : Coordinates()

/**
 * Coordinates indicating the position of all nodes but the root of the AST.
 */
data class InternalCoordinates(
    val containerID: String,
    val containmentName: String,
    val indexInContainment: Int,
) : Coordinates()
