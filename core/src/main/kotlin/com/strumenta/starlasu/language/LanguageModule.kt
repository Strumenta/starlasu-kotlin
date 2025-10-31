package com.strumenta.starlasu.language

import com.strumenta.starlasu.codegen.ASTCodeGenerator
import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.parsing.StarlasuParser

/**
 * This permits to parse code into AST and viceversa going from an AST into code.
 */
class LanguageModule<R : Node>(
    val parser: StarlasuParser<R, *, *, *>,
    val codeGenerator: ASTCodeGenerator<R>,
)
