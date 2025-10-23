package com.strumenta.starlasu.mapping

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.parsing.getOriginalText
import com.strumenta.starlasu.transformation.ASTTransformer
import com.strumenta.starlasu.transformation.TransformationContext
import org.antlr.v4.runtime.ParserRuleContext

/**
 * Translate the given node and ensure a certain type will be obtained.
 *
 * Example:
 * ```
 * JPostIncrementExpr(translateCasted<JExpression>(expression().first()))
 * ```
 */
inline fun <reified T : ASTNode> ASTTransformer.translateCasted(
    original: Any,
    context: TransformationContext,
): T {
    val result = transform(original, context, expectedType = T::class)
    if (result is Nothing) {
        throw IllegalStateException("Transformation produced Nothing")
    }
    return result as T
}

/**
 * Translate a whole collection into a mutable list, translating each element and ensuring
 * the list has the expected type.
 *
 * Example:
 * ```
 * JExtendsType(translateCasted(pt.typeType()), translateList(pt.annotation()))
 * ```
 */
inline fun <reified T : ASTNode> ASTTransformer.translateList(
    original: Collection<out Any>?,
    context: TransformationContext,
): MutableList<T> =
    original?.map { transformIntoNodes(it, context, expectedType = T::class) as List<T> }?.flatten()?.toMutableList()
        ?: mutableListOf()

/**
 * Translate the given node and ensure a certain type will be obtained, if the value is not null.
 * If the value is null, null is returned.
 *
 * Example:
 * ```
 *  JVariableDeclarator(
 *      name = pt.variableDeclaratorId().text,
 *      arrayDimensions = mutableListOf(),
 *      initializer = translateOptional(pt.variableInitializer())
 *  )
 *  ```
 */
inline fun <reified T : ASTNode> ASTTransformer.translateOptional(
    original: Any?,
    context: TransformationContext,
): T? {
    return original?.let {
        val transformed = transform(it, context, expectedType = T::class)
        if (transformed == null) {
            return null
        } else {
            transformed as T
        }
    }
}

/**
 * Translate the only child (of type ParseRuleContext) and ensure the resulting value
 * as the expected type.
 *
 * Example:
 * ```
 * registerNodeFactory<MemberDeclarationContext, JEntityMember> {
 *     translateOnlyChild<JEntityMember>(this)
 * }
 * ```
 */
fun <T> ParseTreeToASTTransformer.translateOnlyChild(
    parent: ParserRuleContext,
    context: TransformationContext,
): T = translateCasted(parent.onlyChild, context)

/**
 * It returns the only child (of type ParseRuleContext). If there is no children or more than
 * one child, an exception is thrown.
 */
val ParserRuleContext.onlyChild: ParserRuleContext
    get() {
        val nodeChildren = children.filterIsInstance<ParserRuleContext>()
        require(nodeChildren.size == 1) {
            "ParserRuleContext was expected to have exactly one child, " +
                "while it has ${nodeChildren.size}. ParserRuleContext: ${this.getOriginalText()} " +
                "(${this.javaClass.canonicalName})"
        }
        return nodeChildren[0]
    }
