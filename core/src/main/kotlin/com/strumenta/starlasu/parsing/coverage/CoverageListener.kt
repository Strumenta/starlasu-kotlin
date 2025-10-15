package com.strumenta.starlasu.parsing.coverage

import com.strumenta.starlasu.traversing.mutableStackOf
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.atn.AtomTransition
import org.antlr.v4.runtime.atn.RuleTransition
import org.antlr.v4.runtime.atn.SetTransition
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeListener
import org.antlr.v4.runtime.tree.TerminalNode

data class PathElement(
    val symbol: Int,
    val rule: Boolean,
) {
    fun toString(parser: Parser): String =
        if (rule) {
            parser.ruleNames[symbol]
        } else {
            parser.vocabulary.getSymbolicName(symbol)
        }
}

data class Path(
    val elements: List<PathElement> = listOf(),
    val states: MutableSet<Int> = mutableSetOf(),
) {
    fun followWith(el: PathElement): Path = Path(elements + el, states = this.states.toMutableSet())

    fun toString(parser: Parser): String = elements.joinToString(" > ") { it.toString(parser) }

    override fun equals(other: Any?): Boolean = other is Path && elements == other.elements

    override fun hashCode(): Int = elements.hashCode()
}

/**
 * EXPERIMENTAL. Listener to compute an estimate of the coverage of a grammar by the code examples at your disposal.
 * The idea is to exercise the parser with a series of code examples and the listener attached. After processing all
 * the examples, we can ask the listener to compute an estimate of the percentage of the grammar that's covered by those
 * examples.
 *
 * USAGE
 *
 * In pseudo-Kotlin:
 * ```
 * coverageListener = CoverageListener()
 * for example in examples:
 *     parser = createParser(example)
 *     coverageListener.listenTo(parser)
 *     parser.sourceFile()
 * coverage = coverageListener.percentage()
 * last10UncoveredPaths = coverageListener.uncoveredPaths(10)
 * ```
 * LIMITATIONS
 * * The estimate that this computes is incorrect. Due to how ANTLR works and the integration points that it offers,
 * we can only compute a percentage considering the rules that the examples actually used. If there is a rule that, in
 * its ramifications, consists in, say, 50% of the grammar, but no example uses that rule, then it won't be considered
 * in the computation of the coverage. So, the computed coverage percentage is more likely to be somewhat correct if
 * the examples cover a reasonable subset of the grammar.
 * * A single instance of this listener is not thread safe. You cannot attach it to multiple parsers that run in
 * parallel.
 *
 * HOW IT WORKS
 *
 * At each decision/branching point that the parser encounters, we record the paths that are possible and those that
 * are actually taken, and compute the ratio between the two.
 *
 * HOW IT SHOULD WORK
 *
 * It would be nicer if ANTLR allowed to intercept state changes, then we could compute a more precise estimate with
 * (number of activated states / total number of states). Even though the number of states does not correlate completely
 * with the number of rules and alternatives, as parsing certain patterns requires more states than other patterns with
 * similar complexity, it would still be a better measure than what we have now.
 */
open class CoverageListener(
    var parser: Parser? = null,
    val expandUncoveredPaths: Boolean = true,
) : ParseTreeListener {
    val paths = mutableMapOf<Path, Boolean>()
    val pathStack = mutableStackOf<Path>()

    override fun visitTerminal(node: TerminalNode) {
        val path = pathStack.pop().followWith(PathElement(node.symbol.type, false))
        pathStack.push(path)
        paths[path] = true
    }

    override fun visitErrorNode(node: ErrorNode?) {}

    override fun enterEveryRule(ctx: ParserRuleContext) {
        addUncoveredPaths(ctx.invokingState)
        val prevPath = pathStack.peek() ?: Path()
        if (!isLeftRecursive(prevPath, ctx.ruleIndex)) {
            val el = PathElement(ctx.ruleIndex, true)
            paths[prevPath.followWith(el)] = true
            val path = Path(states = prevPath.states).followWith(el)
            pathStack.push(path)
            addUncoveredPaths()
            paths[path] = true
        } else {
            addUncoveredPaths()
        }
    }

    private fun isLeftRecursive(
        path: Path,
        ruleIndex: Int,
    ): Boolean =
        if (path.elements.isNotEmpty()) {
            val last = path.elements.last()
            last.rule && last.symbol == ruleIndex
        } else {
            false
        }

    private fun addUncoveredPaths(state: Int = parser!!.state) {
        val path = pathStack.peek()
        if (path == null || path.states.contains(state) || state < 0) {
            return
        }
        path.states.add(state)
        parser!!.atn.states[state].transitions.forEach {
            when (it) {
                is RuleTransition -> {
                    if (!isLeftRecursive(path, it.ruleIndex)) {
                        addUncoveredPath(PathElement(it.ruleIndex, true), it.target.stateNumber)
                    }
                }

                is AtomTransition -> {
                    addUncoveredPath(PathElement(it.label, false), it.target.stateNumber)
                }

                is SetTransition -> {
                    it.set.intervals.forEach { interval ->
                        for (i in interval.a..interval.b) {
                            addUncoveredPath(PathElement(i, false), it.target.stateNumber)
                        }
                    }
                }

                else -> {
                    addUncoveredPaths(it.target.stateNumber)
                }
            }
        }
    }

    private fun addUncoveredPath(
        element: PathElement,
        nextState: Int,
    ): Boolean {
        val path = pathStack.peek().followWith(element)
        return if (!paths.containsKey(path)) {
            paths[path] = false
            if (expandUncoveredPaths) {
                val newPath =
                    if (element.rule) {
                        Path(listOf(element), path.states)
                    } else {
                        path
                    }
                pathStack.push(newPath)
                try {
                    addUncoveredPaths(nextState)
                } finally {
                    pathStack.pop()
                }
            }
            true
        } else {
            false
        }
    }

    override fun exitEveryRule(ctx: ParserRuleContext) {
        if (pathStack
                .peek()
                ?.elements
                ?.last { it.rule }
                ?.symbol == ctx.ruleIndex
        ) {
            pathStack.pop()
        }
    }

    fun pathStrings(): List<String> = paths.keys.map { it.toString(parser!!) }

    fun uncoveredPaths(maxLength: Int = Int.MAX_VALUE): Collection<Path> =
        paths
            .filterValues {
                !it
            }.keys
            .filter { it.elements.size <= maxLength }

    fun uncoveredPathStrings(maxLength: Int = Int.MAX_VALUE): Collection<String> =
        uncoveredPaths(maxLength).map {
            it.toString(parser!!)
        }

    fun listenTo(parser: Parser) {
        this.parser?.removeParseListener(this)
        this.parser = parser
        parser.addParseListener(this)
    }

    fun percentage() = (1.0 - (this.uncoveredPaths().size.toDouble() / this.paths.size.toDouble())) * 100.0
}
