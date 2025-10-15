package com.strumenta.starlasu.model

import com.strumenta.starlasu.parsing.ParsingResult
import java.lang.reflect.ParameterizedType
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

/**
 * Influence what and how we print the debug information.
 */
data class DebugPrintConfiguration constructor(
    var skipEmptyCollections: Boolean = false,
    var skipNull: Boolean = false,
    var forceShowPosition: Boolean = false,
    val hide: MutableList<String> = mutableListOf(),
    var indentBlock: String = "  ",
)

private fun Node.showSingleAttribute(
    indent: String,
    sb: StringBuilder,
    propertyName: String,
    value: Any?,
    configuration: DebugPrintConfiguration = DebugPrintConfiguration(),
) {
    sb.append("$indent${configuration.indentBlock}$propertyName = ${value}\n")
}

fun Any?.debugPrint(
    indent: String = "",
    configuration: DebugPrintConfiguration = DebugPrintConfiguration(),
): String {
    val sb = StringBuilder()
    sb.append("$indent${this}\n")
    return sb.toString()
}

fun <N : Node> ParsingResult<N>.debugPrint(
    indent: String = "",
    configuration: DebugPrintConfiguration = DebugPrintConfiguration(),
): String {
    val indentBlock = configuration.indentBlock
    val sb = StringBuilder()
    sb.append("${indent}Result {\n")
    sb.append("${indent}${indentBlock}issues= [\n")
    sb.append("${indent}$indentBlock]\n")
    if (this.root == null) {
        sb.append("${indent}${indentBlock}root = null\n")
    } else {
        sb.append("${indent}${indentBlock}root = [\n")
        sb.append(this.root.debugPrint(indent + indentBlock + indentBlock, configuration = configuration))
        sb.append("${indent}$indentBlock]\n")
    }
    sb.append("$indent}\n")
    return sb.toString()
}

@JvmOverloads
fun Node.debugPrint(
    indent: String = "",
    configuration: DebugPrintConfiguration = DebugPrintConfiguration(),
): String {
    val indentBlock = configuration.indentBlock
    val sb = StringBuilder()
    if (this.relevantMemberProperties(withPosition = configuration.forceShowPosition).isEmpty()) {
        sb.append("$indent${this.javaClass.simpleName}\n")
    } else {
        sb.append("$indent${this.javaClass.simpleName} {\n")
        this.relevantMemberProperties(withPosition = configuration.forceShowPosition).forEach { property ->
            if (configuration.hide.contains(property.name)) {
                // skipping
            } else {
                val mt = property.returnType.javaType
                if (mt is ParameterizedType && mt.rawType == List::class.java) {
                    property.isAccessible = true
                    if (property.get(this) == null && !configuration.skipNull) {
                        sb.append("$indent$indentBlock${property.name} = null")
                    } else {
                        val value = property.get(this) as List<*>
                        if (value.isEmpty()) {
                            if (configuration.skipEmptyCollections) {
                                // nothing to do
                            } else {
                                sb.append("$indent$indentBlock${property.name} = []\n")
                            }
                        } else {
                            val paramType = mt.actualTypeArguments[0]
                            if (paramType is Class<*> && paramType.kotlin.isANode()) {
                                sb.append("$indent$indentBlock${property.name} = [\n")
                                (value as List<Node>).forEach {
                                    sb.append(
                                        it.debugPrint(
                                            indent + indentBlock + indentBlock,
                                            configuration,
                                        ),
                                    )
                                }
                                sb.append("$indent$indentBlock]\n")
                            } else {
                                sb.append("$indent$indentBlock${property.name} = [\n")
                                value.forEach {
                                    sb.append(
                                        it?.debugPrint(
                                            indent + indentBlock + indentBlock,
                                            configuration,
                                        ),
                                    )
                                }
                                sb.append("$indent$indentBlock]\n")
                            }
                        }
                    }
                } else {
                    property.isAccessible = true
                    val value = property.get(this)
                    if (value == null && configuration.skipNull) {
                        // nothing to do
                    } else {
                        if (value is Node) {
                            sb.append("$indent$indentBlock${property.name} = [\n")
                            sb.append(
                                value.debugPrint(
                                    indent + indentBlock + indentBlock,
                                    configuration,
                                ),
                            )
                            sb.append("$indent$indentBlock]\n")
                        } else {
                            this.showSingleAttribute(indent, sb, property.name, value, configuration)
                        }
                    }
                }
            }
        }
        sb.append("$indent} // ${this.javaClass.simpleName}\n")
    }
    return sb.toString()
}
