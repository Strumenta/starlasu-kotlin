package com.strumenta.starlasu.parsing

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Either a Parse Tree terminal/leaf or non-terminal/node
 */
sealed class ParseTreeElement {
    abstract fun multiLineString(indentation: String = ""): String
}

/**
 * Representation of the information contained in a Parse Tree terminal or leaf.
 */
class ParseTreeLeaf(
    val type: String,
    val text: String,
) : ParseTreeElement() {
    override fun toString(): String = "T:$type[$text]"

    override fun multiLineString(indentation: String): String = "${indentation}T:$type[$text]\n"
}

/**
 * Representation of the information contained in a Parse Tree non-terminal or node.
 */
class ParseTreeNode(
    val name: String,
) : ParseTreeElement() {
    val children = mutableListOf<ParseTreeElement>()

    fun child(c: ParseTreeElement): ParseTreeNode {
        children.add(c)
        return this
    }

    override fun toString(): String = "Node($name) $children"

    override fun multiLineString(indentation: String): String {
        val sb = StringBuilder()
        sb.append("${indentation}$name\n")
        children.forEach { c -> sb.append(c.multiLineString("$indentation  ")) }
        return sb.toString()
    }
}

/**
 * Given an actual parse-tree produced by ANTLR, it creates a Parse Tree model.
 */
fun toParseTreeModel(
    node: ParserRuleContext,
    vocabulary: Vocabulary,
): ParseTreeNode {
    val res = ParseTreeNode(node.javaClass.simpleName.removeSuffix("Context"))
    node.children?.forEach { c ->
        when (c) {
            is ParserRuleContext -> res.child(toParseTreeModel(c, vocabulary))
            is TerminalNode -> res.child(ParseTreeLeaf(vocabulary.getSymbolicName(c.symbol.type), c.text))
        }
    }
    return res
}
