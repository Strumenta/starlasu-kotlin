# Starlasu Kotlin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.strumenta.starlasu/starlasu-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.strumenta.starlasu/starlasu-core)
![Build Status](https://img.shields.io/github/actions/workflow/status/Strumenta/starlasu-kotlin/check.yml?branch=main)

**Starlasu Kotlin** provides the infrastructure for building custom, possibly mutable, Abstract Syntax Trees (ASTs).  
It supports both **Kotlin** and **Java**, and integrates smoothly with **ANTLR**, though it can also be used standalone.

> **Starlasu** stands for **Star** **La**nguage **Su**pport — a family of libraries available for multiple languages, including **Python**, **TypeScript**, and **C#**.

### **Starlasu Kotlin (previosuly called Kolasu) Version Overview**

| Version | Status | JVM Support | Kotlin Support | Notes |
|----------|---------|--------------|----------------|--------|
| **1.5** | Maintenance mode | 1.8 | 1.8            | Actively maintained for legacy compatibility (Kolasu, `com.strumenta.kolasu`) |
| **1.6** | Dropped | — | —              | Internal line, never meant to be adopted |
| **1.7** | Current stable | 21 | 2.4            | Current release line (`main`); see the [1.5 → 1.7 migration guide](docs/migration-1.5-to-1.7.md) |
| **2.0** | Planned | 29 | —              | Future major release |

## Supported JDKs

Tested with **JDK 21** and **25**.

---

## Documentation

- **Concepts and Principles:** [Starlasu documentation](https://github.com/Strumenta/Starlasu/tree/main/documentation) — shared across all Starlasu libraries.
- **API Reference:** [Javadoc for Starlasu Kotlin](https://www.javadoc.io/doc/com.strumenta.starlasu).
- **Migrating from Kolasu 1.5:** [1.5 → 1.7 migration guide](docs/migration-1.5-to-1.7.md) — package rename, renamed/removed types, LionWeb and Starlasu-Specs coordinates.

---

## What Is It Used For?

Starlasu is used to implement:

- Parsers
- Editors
- Transpilers
- Code analysis tools

It serves as a general foundation for projects that need to manipulate or generate language structures.

---

## Key Features

Extend your AST classes from `Node` to automatically gain:

- **Navigation:** Traverse, search, and modify the AST with utility methods.
- **Printing:** Output ASTs as XML, JSON, or parse trees.
- **LionWeb Interoperability:** Export ASTs and languages (i.e., metamodels) to LionWeb.
- **Name Resolution:** Built-in utilities for named elements and reference resolution.
- **Automatic Structure Discovery:** Starlasu introspects your AST — all properties and tree structure are detected automatically.

---

## Background

Starlasu began as a small framework to support building languages with ANTLR and Kotlin.  
It has since evolved into a modular, cross-language toolkit used at **[Strumenta](https://strumenta.com)** in both open-source and commercial projects for transpilers, interpreters, compilers, and related tools.

---

## Installation

Add the dependency from Maven Central:

```gradle
dependencies {
    implementation "com.strumenta.starlasu:starlasu-core:1.7.x"
}
```

## Performance benchmarks

The `lionweb` module contains JMH microbenchmarks for the model-conversion
hot paths (`importModelFromLionWeb`, `exportModelToLionWeb`, tree traversal).

```bash
# Run all benchmarks
./gradlew :lionweb:jmh

# Run a specific class
./gradlew :lionweb:jmh --include "ImportModelBenchmark"

# Run with GCProfiler (allocation tracking) via the fat jar
./gradlew :lionweb:jmhJar
java -jar lionweb/build/libs/lionweb-jmh.jar ImportModelBenchmark
```

See [`lionweb/README.md`](lionweb/README.md) for the full benchmark documentation.

## How to format code

## Code Formatting

Format the code using:

```bash
./gradlew ktlintFormat
```

---

## Projects Using Starlasu / Kolasu

Starlasu (and its predecessor Kolasu) are used in several internal and commercial projects developed at [Strumenta](https://strumenta.com).

---

## Releasing a New Version

If you need to publish a new release:

1. **Set up GPG keys**
   ```bash
   brew install gnupg
   gpg --gen-key
   ```
   (no passphrase needed)  
   Export your keys:
   ```bash
   gpg --keyring secring.gpg --export-secret-keys > ~/.gnupg/secring.gpg
   ```

2. **Configure credentials**  
   Add your Sonatype credentials to `~/.gradle/gradle.properties`:
   ```
   ossrhTokenUsername=your_username
   ossrhTokenPassword=your_password
   ```

3. **Publish the release**
   ```bash
   ./gradlew release
   ```

Releases are handled automatically once triggered.

For detailed setup instructions, see  
[Publishing your first open-source library with Gradle](https://selectfrom.dev/publishing-your-first-open-source-library-with-gradle-50bd0b1cd3af).

