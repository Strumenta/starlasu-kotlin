import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
    alias(libs.plugins.vanniktech.publish)
    id("antlr")
    id("idea")
    id("signing")
    id("org.jetbrains.dokka")
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
    testImplementation(kotlin("test-junit", libs.versions.kotlin.get()))
    testImplementation(libs.guava)
}

// Configure Kotlin compilation source sets
tasks.named<KotlinCompile>("compileKotlin").configure {
    source(sourceSets["main"].allJava, sourceSets["main"].kotlin)
}

tasks.named<KotlinCompile>("compileTestKotlin").configure {
    source(sourceSets["test"].kotlin)
}

// Task dependencies
tasks.named("dokkaJavadoc") {
    dependsOn(":core:compileKotlin")
}
tasks.named("runKtlintCheckOverMainSourceSet") {
    dependsOn("generateGrammarSource")
}
tasks.named("runKtlintCheckOverTestSourceSet") {
    dependsOn("generateTestGrammarSource")
}
tasks.named("compileTestKotlin") {
    dependsOn("generateTestGrammarSource")
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
    coordinates(project.group.toString(),
        "starlasu-${project.name}", project.version.toString())

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
