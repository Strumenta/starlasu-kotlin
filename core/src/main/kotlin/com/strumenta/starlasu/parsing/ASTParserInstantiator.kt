package com.strumenta.starlasu.parsing

import java.io.File

/**
 * This interface is used when need to configure parsers. For example, it can be used for testing, when we need
 * to instantiate a parser with a particular configuration (e.g. specifying include lists of files).
 * With respect to [StarlasuParserInstantiator], this interface can be used to create more generic [ASTParser].instances.
 **/
interface ASTParserInstantiator {
    fun instantiate(fileToParse: File): ASTParser<*>

    fun instantiate(code: String): ASTParser<*>
}
