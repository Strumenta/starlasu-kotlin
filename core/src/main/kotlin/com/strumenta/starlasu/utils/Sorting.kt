package com.strumenta.starlasu.utils

import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

fun Iterable<KClass<*>>.sortBySubclassesFirst() =
    this.sortedWith { left, right ->
        when {
            left.isSuperclassOf(right) -> 1
            right.isSuperclassOf(left) -> -1
            else -> 0
        }
    }
