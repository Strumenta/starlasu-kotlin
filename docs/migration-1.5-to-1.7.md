# Migrating a language module from Kolasu 1.5 to Starlasu Kotlin 1.7

This guide covers what a language module written against **Kolasu 1.5** (`com.strumenta.kolasu`, Kotlin 1.8,
JVM 1.8) has to change to build against **Starlasu Kotlin 1.7** (`com.strumenta.starlasu`, Kotlin 2.4, JVM 21).
It lists, area by area, what is a pure rename, what changed shape, and what was dropped.

There is no 1.6 to migrate from: that line was internal, never meant to be adopted, and was dropped. Modules on
1.5 move directly to 1.7.

## 1. Toolchain, coordinates and packages

| | Kolasu 1.5 | Starlasu 1.7 |
|---|---|---|
| Kotlin | 1.8 | 2.4 |
| JVM target | 1.8 | 21 |
| ANTLR | 4.9.3 | 4.13.2 (regenerate your parsers) |
| Maven group | `com.strumenta.kolasu` | `com.strumenta.starlasu` |
| Artifacts | `kolasu-core`, `kolasu-semantics`, `kolasu-lionweb`, `kolasu-lionwebrepo-client`, `kolasu-javalib`, `kolasu-codebase`, `kolasu-emf`, `kolasu-lionweb-gen`, `kolasu-lionweb-ksp` | `starlasu-core`, `starlasu-semantics`, `starlasu-lionweb`, `starlasu-lionweb-client`, `starlasu-javalib`, `starlasu-codebase` |
| Root package | `com.strumenta.kolasu` | `com.strumenta.starlasu` |
| LionWeb JVM | `io.lionweb:lionweb-2024.1-core` 1.3.14, `lionweb-2024.1-kotlin-core` / `-kotlin-client` 1.3.9 | same artifacts, all at 1.4.5 (`lionweb-2024.1-core`, `-kotlin-core`, `-client`, `-kotlin-client`) |
| Starlasu-Specs | `com.strumenta.starlasu.specs:starlasuspecs-jvm:0.6.x` | `com.strumenta.starlasu.specs:languages` and `com.strumenta.starlasu.specs:components` 0.7.x |

```kotlin
dependencies {
    implementation("com.strumenta.starlasu:starlasu-core:1.7.5")
    implementation("com.strumenta.starlasu:starlasu-semantics:1.7.5")   // declarative scope providers
    implementation("com.strumenta.starlasu:starlasu-lionweb:1.7.5")     // LionWeb export/import
    implementation("com.strumenta.starlasu:starlasu-lionweb-client:1.7.5") // LionWeb repository client (was lionwebrepo-client)
}
```

Start with a mechanical rename: `com.strumenta.kolasu.` → `com.strumenta.starlasu.` in every import. The only
package that kept its old name is `com.strumenta.kolasu.interning`.

Modules dropped without replacement: `emf` (EMF/Ecore export), `lionweb-gen`, `lionweb-ksp`,
`lionweb-gen-gradle` (the `com.strumenta.kolasu.lionwebgen` Gradle plugin) and `playground`.

## 2. Nodes

`Node` was one class; it is now an interface plus a base class.

| Kolasu 1.5 | Starlasu 1.7 |
|---|---|
| `open class Node` | `interface ASTNode` (what APIs accept and return) and `open class BaseASTNode : ASTNode` (what your node classes extend) |
| — | `typealias Node = BaseASTNode` is kept, so `class Foo : Node()` still compiles |
| `Node()`, `Node(position)`, `Node(origin)` | same constructors on `BaseASTNode` |
| `@ASTRoot`, `@Internal`, `@Derived` | unchanged |
| `@NodeType` on an interface | **removed**: make the interface extend `ASTNode` instead (`interface Statement : ASTNode`) |
| `@Link` on a property | **removed**: express references as `ReferenceByName<T>` properties; a `Node`-typed property is always a containment |
| `Named`, `PossiblyNamed` | now extend `ASTNode`, so only nodes can implement them |
| `Statement`, `Expression`, `EntityDeclaration`, `Parameter`, `Documentation`, `TypeAnnotation`, `BehaviorDeclaration`, `EntityGroupDeclaration` | unchanged names, now `: CommonElement : ASTNode`; `PlaceholderElement` moved to its own file, same package |
| `Node.derivedProperties` | removed; use `properties.filter { it.derived }` |
| `withPosition`, `withOrigin`, `withDestination`, `withParseTreeNode` | unchanged, now generic on `ASTNode` |
| `walk`, `walkDescendants`, `walkAncestors`, `findAncestorOfType`, `searchByType`, `collectByType` | unchanged names, receivers and results are `ASTNode` |
| `HasID`, `NodeIdProviderAdapter`, `assignIDsToTree` | moved from `model` to `com.strumenta.starlasu.ids` |
| `NodeIDDestination` | removed |
| `GenericNode`, `findGenericNode()` | removed |

Reserved property names grew: besides `parent`, `position`, `id`, `annotations`, a node may not declare a public
property named `destination`, `origin`, `nodeType`, `simpleNodeType`, `source` or `sourceText` unless it is marked
`@Internal`.

Generic code that walked the tree and called `BaseASTNode`-only members (`detach`, `setSourceForTree`) on the
`ASTNode` values returned by `children`/`walk()` needs a cast.

## 3. `ReferenceByName`

The class is unchanged: `ReferenceByName(name, initialReferred = null, identifier = null)`, `referred`,
`resolved`, `retrieved`, `tryToResolve(candidates, caseInsensitive)`, `tryToResolve(possibleValue)`,
`KReferenceByName`, `getReferredType()`. Since 1.7.5 the `referred` setter accepts any `ASTNode`
(before it only accepted `BaseASTNode` subclasses at runtime).

The type parameter bound `N : PossiblyNamed` now implies `N` is a node, so a reference to a non-node class no
longer type-checks.

## 4. Parsing

Pure renames:

| Kolasu 1.5 | Starlasu 1.7 |
|---|---|
| `KolasuParser<R, P, C, T>` | `StarlasuParser<R, P, C, T>` |
| `KolasuANTLRLexer<T>` / `KolasuLexer<T>` | `StarlasuANTLRLexer<T>` / `StarlasuLexer<T>` |
| `KolasuToken`, `KolasuANTLRToken` | `StarlasuToken`, `StarlasuANTLRToken` |
| `KolasuParserInstantiator` | `StarlasuParserInstantiator` |
| `ParsingResult.correct` (deprecated) | `ParsingResult.isCorrect` |

`TokenFactory`, `ANTLRTokenFactory`, `TokenCategory`, `LexingResult`, `FirstStageParsingResult`,
`ParsingResult`, `ASTParser`, `LanguageModule` keep their names. All `StarlasuParser` methods keep their names
and signatures (`createANTLRLexer`, `createANTLRParser`, `invokeRootRule`, `parseFirstStage`, `parse`, `lex`,
`postProcessAst`, `verifyParseTree`, `assignParents`, ...).

One change: `parseTreeToAst(parseTreeRoot, considerPosition, issues, source)` is no longer abstract. The default
implementation uses the transformer returned by the new `protected open fun setupASTTransformer(): ASTTransformer?`
(default `null`), so a parser can either keep overriding `parseTreeToAst` or override `setupASTTransformer`.

## 5. Transformations (`NodeFactory` → `TransformationRule`)

This is the one area with real signature changes: every callback now receives a `TransformationContext`, which
carries what used to be transformer state (`issues`, `addIssue(...)`, `parent`, `source`).

| Kolasu 1.5 | Starlasu 1.7 |
|---|---|
| `NodeFactory<S, T>` | `TransformationRule<S, T>` |
| `ChildNodeFactory` | `ChildTransformationRule` |
| `ASTTransformer(issues, allowGenericNode, throwOnUnmappedNode, faultTollerant, defaultTransformation)` | `ASTTransformer(faultTolerance = FaultTolerance.LOOSE, defaultTransformation)`; `FaultTolerance` is `STRICT`, `THROW_ONLY_ON_UNMAPPED` or `LOOSE` |
| `ParseTreeToASTTransformer(issues, allowGenericNode, source, throwOnUnmappedNode)` | `ParseTreeToASTTransformer(faultTolerance = THROW_ONLY_ON_UNMAPPED)`; pass the source through `TransformationContext(source = ...)` |
| `transformer.issues`, `transformer.addIssue(...)` | `context.issues`, `context.addIssue(...)` |
| `transform(source, parent, expectedType)` | `transform(source, context = TransformationContext(), expectedType)` |
| `transformIntoNodes(source, parent, expectedType)` | `transformIntoNodes(source, context, expectedType)` |
| `registerNodeFactory(...)` | `registerRule(...)` |
| `registerMultipleNodeFactory(...)` | `registerMultipleTransform(...)` |
| `registerNodeFactoryUnwrappingChild(...)` | `registerRuleUnwrappingChild(...)` |
| lambda `(S, ASTTransformer) -> T?` | `(S, TransformationContext, ASTTransformer) -> T?` |
| lambda `(S, ASTTransformer, NodeFactory) -> T?` | `(S, TransformationContext, ASTTransformer, TransformationRule) -> T?` |
| lambda `S.(ASTTransformer) -> T?` | `S.(TransformationContext) -> T?` |
| lambda `(S) -> T?` | unchanged |
| `getNodeFactory(kClass)` | `getTransformationRule(kClass)` |
| `translateCasted(...)`, `translateList(...)`, `translateOptional(...)`, `translateOnlyChild(...)` | same names, take the `context` as second parameter |
| `TrivialFactoryOfParseTreeToASTNodeFactory` | `TrivialFactoryOfParseTreeToASTTransform`; `convertString(text, expectedType)`, `convert(value, transformer, context, expectedType)` |
| `withChild(...)`, `withFinalizer(...)`, `skipChildren()`, `registerIdentityTransformation(...)`, `notTranslateDirectly<S>()` | unchanged |

Behaviour changes to be aware of:

- `NodeFactory.childrenSetAtConstruction`/`skipChildren` flags became `TransformationRule.childrenPolicy`
  (`SET_AT_CONSTRUCTION`, `SET_AFTER_CONSTRUCTION`, `SKIP`).
- Calling `withChild` with a read-only property on a rule that has a custom constructor lambda now throws a
  `ConfigurationException` at configuration time (1.5 silently switched to "set at construction").
  Either make the property a `var` or set the child yourself in the constructor lambda.
- `allowGenericNode` and `GenericNode` are gone.

## 6. Semantics: declarative scope providers

The whole 1.5 `semantics` module survived with the same type names; only the package changed and `Node` bounds
became `ASTNode` bounds.

| Kolasu 1.5 (`com.strumenta.kolasu.semantics...`) | Starlasu 1.7 (`com.strumenta.starlasu.semantics...`) |
|---|---|
| `scope.provider.declarative.DeclarativeScopeProvider`, `scopeFor(Node::ref) { ... }`, `DeclarativeScopeProviderRule`, `DeclarativeScopeProviderRuleContext` | same, in `starlasu-semantics` |
| `scope.description.ScopeDescription`, `ScopeDescriptionApi`, `scope { ... }` | same |
| `scope.provider.ScopeProvider` | same |
| `symbol.resolver.SymbolResolver(scopeProvider)`, `resolve(node, reference)`, `resolve(node, entireTree)` | same |
| `symbol.provider.declarative.DeclarativeSymbolProvider`, `symbolFor { ... }` | same |
| `symbol.repository.SymbolRepository`, `symbol.importer.SymbolImporter`, `symbol.description.SymbolDescription` | same |

Two things to check:

- `KOLASU_SYMBOL_DESCRIPTION_LANGUAGE_NAME` changed from
  `com.strumenta.kolasu.semantics.symbol.SymbolDescriptionLanguage` to
  `com.strumenta.starlasu.semantics.symbol.SymbolDescriptionLanguage`. Symbol repositories persisted by 1.5 refer
  to the old language name.
- `starlasu-core` also contains `com.strumenta.starlasu.semantics.{Semantics, ScopeProvider, SymbolResolver,
  TypeComputer}` and `com.strumenta.starlasu.symbolresolution.LocalSymbolResolver`. These are the *old*,
  `@Deprecated` DSL (`semantics { ... }`, `scopeProvider { ... }`) that predates the declarative module; they
  were deprecated in 1.5 as well. Language modules that use `DeclarativeScopeProvider` should keep depending on
  `starlasu-semantics` and not switch to the `core` ones.

## 7. LionWeb

Package `com.strumenta.kolasu.lionweb` → `com.strumenta.starlasu.lionweb`, module `kolasu-lionweb` →
`starlasu-lionweb`.

### `LionWebModelConverter`

The constructor is unchanged: `LionWebModelConverter(nodeIdProvider, initialLanguageConverter, metamodelRegistry,
ignoreMissingReferences)`. Unchanged methods: `registerPrimitiveValueSerialization(kClass, serialization)`,
`exportLanguageToLionWeb(kolasuLanguage)`, `associateLanguages(lwLanguage, kolasuLanguage)`,
`correspondingLanguage(...)`, `importModelFromLionWeb(lwTree)`, `prepareSerialization(...)`,
`deserializeToNodes(json)`, `knownLWLanguages()`, `knownKolasuLanguages()`, `exportIssueToLionweb`,
`importIssueFromLionweb`, `exportParsingResultToLionweb`, `externalNodeResolver`, `clearNodesMapping()`.

| Kolasu 1.5 | Starlasu 1.7 |
|---|---|
| `exportModelToLionWeb(kolasuTree = ..., nodeIdProvider, considerParent)` | first parameter renamed to `starlasuTree` (matters only with named arguments) |
| `getKolasuClassesToClassifiersMapping()` | `getStarlasuClassesToClassifiersMapping()` |
| `getClassifiersToKolasuClassesMapping()` | `getClassifiersToStarlasuClassesMapping()` |
| `KNode` | `SNode` (= `ASTNode`) |
| `LIONWEB_VERSION_USED_BY_KOLASU` | `LIONWEB_VERSION_USED_BY_STARLASU` |
| `NodeIdProvider.registerMapping(node, id)` | removed |

`KolasuLanguage` keeps its name (`com.strumenta.starlasu.language.KolasuLanguage`) with `addClass`,
`addInterfaceClass`, `addEnumClass`, `addPrimitiveClass`. It is marked `@Deprecated("Use LionWeb's Language")`
together with `Feature`/`Attribute`/`Containment`/`Reference`, but it is still the input of
`exportLanguageToLionWeb` and `associateLanguages`, so a language module keeps using it.

The same `LionWebLanguageConverter` methods were renamed `Kolasu` → `Starlasu`
(`getEnumerationsToStarlasuClassesMapping()`, `correspondingStarlasuClass(classifier)`, ...).

### Custom primitive types (`PrimitiveValueSerialization`)

Unchanged. `interface PrimitiveValueSerialization<E> { fun serialize(value: E): String; fun deserialize(serialized: String): E }`
and `converter.registerPrimitiveValueSerialization(LocalDateTime::class, ...)` work as in 1.5; the primitive
class is still discovered through `KolasuLanguage.primitiveClasses` and exported as a LionWeb `PrimitiveType`.

### Annotations

Unchanged: `node.annotations` is a `MutableList<AnnotationInstance>` (LionWeb `DynamicAnnotationInstance`
instances are exported and imported as they are). `ASTNode` gained `addAnnotation(instance)`.

### Built-in serializers

| Kolasu 1.5 | Starlasu 1.7 |
|---|---|
| `registerSerializersAndDeserializersInMetamodelRegistry(...)`, `charSerializer`, `pointSerializer`, `positionSerializer`, `tokensListDataTypeSerializer` (and deserializers) | unchanged |
| `tokensListPrimitiveDeserializer` | `tokensListDataTypeDeserializer` |
| `TokensList(tokens: List<KolasuToken>)` | `TokensList(tokens: List<StarlasuToken>)` |

### Serialized output

1.5 exported languages depended on the Specs **v1** AST language (`com.strumenta.starlasu.base.v1`); 1.7 exports
depend on **v2** (`com.strumenta.starlasu.base.v2.ASTLanguage`). Import still accepts models based on either.
Exported JSON produced by 1.5 and 1.7 for the same tree therefore differs in the language dependencies and in
the ids of the built-in concepts (`ASTNode`, `Issue`, ...).

The `lionwebrepo-client` module became `lionweb-client`, and `KolasuClient` became `StarlasuClient`.

## 8. Starlasu-Specs

| Kolasu 1.5 (`starlasuspecs-jvm`) | Starlasu 1.7 (`languages`) |
|---|---|
| `com.strumenta.starlasu.base.v1.ASTLanguageV1.getASTNode()` (static getters) | `com.strumenta.starlasu.base.v2.ASTLanguage.getInstance().astNode` (singleton + properties); v1 is still available as `com.strumenta.starlasu.base.v1.ASTLanguage.getInstance()` |
| `CodebaseLanguageV1.getCodebaseFile()` | `com.strumenta.starlasu.base.v2.CodebaseLanguage.getInstance().codebaseFile` |
| `com.strumenta.starlasu.base.v1.MigrationLanguage.getDroppedElement()` | `MigrationLanguage.getInstance().droppedElement` |
| `com.strumenta.starlasu.base.IDProvider` | `com.strumenta.starlasu.IDProvider` |
| `com.strumenta.starlasu.base.CodebaseAccess` | `com.strumenta.starlasu.pipeline.CodebaseAccess` (in `components`) |

Watch out for a simple-name clash: Specs `components` also contains
`com.strumenta.starlasu.pipeline.components.StarlasuParser`, which is unrelated to
`com.strumenta.starlasu.parsing.StarlasuParser`.

## 9. Removed without replacement

- `com.strumenta.kolasu.serialization` (`JsonGenerator`, `JsonDeserializer`, `XMLGenerator`, `toJson` helpers,
  `Indexing.kt` id providers): use the LionWeb serialization (`LionWebModelConverter.prepareSerialization()`).
- `com.strumenta.kolasu.cli` (`CLITool`, `ASTProcessingCommand`, `ASTSaverCommand`, `StatsCommand`).
- `com.strumenta.kolasu.language.Unparser`.
- `@Link`, `@NodeType`, `KClass.isMarkedAsNodeType()`, `GenericNode`, `Node.derivedProperties`,
  `PropertyDescription.provideNodes`, `NodeIDDestination`, `NodeIdProvider.registerMapping`.
- The `emf`, `lionweb-gen`, `lionweb-ksp`, `lionweb-gen-gradle` and `playground` modules.

## 10. Suggested order of work

1. Update the build: Kotlin 2.4, JVM 21, ANTLR 4.13, the `com.strumenta.starlasu` coordinates, LionWeb 1.4.5 and
   Specs 0.7.5 if referenced directly.
2. Rename imports `com.strumenta.kolasu.` → `com.strumenta.starlasu.`.
3. Replace `@NodeType interface X` with `interface X : ASTNode`, drop `@Link`.
4. Rename `KolasuParser`, `KolasuToken`, `NodeFactory`, `KNode`, ... as listed in the tables above.
5. Adapt transformer registrations: add the `TransformationContext` parameter to lambdas, move `issues`/`source`
   into the context, replace the boolean flags with `FaultTolerance`.
6. Update Specs accessors to `getInstance()` and the `v2` AST language.
7. Re-check expectations in tests that compare exported LionWeb JSON.
