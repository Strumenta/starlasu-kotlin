package com.strumenta.starlasu.mapping

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Origin
import com.strumenta.starlasu.parsing.ParseTreeOrigin
import com.strumenta.starlasu.parsing.withParseTreeNode
import com.strumenta.starlasu.transformation.ASTTransformer
import com.strumenta.starlasu.transformation.FailingASTTransformation
import com.strumenta.starlasu.transformation.FaultTolerance
import com.strumenta.starlasu.transformation.Transform
import com.strumenta.starlasu.transformation.TransformationContext
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import kotlin.reflect.KClass

/**
 * Implements a transformation from an ANTLR parse tree (the output of the parser) to an AST (a higher-level
 * representation of the source code).
 */
open class ParseTreeToASTTransformer
    @JvmOverloads
    constructor(
        faultTolerance: FaultTolerance = FaultTolerance.THROW_ONLY_ON_UNMAPPED,
    ) : ASTTransformer(faultTolerance) {
        /**
         * Performs the transformation of a node and, recursively, its descendants. In addition to the overridden method,
         * it also assigns the parseTreeNode to the AST node so that it can keep track of its position.
         * However, a node factory can override the parseTreeNode of the nodes it creates (but not the parent).
         */
        override fun transformIntoNodes(
            source: Any?,
            context: TransformationContext,
            expectedType: KClass<out ASTNode>,
        ): List<ASTNode> {
            if (source is ParserRuleContext && source.exception != null) {
                if (faultTolerance == FaultTolerance.STRICT) {
                    throw RuntimeException("Failed to transform $source into $expectedType", source.exception)
                }
                val origin =
                    FailingASTTransformation(
                        asOrigin(source, context),
                        "Failed to transform $source into $expectedType because of an error (${source.exception.message})",
                    )
                val nodes = defaultNodes(source, context, expectedType)
                nodes.forEach { node ->
                    node.origin = origin
                }
                return nodes
            }
            val transformed = super.transformIntoNodes(source, context, expectedType)
            return transformed
                .map { node ->
                    if (source is ParserRuleContext) {
                        if (node.origin == null) {
                            node.withParseTreeNode(source, context.source)
                        } else if (node.position != null && node.source == null) {
                            node.position!!.source = context.source
                        }
                    }
                    return listOf(node)
                }.flatten()
        }

        override fun getSource(
            node: ASTNode,
            source: Any,
        ): Any {
            val origin = node.origin
            return if (origin is ParseTreeOrigin) origin.parseTree else source
        }

        override fun asOrigin(
            source: Any,
            context: TransformationContext,
        ): Origin? =
            if (source is ParseTree) {
                ParseTreeOrigin(source, context.source)
            } else {
                null
            }

        override fun asString(source: Any): String? =
            if (source is ParseTree) {
                source.text
            } else {
                super.asString(source)
            }

        /**
         * Often in ANTLR grammar we have rules which wraps other rules and act as
         * wrapper. When there is only a ParserRuleContext child we can transform
         * that child and return that result.
         */
        fun <P : ParserRuleContext> registerTransformUnwrappingChild(kclass: KClass<P>): Transform<P, Node> =
            registerTransform(kclass) { source, context, _ ->
                val nodeChildren = source.children.filterIsInstance<ParserRuleContext>()
                require(nodeChildren.size == 1) {
                    "Node $source (${source.javaClass}) has ${nodeChildren.size} " +
                        "node children: $nodeChildren"
                }
                transform(nodeChildren[0], context) as Node
            }

        /**
         * Alternative to registerNodeFactoryUnwrappingChild(KClass) which is slightly more concise.
         */
        inline fun <reified P : ParserRuleContext> registerTransformUnwrappingChild(): Transform<P, Node> =
            registerTransformUnwrappingChild(P::class)
    }
