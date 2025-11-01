package com.strumenta.starlasu.lwstubs

import com.squareup.kotlinpoet.ClassName
import kotlin.reflect.KClass

val KClass<*>.className : ClassName
    get() = ClassName(this.java.`package`.name, this.java.simpleName)