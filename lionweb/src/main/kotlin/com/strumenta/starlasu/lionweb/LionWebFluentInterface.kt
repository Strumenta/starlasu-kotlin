package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.model.Multiplicity
import io.lionweb.language.Concept
import io.lionweb.language.IKeyed
import io.lionweb.model.Node
import io.lionweb.model.impl.DynamicNode
import kotlin.random.Random

val Multiplicity.optional
    get() = this == Multiplicity.OPTIONAL || this == Multiplicity.MANY

val Multiplicity.multiple
    get() = this == Multiplicity.MANY

/**
 * Create a Dynamic Node with the given Concept and a random node ID.
 */
fun Concept.dynamicNode(nodeId: String = "node-id-rand-${Random.nextInt()}"): DynamicNode = DynamicNode(nodeId, this)

fun String.lwIDCleanedVersion(): String =
    this
        .replace(".", "_")
        .replace(" ", "_")
        .replace("/", "_")

private fun Node.idPrefixForContainedElements(): String = this.id!!.removePrefix("language-").removeSuffix("-id")

private fun IKeyed<*>.keyPrefixForContainedElements(): String =
    this.key!!.removePrefix("language-").removeSuffix("-key")

fun Node.idForContainedElement(containedElementName: String): String =
    "${this.idPrefixForContainedElements()}-${containedElementName.lwIDCleanedVersion()}-id"

fun IKeyed<*>.keyForContainedElement(containedElementName: String): String =
    "${this.keyPrefixForContainedElements()}-${containedElementName.lwIDCleanedVersion()}-key"
