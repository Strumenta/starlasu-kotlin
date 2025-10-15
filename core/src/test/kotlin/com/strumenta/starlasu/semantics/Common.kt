package com.strumenta.starlasu.semantics

import com.strumenta.starlasu.model.Expression
import com.strumenta.starlasu.model.Named
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.model.Statement

internal data class CompilationUnit(
    var content: MutableList<TypeDecl> = mutableListOf(),
) : Node()

internal sealed class SymbolNode(
    override val name: String,
) : Node(),
    Named

internal open class TypeDecl(
    override val name: String,
) : SymbolNode(name)

internal data class ClassDecl(
    override val name: String,
    var superclass: ReferenceByName<ClassDecl>? = null,
    var features: MutableList<FeatureDecl> = mutableListOf(),
    var operations: MutableList<OperationDecl> = mutableListOf(),
) : TypeDecl(name)

internal data class FeatureDecl(
    override val name: String,
    var type: ReferenceByName<TypeDecl>,
) : SymbolNode(name)

internal data class OperationDecl(
    override val name: String,
    var parameters: MutableList<ParameterDecl> = mutableListOf(),
    var statements: MutableList<StmtNode> = mutableListOf(),
    var returns: ReferenceByName<TypeDecl>? = null,
) : SymbolNode(name)

internal data class ParameterDecl(
    override val name: String,
    var type: ReferenceByName<TypeDecl>,
) : SymbolNode(name)

internal sealed class StmtNode :
    Node(),
    Statement

internal data class DeclarationStmt(
    val variable: Variable,
    var value: ExprNode? = null,
) : StmtNode()

internal data class Variable(
    override val name: String,
    var type: ReferenceByName<TypeDecl>,
) : SymbolNode(name)

internal data class AssignmentStmt(
    var lhs: ExprNode,
    var rhs: ExprNode,
) : StmtNode()

internal sealed class ExprNode :
    Node(),
    Expression

internal data class RefExpr(
    var context: ExprNode? = null,
    var symbol: ReferenceByName<SymbolNode>,
) : ExprNode()

internal data class CallExpr(
    var operation: ReferenceByName<OperationDecl>,
    var arguments: MutableList<ExprNode> = mutableListOf(),
) : ExprNode()

internal data class NewExpr(
    var clazz: ReferenceByName<ClassDecl>,
) : ExprNode()
