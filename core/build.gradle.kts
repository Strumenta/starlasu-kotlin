plugins {
    kotlin("jvm")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.vanniktech.publish)
    id("antlr")
    id("idea")
    id("signing")
    id("org.jetbrains.dokka")
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())

    registerFeature("cli") {
        usingSourceSet(sourceSets["main"])
    }
}

dependencies {
    antlr(libs.antlr)
    api(libs.clikt)
    api(libs.lionweb.java)
    implementation(libs.antlr.runtime)
    implementation(libs.starlasu.specs)
    implementation(kotlin("stdlib", libs.versions.kotlin.get()))
    implementation(kotlin("reflect", libs.versions.kotlin.get()))
    implementation(kotlin("test", libs.versions.kotlin.get()))
    implementation(libs.gson)

    testImplementation(kotlin("test-junit", libs.versions.kotlin.get()))
    // To be removed in v1.7
    implementation("org.redundent:kotlin-xml-builder:1.7.3")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.9.2")
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
tasks.named("sourcesJar") {
    dependsOn("generateGrammarSource")
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

idea {
    module {
        testSources.from(file("generated-test-src/antlr/main"))
    }
}

publishing {
    repositories {
        maven {
            val releaseRepo = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            val snapshotRepo = uri("https://central.sonatype.com/repository/maven-snapshots/")
            url = if (!version.toString().endsWith("SNAPSHOT")) releaseRepo else snapshotRepo

            credentials {
                username = project.findProperty("mavenCentralUsername") as? String ?: "Unknown user"
                password = project.findProperty("mavenCentralPassword") as? String ?: "Unknown password"
            }
        }
    }

    publications.withType<MavenPublication>().configureEach {
        if (name == "maven") {
            artifactId = "kolasu-${project.name}"
            suppressPomMetadataWarningsFor("cliApiElements")
            suppressPomMetadataWarningsFor("cliRuntimeElements")
            pom {
                name.set("kolasu-${project.name}")
                description.set("Framework to work with AST and building languages. Integrated with ANTLR.")
                version = project.version.toString()
                packaging = "jar"
                url.set("https://github.com/Strumenta/kolasu")
                scm {
                    connection.set("scm:git:https://github.com/Strumenta/kolasu.git")
                    developerConnection.set("scm:git:git@github.com:Strumenta/kolasu.git")
                    url.set("https://github.com/Strumenta/kolasu.git")
                }
                licenses {
                    license {
                        name.set("Apache License V2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        distribution.set("repo")
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
            }
        }
    }
}

signing {
    val key = providers.gradleProperty("signingInMemoryKey").orNull
    val keyId = providers.gradleProperty("signingInMemoryKeyId").orNull
    val pass = providers.gradleProperty("signingInMemoryKeyPassword").orNull
    if (!key.isNullOrBlank()) {
        useInMemoryPgpKeys(keyId, key, pass)
        sign(publishing.publications)
    }
}

// Some tasks are created during the configuration, and therefore we need to set the dependencies involving
// them after the configuration has been completed
project.afterEvaluate {
    tasks.named("dokkaJavadocJar") {
        dependsOn(tasks.named("dokkaJavadoc"))
    }
    tasks.matching { it.name.startsWith("publish") && it.name.endsWith("ToMavenRepository") }
        .configureEach {
            dependsOn("dokkaJavadocJar", "javaSourcesJar", "javadocJar", "sourcesJar")
        }
    tasks.named("javaSourcesJar") {
        dependsOn(tasks.named("generateGrammarSource"))
    }
    if (tasks.findByName("signMavenPublication") != null) {
        tasks.matching { it.name.startsWith("sign") && it.name.endsWith("Publication") }
            .configureEach {
                dependsOn("javadocJar", "sourcesJar")
            }
    }
}
