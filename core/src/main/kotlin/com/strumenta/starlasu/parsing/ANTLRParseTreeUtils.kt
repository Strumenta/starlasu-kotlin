@file:JvmName("ANTLRParseTreeUtils")

package com.strumenta.starlasu.parsing

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.Origin
import com.strumenta.starlasu.model.Point
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.Source
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import kotlin.reflect.KClass

/**
 * Navigate the parse tree performing the specified operations on the nodes, either real nodes or nodes
 * representing errors.
 */
fun ParserRuleContext.processDescendantsAndErrors(
    operationOnParserRuleContext: (ParserRuleContext) -> Unit,
    operationOnError: (ErrorNode) -> Unit,
    includingMe: Boolean = true,
) {
    if (includingMe) {
        operationOnParserRuleContext(this)
    }
    if (this.children != null) {
        this.children.filterIsInstance<ParserRuleContext>().forEach {
            it.processDescendantsAndErrors(operationOnParserRuleContext, operationOnError, includingMe = true)
        }
        this.children.filterIsInstance<ErrorNode>().forEach {
            operationOnError(it)
        }
    }
}

/**
 * Get the original text associated to this non-terminal by querying the inputstream.
 */
fun ParserRuleContext.getOriginalText(): String {
    val a: Int = this.start.startIndex
    val b: Int = this.stop.stopIndex
    if (a > b) {
        throw IllegalStateException("Start index should be less than or equal to the stop index. Start: $a, Stop: $b")
    }
    val interval =
        org.antlr.v4.runtime.misc
            .Interval(a, b)
    return this.start.inputStream.getText(interval)
}

/**
 * Get the original text associated to this terminal by querying the inputstream.
 */
fun TerminalNode.getOriginalText(): String = this.symbol.getOriginalText()

/**
 * Get the original text associated to this token by querying the inputstream.
 */
fun Token.getOriginalText(): String {
    val a: Int = this.startIndex
    val b: Int = this.stopIndex
    if (a > b) {
        throw IllegalStateException("Start index should be less than or equal to the stop index. Start: $a, Stop: $b")
    }
    val interval =
        org.antlr.v4.runtime.misc
            .Interval(a, b)
    return this.inputStream.getText(interval)
}

/**
 * Given the entire code, this returns the slice covered by this Node.
 */
fun Node.getText(code: String): String? = position?.text(code)

/**
 * An Origin corresponding to a ParseTreeNode. This is used to indicate that an AST Node has been obtained
 * by mapping an original ParseTreeNode.
 *
 * Note that this is NOT serializable as ParseTree elements are not Serializable.
 */
class ParseTreeOrigin(
    val parseTree: ParseTree,
    override var source: Source? = null,
) : Origin {
    override val position: Position?
        get() = parseTree.toPosition(source = source)

    override val sourceText: String?
        get() =
            when (parseTree) {
                is ParserRuleContext -> {
                    parseTree.getOriginalText()
                }

                is TerminalNode -> {
                    parseTree.text
                }

                else -> null
            }
}

/**
 * Set the origin of the AST node as a ParseTreeOrigin, providing the parseTree is not null.
 * If the parseTree is null, no operation is performed.
 */
fun <T : Node> T.withParseTreeNode(
    parseTree: ParserRuleContext?,
    source: Source? = null,
): T {
    if (parseTree != null) {
        this.origin = ParseTreeOrigin(parseTree, source)
    }
    return this
}

val RuleContext.hasChildren: Boolean
    get() = this.childCount > 0

val RuleContext.firstChild: ParseTree?
    get() = if (hasChildren) this.getChild(0) else null

val RuleContext.lastChild: ParseTree?
    get() = if (hasChildren) this.getChild(this.childCount - 1) else null

val Token.length
    get() = if (this.type == Token.EOF) 0 else text.length

val Token.startPoint: Point
    get() = Point(this.line, this.charPositionInLine)

val Token.endPoint: Point
    get() = if (this.type == Token.EOF) startPoint else startPoint + this.text

val Token.position: Position
    get() = Position(startPoint, endPoint)

/**
 * Returns the position of the receiver parser rule context.
 */
val ParserRuleContext.position: Position
    get() = Position(start.startPoint, stop.endPoint)

/**
 * Returns the position of the receiver parser rule context.
 * @param considerPosition if it's false, this method returns null.
 */
fun ParserRuleContext.toPosition(
    considerPosition: Boolean = true,
    source: Source? = null,
): Position? =
    if (considerPosition && start != null && stop != null) {
        val position = position
        if (source == null) position else Position(position.start, position.end, source)
    } else {
        null
    }

fun TerminalNode.toPosition(
    considerPosition: Boolean = true,
    source: Source? = null,
): Position? = this.symbol.toPosition(considerPosition, source)

fun Token.toPosition(
    considerPosition: Boolean = true,
    source: Source? = null,
): Position? = if (considerPosition) Position(this.startPoint, this.endPoint, source) else null

fun ParseTree.toPosition(
    considerPosition: Boolean = true,
    source: Source? = null,
): Position? =
    when (this) {
        is TerminalNode -> this.toPosition(considerPosition, source)
        is ParserRuleContext -> this.toPosition(considerPosition, source)
        else -> null
    }

/**
 * Find the ancestor of the given element with the given class.
 */
inline fun <reified T : RuleContext> RuleContext.ancestor(): T = this.ancestor(T::class)

/**
 * Find the ancestor of the given element with the given class.
 */
fun <T : RuleContext> RuleContext.ancestor(kclass: KClass<T>): T =
    if (this.parent == null) {
        throw IllegalStateException("Cannot find ancestor of type $kclass")
    } else if (kclass.isInstance(this.parent)) {
        this.parent as T
    } else {
        this.parent.ancestor(kclass)
    }
