package com.strumenta.starlasu.lionweb

import com.strumenta.starlasu.base.v2.ASTLanguage
import io.lionweb.language.PrimitiveType

// These will be unnecessary once https://github.com/LionWeb-io/lionweb-java/issues/293 is fixed

val ASTLanguage.position: PrimitiveType
    get() = this.requirePrimitiveTypeByName("Position")

val ASTLanguage.char: PrimitiveType
    get() = this.requirePrimitiveTypeByName("Char")

val ASTLanguage.tokensList: PrimitiveType
    get() = this.requirePrimitiveTypeByName("TokensList")
