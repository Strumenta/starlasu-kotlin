# Module starlasu-javalib

Optional helpers for using Starlasu Kotlin from Java.

# Package com.strumenta.starlasu.javalib

- `JavaNode`: base class for AST nodes written in Java; discovers properties through JavaBeans getters.
- `Mandatory`: marks a `JavaNode` getter as a required (singular) property.
- `ASTTransformer`, `ParseTreeToASTTransformer`: variants of the Kotlin transformers that accept `Class` objects
  instead of `KClass`.
- `Traversing`: static methods exposing tree walks as `Stream`s or with a `Consumer`.
- `DebugPrinter`: prints a node with a configurable `DebugPrintConfiguration`.
