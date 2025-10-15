package com.strumenta.starlasu.semantics

import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.model.assignParents
import com.strumenta.starlasu.model.processNodesOfType
import com.strumenta.starlasu.testing.assertReferencesNotResolved
import com.strumenta.starlasu.testing.assertReferencesResolved
import com.strumenta.starlasu.traversing.findAncestorOfType
import com.strumenta.starlasu.traversing.walk
import org.junit.Test
import kotlin.test.assertEquals

class SemanticsTest {
    @Test
    fun testSymbolResolution() {
        getCompilationUnit()
            .apply { assertReferencesNotResolved() }
            .apply { getSemantics().symbolResolver.resolve(this) }
            .apply { assertReferencesResolved() }
    }

    @Test
    fun testTypeComputation() {
        val compilationUnit = getCompilationUnit()
        val featureDeclaration =
            compilationUnit
                .walk()
                .filterIsInstance<FeatureDecl>()
                .find { featureDeclaration -> featureDeclaration.name == "feature_0" }!!
        assertEquals(
            compilationUnit
                .walk()
                .filterIsInstance<ClassDecl>()
                .find { classDeclaration -> classDeclaration.name == "class_1" },
            getSemantics().typeComputer.typeFor(featureDeclaration),
        )
    }

    private fun getCompilationUnit() =
        CompilationUnit(
            content =
                mutableListOf(
                    ClassDecl(
                        name = "class_0",
                        features =
                            mutableListOf(
                                FeatureDecl(
                                    name = "feature_0",
                                    type = ReferenceByName(name = "class_1"),
                                ),
                            ),
                        operations =
                            mutableListOf(
                                OperationDecl(
                                    name = "operation_0",
                                    returns = ReferenceByName(name = "class_0"),
                                    statements =
                                        mutableListOf(
                                            AssignmentStmt(
                                                lhs =
                                                    RefExpr(
                                                        context =
                                                            CallExpr(
                                                                operation = ReferenceByName(name = "operation_0"),
                                                            ),
                                                        symbol = ReferenceByName(name = "feature_0"),
                                                    ),
                                                rhs =
                                                    RefExpr(
                                                        context =
                                                            CallExpr(
                                                                operation = ReferenceByName(name = "operation_0"),
                                                            ),
                                                        symbol = ReferenceByName(name = "feature_0"),
                                                    ),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                    ClassDecl("class_1"),
                ),
        ).apply { assignParents() }

    private fun getSemantics() =
        semantics {
            symbolResolver {
                // scope resolution rules
                scopeFor(ClassDecl::superclass) {
                    scope {
                        it
                            .findAncestorOfType(CompilationUnit::class.java)
                            ?.content
                            ?.filterIsInstance<ClassDecl>()
                            ?.filter { classDecl -> classDecl.name != it.name }
                            ?.forEach(this::define)
                    }
                }
                scopeFor(FeatureDecl::type) {
                    scope { it.findAncestorOfType(CompilationUnit::class.java)?.content?.forEach(this::define) }
                }
                scopeFor(OperationDecl::returns) {
                    scope { it.findAncestorOfType(CompilationUnit::class.java)?.content?.forEach(this::define) }
                }
                scopeFor(ParameterDecl::type) {
                    scope { it.findAncestorOfType(CompilationUnit::class.java)?.content?.forEach(this::define) }
                }
                scopeFor(Variable::type) {
                    scope { it.findAncestorOfType(CompilationUnit::class.java)?.content?.forEach(this::define) }
                }
                scopeFor(RefExpr::symbol) {
                    if (it.context != null) {
                        symbolResolver.scopeFrom(typeComputer.typeFor(it.context))
                    } else {
                        scope {
                            it
                                .findAncestorOfType(CompilationUnit::class.java)
                                ?.processNodesOfType(SymbolNode::class.java, this::define)
                        }
                    }
                }
                scopeFor(CallExpr::operation) {
                    scope {
                        it
                            .findAncestorOfType(ClassDecl::class.java)
                            ?.operations
                            ?.forEach(this::define)
                    }
                }
                // scope construction rules
                scopeFrom(ClassDecl::class) {
                    scope {
                        it.operations.forEach(this::define)
                        it.features.forEach(this::define)
                        parent =
                            symbolResolver.scopeFrom(
                                it.superclass
                                    ?.apply {
                                        if (!this.resolved) {
                                            symbolResolver.resolve(ClassDecl::superclass, it)
                                        }
                                    }?.referred,
                            )
                    }
                }
            }
            typeComputer {
                // type computation rules
                typeFor(RefExpr::class) {
                    if (!it.symbol.resolved) {
                        symbolResolver.resolve(RefExpr::symbol, it)
                    }
                    typeComputer.typeFor(it.symbol.referred)
                }
                typeFor(CallExpr::class) {
                    if (!it.operation.resolved) {
                        symbolResolver.resolve(CallExpr::operation, it)
                    }
                    typeComputer.typeFor(it.operation.referred)
                }
                typeFor(Variable::class) {
                    if (!it.type.resolved) {
                        symbolResolver.resolve(Variable::type, it)
                    }
                    typeComputer.typeFor(it.type.referred)
                }
                typeFor(ParameterDecl::class) {
                    if (!it.type.resolved) {
                        symbolResolver.resolve(ParameterDecl::type, it)
                    }
                    typeComputer.typeFor(it.type.referred)
                }
                typeFor(OperationDecl::class) {
                    it.returns
                        ?.takeIf { returns -> !returns.resolved }
                        ?.let { _ -> symbolResolver.resolve(OperationDecl::returns, it) }
                    typeComputer.typeFor(it.returns?.referred)
                }
                typeFor(FeatureDecl::class) {
                    if (!it.type.resolved) {
                        symbolResolver.resolve(FeatureDecl::type, it)
                    }
                    typeComputer.typeFor(it.type.referred)
                }
            }
        }
}
