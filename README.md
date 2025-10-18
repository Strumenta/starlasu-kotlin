# Starlasu Kotlin

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.strumenta.starlasy/starlasu-core/badge.svg?gav=true)](https://maven-badges.herokuapp.com/maven-central/com.strumenta.kolasu/kolasu-core?gav=true)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/Strumenta/starlasu-kotlin/check.yml)

Starlasu supplies the infrastructure to build a custom, possibly mutable, Abstract Syntax Tree (AST). This library
specifically support Kotlin and Java.
In particular, it can be integrated easily with ANTLR, but it can also be used on its own.
Starlasu Kotlin strives to be usable and idiomatic also in Java projects.

Starlasu stands for **Star** _**La**nguage_ _**Su**pport_.
The **Star** indicates that this is a multi-language library, with ports of this same code
for Python, Typescript and C#.

## JDK supported

We support JDK 11, 17, and 21. All JDKs in between should work too, but these are explicitly tested.

## Documentation

You can take a look at the documentation for Starlasu, as it explain the principles used in the whole set of libraries, including Kolasu: [Starlasu documentation](https://github.com/Strumenta/Starlasu/tree/main/documentation).

The documentation of Starlasu Kotlin's APIs is on Maven Central for consumption by IDEs. It's also possible to consult it online at https://www.javadoc.io/doc/com.strumenta.kolasu.

## What do we use Kolasu for?

Kolasu has been used to implement:
* Parsers
* Editors
* Transpilers
* Code analysis tools

## Features

Extend your AST classes from `Node` to get these features:
* Navigation: utility methods to traverse, search, and modify the AST
* Printing: print the AST as XML, as JSON, as a parse tree
* EMF interoperability: ASTs and their metamodel can be exported to EMF

Classes can have a *name*, and classes can *reference* a name.
Utilities for resolving these references are supplied.

Kolasu tries to be non-invasive and implements this functionality by introspecting the AST.
All properties, and therefore the whole tree structure, will be detected automatically. 

## Origin

Starlasu was born as a small framework to support building languages using ANTLR and Kotlin. It evolved over the time as 
it was used at Strumenta as part of open-source and commercial projects for building transpilers, interpreters, 
compilers, and more.

## Using Starlasu in your project

Releases are published on Maven Central: 

```
dependencies {
    compile "com.strumenta.starlasu:starlasu-core:1.7.x"
}
```

## How to format code

Run:

```
./gradlew ktlintFormat
```

## Projects using Kolasu

Kolasu is used in several internal and commercial projects developed at [Strumenta](https://strumenta.com).

## Publishing a new release

If you do not have gpg keys:

1. Install gpg (`brew install gnupg` on mac)
2. Generate the key (`gpg --gen-key`, no passphrase needed)
3. Publish the key

Instructions available here: https://selectfrom.dev/publishing-your-first-open-source-library-with-gradle-50bd0b1cd3af

Please note that you may have to export the keys (`gpg --keyring secring.gpg --export-secret-keys > ~/.gnupg/secring.gpg`)

You will need to store in ~/.gradle/gradle.properties your sonatype credentials under ossrhTokenUsername and ossrhTokenPassword.

New release can be made by running:

```
./gradlew release
```

Releases are performed automatically.