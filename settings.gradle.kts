pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "starlasu-kotlin"

include("core")
include("codebase")
include("semantics")
include("javalib")
include("lionweb")
include("lionweb-client")
