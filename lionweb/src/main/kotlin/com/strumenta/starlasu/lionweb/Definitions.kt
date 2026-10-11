package com.strumenta.starlasu.lionweb

import io.lionweb.language.Language
import io.lionweb.model.Node
import kotlin.reflect.KClass

/** A LionWeb language. */
typealias LWLanguage = Language
typealias EnumKClass = KClass<out Enum<*>>

/** A Starlasu AST node. */
typealias SNode = com.strumenta.starlasu.model.ASTNode

/** A LionWeb node. */
typealias LWNode = Node
