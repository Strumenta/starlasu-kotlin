package com.strumenta.starlasu.mapping

import com.strumenta.starlasu.model.BaseASTNode
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Origin
import com.strumenta.starlasu.model.Source
import com.strumenta.starlasu.parsing.ParseTreeOrigin
import com.strumenta.starlasu.parsing.withParseTreeNode
import com.strumenta.starlasu.transformation.ASTTransformer
import com.strumenta.starlasu.transformation.NodeFactory
import com.strumenta.starlasu.validation.Issue
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
        issues: MutableList<Issue> = mutableListOf(),
        allowGenericNode: Boolean = true,
        val source: Source? = null,
        throwOnUnmappedNode: Boolean = true,
    ) : ASTTransformer(issues, allowGenericNode, throwOnUnmappedNode) {
        /**
         * Performs the transformation of a node and, recursively, its descendants. In addition to the overridden method,
         * it also assigns the parseTreeNode to the AST node so that it can keep track of its position.
         * However, a node factory can override the parseTreeNode of the nodes it creates (but not the parent).
         */
        override fun transformIntoNodes(
            source: Any?,
            parent: BaseASTNode?,
            expectedType: KClass<out BaseASTNode>,
        ): List<BaseASTNode> {
            val transformed = super.transformIntoNodes(source, parent, expectedType)
            return transformed
                .map { node ->
                    if (source is ParserRuleContext) {
                        if (node.origin == null) {
                            node.withParseTreeNode(source, this.source)
                        } else if (node.position != null && node.source == null) {
                            node.position!!.source = this.source
                        }
                    }
                    return listOf(node)
                }.flatten()
        }

        override fun getSource(
            node: Node,
            source: Any,
        ): Any {
            val origin = node.origin
            return if (origin is ParseTreeOrigin) origin.parseTree else source
        }

        override fun asOrigin(source: Any): Origin? = if (source is ParseTree) ParseTreeOrigin(source) else null

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
        fun <P : ParserRuleContext> registerNodeFactoryUnwrappingChild(kclass: KClass<P>): NodeFactory<P, Node> =
            registerNodeFactory(kclass) { source, transformer, _ ->
                val nodeChildren = source.children.filterIsInstance<ParserRuleContext>()
                require(nodeChildren.size == 1) {
                    "Node $source (${source.javaClass}) has ${nodeChildren.size} " +
                        "node children: $nodeChildren"
                }
                transformer.transform(nodeChildren[0]) as Node
            }

        /**
         * Alternative to registerNodeFactoryUnwrappingChild(KClass) which is slightly more concise.
         */
        inline fun <reified P : ParserRuleContext> registerNodeFactoryUnwrappingChild(): NodeFactory<P, Node> =
            registerNodeFactoryUnwrappingChild(P::class)
    }
