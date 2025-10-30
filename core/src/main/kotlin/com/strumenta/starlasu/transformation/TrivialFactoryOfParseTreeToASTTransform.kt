package com.strumenta.starlasu.transformation

import com.strumenta.starlasu.mapping.ParseTreeToASTTransformer
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.model.PossiblyNamed
import com.strumenta.starlasu.model.ReferenceByName
import com.strumenta.starlasu.model.children
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNode
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

object TrivialFactoryOfParseTreeToASTTransform {
    fun convertString(
        text: String,
        expectedType: KType,
    ): Any? =
        when (expectedType.classifier) {
            ReferenceByName::class -> {
                ReferenceByName<PossiblyNamed>(name = text)
            }

            String::class -> {
                text
            }

            Int::class -> {
                text.toInt()
            }

            else -> {
                TODO()
            }
        }

    fun convert(
        value: Any?,
        astTransformer: ASTTransformer,
        context: TransformationContext,
        expectedType: KType,
    ): Any? {
        when (value) {
            is Token -> {
                return convertString(value.text, expectedType)
            }

            is List<*> -> {
                return value.map { convert(it, astTransformer, context, expectedType.arguments[0].type!!) }
            }

            is ParserRuleContext -> {
                return when (expectedType) {
                    String::class.createType(), String::class.createType(nullable = true) -> {
                        value.text
                    }

                    else -> {
                        astTransformer.transform(value, context)
                    }
                }
            }

            null -> {
                return null
            }

            is TerminalNode -> {
                return convertString(value.text, expectedType)
            }

            else -> TODO("value $value (${value.javaClass})")
        }
    }

    inline fun <S : RuleContext, reified T : Node> trivialTransform(
        vararg nameConversions: Pair<String, String>,
    ): (
        S,
        TransformationContext,
        ASTTransformer,
    ) -> T? =
        { parseTreeNode, context, astTransformer ->
            val constructor = T::class.preferredConstructor()
            val args: Array<Any?> =
                constructor.parameters
                    .map {
                        val parameterName = it.name
                        val searchedName = nameConversions.find { it.second == parameterName }?.first ?: parameterName
                        val parseTreeMember =
                            parseTreeNode.javaClass.kotlin.memberProperties
                                .find { it.name == searchedName }
                        if (parseTreeMember == null) {
                            val method =
                                parseTreeNode.javaClass.kotlin.memberFunctions.find {
                                    it.name == searchedName && it.parameters.size == 1
                                }
                            if (method == null) {
                                TODO(
                                    "Unable to convert $parameterName (looking for $searchedName in " +
                                        "${parseTreeNode.javaClass})",
                                )
                            } else {
                                val value = method.call(parseTreeNode)
                                convert(value, astTransformer, context, it.type)
                            }
                        } else {
                            val value = parseTreeMember.get(parseTreeNode)
                            convert(value, astTransformer, context, it.type)
                        }
                    }.toTypedArray()
            try {
                val instance = constructor.call(*args)
                instance.children.forEach { it.parent = instance }
                instance
            } catch (e: java.lang.IllegalArgumentException) {
                throw java.lang.RuntimeException(
                    "Failure while invoking constructor $constructor with args: " +
                        args.joinToString(",") { "$it (${it?.javaClass})" },
                    e,
                )
            }
        }
}

inline fun <reified S : RuleContext, reified T : Node> ASTTransformer.registerTrivialPTtoASTConversion(
    vararg nameConversions: Pair<String, String>,
) {
    this.registerRule(
        S::class,
        TrivialFactoryOfParseTreeToASTTransform.trivialTransform<S, T>(*nameConversions),
    )
}

inline fun <reified S : RuleContext, reified T : Node> ParseTreeToASTTransformer.registerTrivialPTtoASTConversion(
    vararg nameConversions: Pair<KCallable<*>, KCallable<*>>,
) = this.registerTrivialPTtoASTConversion<S, T>(
    *nameConversions
        .map { it.first.name to it.second.name }
        .toTypedArray(),
)

inline fun <reified S : RuleContext, reified T : Node> ParseTreeToASTTransformer.unwrap(wrappingMember: KCallable<*>) {
    this.registerRule(S::class) { parseTreeNode, context ->
        val wrapped = wrappingMember.call(parseTreeNode)
        transform(wrapped, context) as T?
    }
}

fun <T : Any> KClass<T>.preferredConstructor(): KFunction<T> {
    val constructors = this.constructors
    return if (constructors.size != 1) {
        if (this.primaryConstructor != null) {
            this.primaryConstructor!!
        } else {
            throw RuntimeException(
                "Node Factories support only classes with exactly one constructor or a " +
                    "primary constructor. Class ${this.qualifiedName} has ${constructors.size}",
            )
        }
    } else {
        constructors.first()
    }
}
