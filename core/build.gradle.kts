plugins {
    kotlin("jvm")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.vanniktech.publish)
    id("antlr")
    id("idea")
    id("signing")
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
}

dependencies {
    antlr(libs.antlr)
    implementation(libs.antlr.runtime)
    implementation(libs.starlasu.specs.languages)
    implementation(libs.starlasu.specs.components)
    implementation(kotlin("stdlib", libs.versions.kotlin.get()))
    implementation(kotlin("reflect", libs.versions.kotlin.get()))
    implementation(libs.gson)
    api(libs.clikt)
    api(libs.lionweb.java)

    implementation(kotlin("test", libs.versions.kotlin.get()))
    testImplementation(kotlin("test-junit", libs.versions.kotlin.get()))
}

tasks.named<AntlrTask>("generateTestGrammarSource") {
    maxHeapSize = "64m"
    arguments.addAll(listOf("-package", "com.strumenta.simplelang"))
    outputDirectory = file("generated-test-src/antlr/main/com/strumenta/simplelang")
}

tasks.named("runKtlintCheckOverMainSourceSet") {
    dependsOn("generateGrammarSource")
}

tasks.named("compileKotlin") {
    dependsOn("generateGrammarSource")
}
tasks.named("compileJava") {
    dependsOn("generateGrammarSource", "generateTestGrammarSource")
}
tasks.named("compileTestKotlin") {
    dependsOn("generateTestGrammarSource")
}
tasks.named("runKtlintCheckOverTestSourceSet") {
    dependsOn("generateTestGrammarSource")
}
tasks.named("runKtlintFormatOverTestSourceSet") {
    dependsOn("generateTestGrammarSource")
}
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin").configure {
    source(sourceSets["main"].allJava, sourceSets["main"].kotlin)
}
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin").configure {
    source(sourceSets["test"].kotlin)
}

sourceSets.named("test") {
    java.srcDir("generated-test-src/antlr/main")
}

tasks.named<Delete>("clean") {
    delete("generated-src", "generated-test-src")
}

val jvm =
    libs.versions.jvm
        .get()
        .toInt()
kotlin {
    jvmToolchain(jvm)
}

idea {
    module {
        testSources.from(file("generated-test-src/antlr/main"))
    }
}

// Some tasks are created during the configuration, and therefore we need to set the dependencies involving
// them after the configuration has been completed
project.afterEvaluate {
    tasks.named("sourcesJar") {
        dependsOn("generateGrammarSource")
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    coordinates(project.group.toString(), "starlasu-${project.name}", project.version.toString())

    pom {
        name.set(project.name)
        description.set("A framework for Language Engineering")
        inceptionYear.set("2017")
        url.set("https://github.com/Strumenta/starlasu-kotlin/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("ftomassetti")
                name.set("Federico Tomassetti")
                email.set("federico@strumenta.com")
            }
            developer {
                id.set("alessiostalla")
                name.set("Alessio Stalla")
                email.set("alessio.stalla@strumenta.com")
            }
        }
        scm {
            url.set("https://github.com/Strumenta/starlasu-kotlin/")
            connection.set("scm:git:git://github.com/Strumenta/starlasu-kotlin.git")
            developerConnection.set("scm:git:ssh://git@github.com/Strumenta/starlasu-kotlin.git")
        }
    }
}
