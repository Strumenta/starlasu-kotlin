# Kolasu

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.strumenta.kolasu/kolasu-core/badge.svg?gav=true)](https://maven-badges.herokuapp.com/maven-central/com.strumenta.kolasu/kolasu-core?gav=true)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/Strumenta/kolasu/check.yml)

<!-- 
Hiding these badges as they appear broken when they are not!
[![javadoc](https://javadoc.io/badge2/com.strumenta.kolasu/kolasu-core/javadoc.svg)](https://javadoc.io/doc/com.strumenta.kolasu/kolasu-core)
 -->

Kolasu supplies the infrastructure to build a custom, possibly mutable, Abstract Syntax Tree (AST) using Kotlin.
In particular, it can be integrated easily with ANTLR, but it can also be used on its own.
Kolasu strives to be usable and idiomatic also in Java projects.

It stands for _**Ko**tlin_ _**La**nguage_ _**Su**pport_.

Kolasu is part of the [StarLasu](https://github.com/Strumenta/StarLasu) set of libraries. The other libraries provide 
similar support in other languages such as Typescript and Python.

## JDK supported

We support JDK 8, 11, and 17. All JDKs in between should work too, but these are explicitly tested.

## Documentation

You can take a look at the documentation for StarLasu, as it explain the principles used in the whole set of libraries, including Kolasu: [StarLasu documentation](https://github.com/Strumenta/StarLasu/tree/main/documentation).

The documentation of Kolasu's APIs is on Maven Central for consumption by IDEs. It's also possible to consult it online at https://www.javadoc.io/doc/com.strumenta.kolasu.

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

Kolasu was born as a small framework to support building languages using ANTLR and Kotlin. It evolved over the time as 
it was used at Strumenta as part of open-source and commercial projects for building transpilers, interpreters, 
compilers, and more.

## Using Kolasu in your project

Releases are published on Maven Central: 

```
dependencies {
    compile "com.strumenta.starlasu:kolasu-core:1.5.0-RC5"
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