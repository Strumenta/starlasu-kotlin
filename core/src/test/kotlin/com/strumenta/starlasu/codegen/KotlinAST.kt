package com.strumenta.starlasu.codegen

import com.strumenta.starlasu.model.Named
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.ReferenceByName

// This file contains the AST definition for the Kotlin language
// It is partially based on the specifications https://kotlinlang.org/docs/reference/grammar.html
// This could be potentially moved to a separate project in the future

data class KCompilationUnit(
    var packageDecl: KPackageDecl? = null,
    val imports: MutableList<KImport> = mutableListOf(),
    val elements: MutableList<KTopLevelDeclaration> = mutableListOf(),
) : Node()

data class KImport(
    val imported: String,
) : Node()

data class KPackageDecl(
    val name: String,
) : Node()

sealed class KTopLevelDeclaration : Node()

data class KClassDeclaration(
    override val name: String,
    var dataClass: Boolean = false,
    var isSealed: Boolean = false,
    var isAbstract: Boolean = false,
    var primaryConstructor: KPrimaryConstructor = KPrimaryConstructor(),
    val superTypes: MutableList<KSuperTypeInvocation> = mutableListOf(),
) : KTopLevelDeclaration(),
    Named

data class KTopLevelFunction(
    override val name: String,
    val params: MutableList<KParameterDeclaration> = mutableListOf<KParameterDeclaration>(),
    val returnType: KType? = null,
    val body: MutableList<KStatement> = mutableListOf(),
) : KTopLevelDeclaration(),
    Named

data class KExtensionMethod(
    var extendedClass: KName,
    override var name: String,
    val params: MutableList<KParameterDeclaration> = mutableListOf<KParameterDeclaration>(),
    var returnType: KType? = null,
    val body: MutableList<KStatement> = mutableListOf(),
) : KTopLevelDeclaration(),
    Named

sealed class KStatement : Node()

data class KExpressionStatement(
    val expression: KExpression,
) : KStatement()

data class KReturnStatement(
    val value: KExpression? = null,
) : KStatement()

data class KWhenStatement(
    var subject: KExpression? = null,
    val whenClauses: MutableList<KWhenClause> = mutableListOf(),
    var elseClause: KElseClause? = null,
) : KExpression()

data class KWhenClause(
    var condition: KExpression,
    var body: KStatement,
) : Node()

data class KElseClause(
    var body: KStatement,
) : Node()

data class KThrowStatement(
    var exception: KExpression,
) : KStatement()

sealed class KExpression : Node()

class KThisExpression : KExpression()

data class KReferenceExpr(
    var symbol: String,
) : KExpression()

data class KStringLiteral(
    var value: String,
) : KExpression()

data class KIntLiteral(
    var value: Int,
) : KExpression()

data class KPlaceholderExpr(
    var name: String? = null,
) : KExpression()

data class KUniIsExpression(
    var ktype: KType,
) : KExpression()

data class KMethodCallExpression(
    var qualifier: KExpression,
    var method: ReferenceByName<KMethodSymbol>,
    val args: MutableList<KExpression> = mutableListOf(),
    val lambda: KLambda? = null,
) : KExpression()

data class KFieldAccessExpr(
    var qualifier: KExpression,
    var field: String,
) : KExpression()

data class KLambda(
    val params: MutableList<KLambdaParamDecl> = mutableListOf(),
    val body: MutableList<KStatement> = mutableListOf(),
) : KExpression()

data class KLambdaParamDecl(
    override val name: String,
) : Node(),
    Named

data class KParameterValue(
    val value: KExpression,
    val name: String? = null,
) : Node()

data class KInstantiationExpression(
    var type: KType,
    val args: MutableList<KParameterValue> = mutableListOf(),
) : KExpression()

interface KFunctionSymbol : Named

interface KMethodSymbol : Named

data class KFunctionCall(
    val function: ReferenceByName<KFunctionSymbol>,
    val args: MutableList<KParameterValue> = mutableListOf(),
) : KExpression()

sealed class KName : Node() {
    companion object {
        fun fromParts(
            firstPart: String,
            vararg otherParts: String,
        ): KName {
            fun helper(parts: List<String>): KName {
                return when (parts.size) {
                    1 -> return KSimpleName(parts.first())
                    else -> return KQualifiedName(helper(parts.dropLast(1)), parts.last())
                }
            }

            val allParts = listOf(firstPart) + otherParts
            return helper(allParts)
        }
    }
}

data class KSimpleName(
    val name: String,
) : KName()

data class KQualifiedName(
    val container: KName,
    val name: String,
) : KName()

data class KPrimaryConstructor(
    val params: MutableList<KParameterDeclaration> = mutableListOf(),
) : Node()

enum class KPersistence {
    VAL,
    VAR,
    NONE,
}

data class KParameterDeclaration(
    override val name: String,
    override val type: KType,
    val persistemce: KPersistence = KPersistence.NONE,
) : Node(),
    Named,
    KTyped

interface KTyped {
    val type: KType
}

sealed class KType : Node()

data class KRefType(
    val name: String,
    val args: MutableList<KType> = mutableListOf<KType>(),
) : KType()

data class KOptionalType(
    val base: KType,
) : KType()

data class KSuperTypeInvocation(
    val name: String,
) : Node()

data class KObjectDeclaration(
    override val name: String,
) : KTopLevelDeclaration(),
    Named

data class KFunctionDeclaration(
    override val name: String,
) : KTopLevelDeclaration(),
    Named
