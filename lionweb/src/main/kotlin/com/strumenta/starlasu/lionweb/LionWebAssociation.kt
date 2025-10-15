package com.strumenta.starlasu.lionweb

/**
 * This is used to map AST classes and Enums which have been generated from a LionWeb language element.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class LionWebAssociation(
    val key: String,
)
