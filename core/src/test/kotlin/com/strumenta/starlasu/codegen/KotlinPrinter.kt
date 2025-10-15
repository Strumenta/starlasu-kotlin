package com.strumenta.starlasu.codegen

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.transformation.PlaceholderASTTransformation

class KotlinPrinter : ASTCodeGenerator<KCompilationUnit>() {
    override val placeholderNodePrinter: NodePrinter
        get() =
            NodePrinter { output: PrinterOutput, ast: Node ->
                val placeholder = ast.origin as PlaceholderASTTransformation
                output.print("/* ${placeholder.message} */")
            }

    override fun registerRecordPrinters() {
        recordPrinter<KCompilationUnit> {
            print(it.packageDecl)
            printList(prefix = "", it.imports, "\n", false, "")
            if (it.imports.isNotEmpty()) {
                println()
            }
            it.elements.forEach { print(it) }
        }
        recordPrinter<KPackageDecl> {
            print("package ")
            print(it.name)
            println()
            println()
        }
        recordPrinter<KImport> {
            print("import ")
            print(it.imported)
            println()
        }
        recordPrinter<KClassDeclaration> {
            printFlag(it.dataClass, "data ")
            printFlag(it.isAbstract, "abstract ")
            printFlag(it.isSealed, "sealed ")
            print("class ${it.name}")
            print(it.primaryConstructor)
            if (it.superTypes.isNotEmpty()) {
                print(": ")
                printList(it.superTypes)
                print(" ")
            }
            println(" {")
            println("}")
            println()
        }
        recordPrinter<KPrimaryConstructor> {
            printList("(", it.params, ")")
        }
        recordPrinter<KSuperTypeInvocation> {
            print(it.name)
            print("()")
        }
        recordPrinter<KParameterDeclaration> {
            when (it.persistemce) {
                KPersistence.VAR -> print("var ")
                KPersistence.VAL -> print("val ")
                else -> Unit
            }
            print(it.name)
            print(": ")
            print(it.type)
        }
        recordPrinter<KRefType> {
            print(it.name)
            printList("<", it.args, ">")
        }
        recordPrinter<KOptionalType> {
            print(it.base)
            print("?")
        }
        recordPrinter<KExtensionMethod> {
            print("fun ")
            print(it.extendedClass)
            print(".")
            print(it.name)
            printList("(", it.params, ")", printEvenIfEmpty = true)
            if (it.returnType != null) {
                print(": ")
                print(it.returnType)
            }
            println(" {")
            indent()
            printList(it.body, "\n")
            dedent()
            println("}")
            println()
        }
        recordPrinter<KQualifiedName> {
            print(it.container)
            print(".")
            print(it.name)
        }
        recordPrinter<KSimpleName> {
            print(it.name)
        }
        recordPrinter<KExpressionStatement> {
            print(it.expression)
            println()
        }
        recordPrinter<KFunctionCall> {
            print(it.function.name)
            printList("(", it.args, ")", printEvenIfEmpty = true)
        }
        recordPrinter<KReturnStatement> {
            print("return")
            print(it.value, prefix = " ")
            println()
        }
        recordPrinter<KWhenStatement> {
            print("when ")
            print(it.subject, "(", ") ")
            println("{")
            indent()
            printList(it.whenClauses, "")
            print(it.elseClause)
            dedent()
            println("}")
        }
        recordPrinter<KWhenClause> {
            print(it.condition)
            print(" -> ")
            print(it.body)
        }
        recordPrinter<KElseClause> {
            print("else -> ")
            print(it.body)
        }
        recordPrinter<KThisExpression> {
            print("this")
        }
        recordPrinter<KUniIsExpression> {
            print("is ")
            print(it.ktype)
        }
        recordPrinter<KMethodCallExpression> {
            print(it.qualifier)
            print(".")
            print(it.method.name)
            print("(")
            printList(it.args)
            print(")")
            print(it.lambda, " ")
        }
        recordPrinter<KInstantiationExpression> {
            print(it.type)
            print("(")
            printList(it.args)
            print(")")
        }
        recordPrinter<KThrowStatement> {
            print("throw ")
            println(it.exception)
        }
        recordPrinter<KFieldAccessExpr> {
            print(it.qualifier)
            print(".")
            print(it.field)
        }
        recordPrinter<KParameterValue> {
            print(it.name, "", "=")
            print(it.value)
        }
        recordPrinter<KLambda> {
            print("{")
            indent()
            printList(it.body, separator = "")
            dedent()
            println("}")
        }
        recordPrinter<KReferenceExpr> {
            print(it.symbol)
        }
        recordPrinter<KStringLiteral> {
            print('"')
            print(it.value)
            print('"')
        }
        recordPrinter<KIntLiteral> {
            print(it.value)
        }
        recordPrinter<KFunctionDeclaration> {
            print("fun ")
            print(it.name)
            print("(")
            // TODO print parameters
            println(") {")
            indent()
            // TODO print body
            dedent()
            println("}")
        }
    }

    fun printToString(expression: KExpression): String {
        val o = PrinterOutput(nodePrinters, nodePrinterOverrider)
        o.print(expression)
        return o.text()
    }
}
