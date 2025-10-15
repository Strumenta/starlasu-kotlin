plugins {
    kotlin("jvm")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.vanniktech.publish)
    id("idea")
    id("signing")
    id("org.jetbrains.dokka")
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${libs.versions.kotlin.get()}")
    testImplementation(libs.guava)
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
    val keyRaw = providers.gradleProperty("signingInMemoryKey").orNull
    val key = keyRaw?.replace("\\n", "\n") // <-- trasforma \n in newline reali
    val keyId = providers.gradleProperty("signingInMemoryKeyId").orNull
    val pass = providers.gradleProperty("signingInMemoryKeyPassword").orNull

    if (!key.isNullOrBlank()) {
        require(key.startsWith("-----BEGIN PGP PRIVATE KEY BLOCK-----")) {
            "signingInMemoryKey non contiene una PRIVATE key"
        }
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
    if (tasks.findByName("signMavenPublication") != null) {
        tasks.matching { it.name.startsWith("sign") && it.name.endsWith("Publication") }
            .configureEach {
                dependsOn("javadocJar", "sourcesJar")
            }
    }
}
