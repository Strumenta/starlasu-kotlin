plugins {
    kotlin("jvm")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.vanniktech.publish)
    id("org.jetbrains.dokka")
}

val jvmVersion = project.property("jvm_version") as String
val kotlinVersion = project.property("kotlin_version") as String

java {
    sourceCompatibility = JavaVersion.toVersion(jvmVersion)
    targetCompatibility = JavaVersion.toVersion(jvmVersion)
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":lionweb"))
    implementation(libs.starlasu.specs)
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    implementation(libs.gson)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}

publishing {
    repositories {
        maven {
            val releaseRepo = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            val snapshotRepo = uri("https://central.sonatype.com/repository/maven-snapshots/")

            credentials {
                username = project.findProperty("mavenCentralUsername") as? String ?: "Unknown user"
                password = project.findProperty("mavenCentralPassword") as? String ?: "Unknown password"
            }

            url = if (!version.toString().endsWith("SNAPSHOT")) releaseRepo else snapshotRepo
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

tasks.named("dokkaJavadoc").configure {
    dependsOn(":core:compileKotlin")
    dependsOn(":lionweb:jar")
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
    tasks.named("generateMetadataFileForMavenPublication") {
        dependsOn(tasks.named("dokkaJavadocJar"))
        dependsOn(tasks.named("javaSourcesJar"))
    }
}
