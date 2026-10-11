# Module starlasu-semantics

Symbol resolution for Starlasu ASTs: scopes, symbol descriptions and the resolver that links `ReferenceByName`
instances to their targets.

A `SymbolResolver` walks the references of a node and asks a `ScopeProvider` for the `ScopeDescription` to resolve
each one against. A scope maps names either to local nodes (setting `ReferenceByName.referred`) or to identifiers of
external symbols (setting `ReferenceByName.identifier`), and falls back to its parent scope.

Symbols defined in other files can be captured as `SymbolDescription`s by a `SymbolProvider`, stored in a
`SymbolRepository`, and then referenced from scopes.

This module supersedes the deprecated `com.strumenta.starlasu.semantics` package in `starlasu-core`.

# Package com.strumenta.starlasu.semantics.scope.description

`ScopeDescription`, the set of names visible at a point of the AST, and the `scope { }` builder.

# Package com.strumenta.starlasu.semantics.scope.provider

The `ScopeProvider` interface, which returns the scope to use for a given reference property of a node.

# Package com.strumenta.starlasu.semantics.scope.provider.declarative

`DeclarativeScopeProvider`, a `ScopeProvider` defined through `scopeFor` rules keyed by node type and reference.

# Package com.strumenta.starlasu.semantics.symbol.description

`SymbolDescription` and the `ValueDescription` hierarchy: a serializable summary of a symbol (name, identifier, types
and selected properties).

# Package com.strumenta.starlasu.semantics.symbol.provider

The `SymbolProvider` interface, which produces the `SymbolDescription` of a node.

# Package com.strumenta.starlasu.semantics.symbol.provider.declarative

`DeclarativeSymbolProvider`, a `SymbolProvider` defined through `symbolFor` rules keyed by node type.

# Package com.strumenta.starlasu.semantics.symbol.importer

`SymbolImporter`, which extracts symbol descriptions from an AST and stores them in a `SymbolRepository`.

# Package com.strumenta.starlasu.semantics.symbol.repository

The `SymbolRepository` interface for storing and looking up symbol descriptions.

# Package com.strumenta.starlasu.semantics.symbol.resolver

`SymbolResolver`, which resolves the `ReferenceByName` properties of a node (optionally of its whole subtree) using a
`ScopeProvider`.
