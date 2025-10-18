# Starlasu Kotlin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.strumenta.starlasu/starlasu-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.strumenta.starlasu/starlasu-core)
![Build Status](https://img.shields.io/github/actions/workflow/status/Strumenta/starlasu-kotlin/check.yml?branch=main)

**Starlasu Kotlin** provides the infrastructure for building custom, possibly mutable, Abstract Syntax Trees (ASTs).  
It supports both **Kotlin** and **Java**, and integrates smoothly with **ANTLR**, though it can also be used standalone.

> **Starlasu** stands for **Star** **La**nguage **Su**pport — a family of libraries available for multiple languages, including **Python**, **TypeScript**, and **C#**.

---

## Supported JDKs

Tested with **JDK 11**, **17**, and **21**.  
Other intermediate versions are expected to work as well.

---

## Documentation

- **Concepts and Principles:** [Starlasu documentation](https://github.com/Strumenta/Starlasu/tree/main/documentation) — shared across all Starlasu libraries.
- **API Reference:** [Javadoc for Starlasu Kotlin](https://www.javadoc.io/doc/com.strumenta.starlasu).

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
- **LionWev Interoperability:** Export ASTs and languages (i.e., metamodels) to LionWeb.
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

---

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

