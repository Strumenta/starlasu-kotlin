package com.strumenta.starlasu.symbolresolution

import com.strumenta.starlasu.model.Node
import com.strumenta.starlasu.validation.Issue
import java.io.Serializable

/**
 * This object performs symbol resolution within a single AST.
 * It is not to be used to perform cross-ASTs symbol resolution.
 */
@Deprecated(message = "Should use Semantics instead")
abstract class LocalSymbolResolver : Serializable {
    /**
     * This will resolve symbols on the given AST. It will set the links in the ReferenceByName found.
     * It will return a list of issues encountered during symbol resolution.
     */
    abstract fun resolveSymbols(root: Node): List<Issue>
}
