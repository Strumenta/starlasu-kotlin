package com.strumenta.starlasu.parsing

import java.io.File

/**
 * This interface is used when we need to configure parsers. For example, it can be used for testing, when we need
 * to instantiate a parser with a particular configuration (e.g., specifying include lists of files).
 * With respect to [ASTParserInstantiator] this interface can be used to create more specific [StarlasuParser] instances.
 */
interface StarlasuParserInstantiator : ASTParserInstantiator {
    override fun instantiate(fileToParse: File): StarlasuParser<*, *, *, *>

    override fun instantiate(code: String): StarlasuParser<*, *, *, *>
}
