# Module starlasu-core

The foundation of Starlasu Kotlin: the AST model (`Node`, positions, references), traversal and transformation of
ASTs, and the infrastructure for building ANTLR-based parsers that produce ASTs.

# Package com.strumenta.starlasu.model

The AST model. Extend `Node` (an alias of `BaseASTNode`) to define AST classes; their properties are discovered via
reflection and classified as attributes, containments or references (`ReferenceByName`). Also contains `Position`,
`Point` and `Source`, origin/destination tracking, naming (`Named`, `PossiblyNamed`), common node markers
(`Expression`, `Statement`, ...), and utilities to process and debug-print trees.

# Package com.strumenta.starlasu.traversing

Functions to walk an AST: depth-first, leaves-first, ancestors, descendants of a given type, and nodes within a
`Position`.

# Package com.strumenta.starlasu.transformation

`ASTTransformer`, a rule-based tree-to-tree transformation, plus the placeholder origins (`MissingASTTransformation`,
`FailingASTTransformation`) used to mark nodes that could not be transformed.

# Package com.strumenta.starlasu.mapping

`ParseTreeToASTTransformer`, an `ASTTransformer` specialized for mapping ANTLR parse trees to ASTs, and helpers such as
`translateCasted` and `translateList`.

# Package com.strumenta.starlasu.parsing

ANTLR-based lexing and parsing: `StarlasuParser` and `StarlasuANTLRLexer` base classes, tokens and token categories,
`ParsingResult` and `LexingResult`, and utilities to work with ANTLR parse trees.

# Package com.strumenta.starlasu.parsing.coverage

`CoverageListener`, an ANTLR parse listener that estimates how much of a grammar is exercised by a set of inputs.

# Package com.strumenta.starlasu.validation

`Issue` (lexical, syntactic, semantic or translation problems, with a severity and an optional position) and `Result`.

# Package com.strumenta.starlasu.ids

Assignment of IDs to AST nodes, as required for example by LionWeb. `NodeIdProvider` defines the policy;
`StructuralNodeIdProvider` derives IDs from the source and the node's location in the tree, `DeclarativeNodeIdProvider`
from per-node-type `idFor` rules, and `CommonNodeIdProvider` combines the two.

# Package com.strumenta.starlasu.codegen

Generation of source code from an AST: `ASTCodeGenerator` and `PrinterOutput`. Comments and original formatting are
not preserved.

# Package com.strumenta.starlasu.testing

Assertions for tests: comparing ASTs and parsing results, checking that references are resolved, and checking
tokenization.

# Package com.strumenta.starlasu.language

`KolasuLanguage` and the related metamodel classes. Deprecated: use LionWeb's `Language` instead.

# Package com.strumenta.starlasu.semantics

Deprecated symbol resolution and type computation. Use the `starlasu-semantics` module instead.

# Package com.strumenta.starlasu.symbolresolution

Deprecated `LocalSymbolResolver`. Use the `starlasu-semantics` module instead.

# Package com.strumenta.starlasu.utils

Small general-purpose helpers (memoization, sorting classes by hierarchy, string utilities).

# Package com.strumenta.kolasu.interning

Canonicalization of frequently created values (`Point`, `TokenCategory`) to reduce memory usage.
