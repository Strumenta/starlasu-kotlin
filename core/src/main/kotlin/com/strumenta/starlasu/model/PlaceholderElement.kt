package com.strumenta.starlasu.model

/**
 * PlaceholderElements can be used to represent elements in code matchers templates and code templates. They represent
 * variable elements in ASTs.
 *
 * Code matchers recognize portions of ASTs, while code templates generates portions of ATS.
 * For example, in a code matcher we could use a PlaceholderElement to indicate that a certain part of an AST could vary
 * and that we want to recognize that value. We could have an AST of this type:
 * SumExpr(PlaceholderElement("left"), IntLiteral(1)). A code matcher could recognize all of these expressions as matching:
 * SumExpr(IntLiteral(2), IntLiteral(1)), SumExpr(ReferenceExpression("foo"), IntLiteral(1)), or
 * SumExpr(MultiplicationExpr(IntLiteral(2), IntLiteral(3)), IntLiteral(1)).
 * What would vary among these cases would be the value recognized for the PlaceholderElement.
 *
 * Conversely, in a code template the PlaceholderElement indicates where to insert parameters provided to populate the
 * template.
 */
interface PlaceholderElement : CommonElement {
    var placeholderName: String?

    @property:Internal
    val multiplePlaceholderElement: Boolean

    /**
     * Return true if the node can be represented by this PlaceholderElement.
     * For example, a certain PlaceholderElement could be used only to match expressions or statements.
     * In case this is a multiple quoted element, and it is used to match many elements, each element
     * should be passed to this method. For example, if a certain PlaceholderElement is used to match a sequence of three
     * statements S1, S2, and S3, all of them should be passed, one by one, as parameters of this method.
     */
    fun applicableTo(node: Node): Boolean = true
}
