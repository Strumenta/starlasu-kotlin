plugins {
    kotlin("jvm")
    `maven-publish`
    idea
    signing
    alias(libs.plugins.ktlint)
    id("org.jetbrains.dokka")
}

val jvmVersion: String = properties["jvm_version"] as String
val kotlinVersion: String = properties["kotlin_version"] as String

java {
    setSourceCompatibility(jvmVersion)
    setTargetCompatibility(jvmVersion)
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    api(project(":core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
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
    publications {
        create<MavenPublication>("kolasu_semantics") {
            artifactId = "kolasu-" + project.name
            suppressPomMetadataWarningsFor("cliApiElements")
            suppressPomMetadataWarningsFor("cliRuntimeElements")
            from(components["java"])
            pom {
                name = "kolasu-" + project.name
                description = "Semantic enrichment facilities for Kolasu ASTs."
                version = project.version as? String
                packaging = "jar"
                url = "https://github.com/Strumenta/kolasu"
                scm {
                    connection = "scm:git:https://github.com/Strumenta/kolasu.git"
                    developerConnection = "scm:git:git@github.com:Strumenta/kolasu.git"
                    url = "https://github.com/Strumenta/kolasu.git"
                }
                licenses {
                    license {
                        name = "Apache Licenve V2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "loradd"
                        name = "Lorenzo Addazi"
                        email = "lorenzo.addazi@strumenta.com"
                    }
                }
            }
        }
    }
}

tasks.findByName("dokkaJavadoc")?.dependsOn(":core:compileKotlin")
