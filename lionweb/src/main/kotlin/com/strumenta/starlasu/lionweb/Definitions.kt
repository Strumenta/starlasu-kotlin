package com.strumenta.starlasu.lionweb

import io.lionweb.language.Language
import io.lionweb.model.Node
import kotlin.reflect.KClass

typealias LWLanguage = Language
typealias EnumKClass = KClass<out Enum<*>>
typealias SNode = com.strumenta.starlasu.model.ASTNode
typealias LWNode = Node

/**
 * Kolasu 1.5 name of [SNode]. Kept only to ease the migration of 1.5 language modules.
 */
@Deprecated("Renamed in Starlasu 1.7", ReplaceWith("SNode"))
typealias KNode = SNode
