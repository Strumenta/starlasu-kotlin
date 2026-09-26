# Starlasu Kotlin 2: goals and re-architecture

## 1. Goals

Starlasu Kotlin 2 makes the Starlasu node a LionWeb node: an AST in memory is already a LionWeb model, with no
conversion in either direction.

1. **One model in memory.** The Starlasu node extends the LionWeb node. The double representation (Kotlin Kolasu node +
   Starlasu `DynamicNode`) will be supersesed. The bidirectional converter and the node-to-node map will be unnecessary, 
   and with them the performance cost of crossing the boundary will be removed.
2. **Starlasu specifics stay first-class.** References by name with resolution state, source and position, origin
   and destination, placeholders, issues, parsing results, common elements and tokens remain convenient Kotlin
   APIs, not just LionWeb features to query by name.
3. **Explicit language definition.** The metamodel is a LionWeb `Language` known at compile time, not derived by
   reflection from the Kotlin classes at startup.
4. **More language aspects covered.** Type management, code flow, representation of dependencies and built-ins
   emerged as important and are currently underserved: they get first-class support.
5. **Guided migration, not binary compatibility.** Modules written for 1.7 migrate with a guide; 2.0 is a major
   and may break APIs. `java.io.Serializable` on nodes is dropped.

To evaluate:
6. **Performance.** Kotlin reflection leaves the hot paths: traversal, feature access, transformations, id
   assignment.
7. **Lazy loading of reference targets.** To evaluate: a mechanism to load on demand nodes that are targets of
   references living in another file.
8. **Java as implementation language.** To evaluate: writing at least the hard core in Java, with Kotlin as an
   extension on top.

Multiplatform is a non-goal: it would require making every dependency multiplatform, starting from LionWeb.

## 2. Current state (1.7)

### 2.1 Node

- `ASTNode` is an interface, `BaseASTNode` the class language modules extend (`typealias Node`). It exposes `id`,
  `parent`, `position`, `source`, `origin`, `destination`, `annotations`, plus generic accessors.
- Annotations are already `io.lionweb.model.AnnotationInstance`: `core` already depends on `lionweb-core`.
- Features are discovered by Kotlin reflection (`memberProperties`, `@Internal`, reserved names checked at
  runtime). `kotlin.reflect` is used in 29 files of `core`; the tree walker already caches per-class lambdas to
  contain its cost.
- Java support (`javalib`) goes through JavaBeans reflection.

### 2.2 Language

- `KolasuLanguage` (deprecated) collects `KClass`es by reflection; `language/Metamodel.kt` defines Kotlin
  `Attribute`, `Containment`, `Reference` (deprecated).
- `LionWebLanguageConverter` translates it into a LionWeb `Language`. The base language comes from
  `starlasu-specs` (`ast.language.v2.json`): `ASTNode` interface (`position`, `originalNode`,
  `transpiledNodes`), the `CommonElement` interfaces, `Issue`, `ParsingResult`, the `PlaceholderNode` annotation,
  primitive types `Position`, `Point`, `Char`, `TokensList`.
- The language has no notion of `Source`.

### 2.3 References

- `ReferenceByName<N>(name, referred, identifier)` with `resolved` and `retrieved`; single references only.
- LionWeb has `ReferenceValue(referred, resolveInfo)`. The mapping is 1:1: `name` → `resolveInfo`, `referred` →
  `referred`, `identifier` → `ProxyNode(identifier)`.

### 2.4 Position, source, origin, destination

- `Position(start, end, source)`; `Source` is a JVM hierarchy (`FileSource`, `SourceSet`, `URLSource`,
  `StringSource`, `CodeBaseSource`, `SyntheticSource`, `LionWebSource`).
- `origin` and `destination` are projected in export onto `originalNode`, `transpiledNodes`, the
  `PlaceholderNode` annotation and the `DroppedElement` annotation. Anything that is not a node is lost.

### 2.5 Conversion cost

`LionWebModelConverter` walks the tree twice on export and instantiates through reflective
constructor factories on import. This means double memory (two trees plus the identity map), linear extra time at
every boundary crossing, reflection on every value, partial fidelity (non-node origins, source, unresolved
references), and two AST language versions (`ASTV1`/`ASTV2`) to keep supporting.

## 3. Proposal

- `com.strumenta.starlasu.model.Node` extends the LionWeb node (`io.lionweb.model.Node` /
  `AbstractNode`) and adds the Starlasu specifics; language classes extend it.
- Feature values live in Kotlin fields; a KSP processor generates, per class, the LionWeb `Concept`/`Interface`,
  the `getPropertyValue`/`getChildren`/`getReferenceValues` dispatch and the factory used on deserialization. A
  `DynamicNode`-based node remains available for languages known only at runtime.
- `id` is lazy (`calculateID()`), computed with the existing id provider policies.
- `position`, `origin`, `destination` are Kotlin fields exposed as the `ASTNode` LionWeb features.
- `ReferenceByName` keeps its API and becomes the `ReferenceValue` of the feature; multiple references are added.
- `Named` maps to `INamed`; `CommonElement` interfaces and annotations are unchanged.
- `Issue`, `ParsingResult` and tokens become nodes directly.
- Traversal, transformations, printing, testing and id providers are rewritten on LionWeb features and generated
  accessors, keeping the public Kotlin signatures where possible.
- `starlasu-lionweb` shrinks to primitive type serializers and factories; `lionweb-client` uses the LionWeb
  client directly.

## 4. Drop, keep, introduce

- **Drop:** `LionWebModelConverter`, `LionWebLanguageConverter`, `BiMap`, Starlasu `ProxyNode`, `KolasuLanguage`,
  `Metamodel.kt`, the reflective `nodeProperties`/`PropertyDescription` layer, AST language v1,
  `java.io.Serializable` on nodes, the deprecated `core/.../semantics` package.
- **Keep:** `Position`, `Point`, `Source`, `Origin`/`Destination`, placeholders, `ReferenceByName` API and the
  resolution model of the `semantics` module, parsing, transformations, code generation, testing, id providers,
  `codebase`, AST language v2 as the base.
- **Introduce:** `starlasu-ksp`, the Starlasu `Node` over LionWeb, multiple typed references, lazy loading of
  reference targets, support for types, code flow, dependencies and built-ins, benchmarks with a 1.7 baseline.
