import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.Sign
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    alias(libs.plugins.ktlint)
    alias(libs.plugins.vanniktech.publish)
    id("idea")
    id("signing")
    id("org.jetbrains.dokka") version libs.versions.dokka.get()
    id("java-library")
    alias(libs.plugins.release)
}

allprojects {
    group = "com.strumenta.starlasu"
    version = rootProject.version

    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        }
    }
}
subprojects {

    tasks.withType<DokkaTask>().configureEach {
        dokkaSourceSets.named("main") {
            includeNonPublic.set(true)
            moduleName.set("kolasu-" + moduleName.get())
            includes.from("README.md")
        }
    }

    tasks.withType<Test>().configureEach {
        testLogging {
            showStandardStreams = true
            showExceptions = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.get()))
        }
    }

    tasks.withType<Sign>().configureEach {
        onlyIf { isReleaseVersion }
    }

    plugins.withId("java") {
        the<JavaPluginExtension>().apply {
            val jvmVersion = libs.versions.jvm.get()
            require(!jvmVersion.startsWith("1."))
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(jvmVersion))
            }
            sourceCompatibility = JavaVersion.toVersion(jvmVersion)
            targetCompatibility = JavaVersion.toVersion(jvmVersion)
        }
    }

    // Some tasks are created during the configuration, and therefore we need to set the dependencies involving
    // them after the configuration has been completed
    project.afterEvaluate {
        tasks.register<Jar>("javadocJar") {
            dependsOn(":${project.name}:dokkaJavadoc")
            archiveClassifier.set("javadoc")
            from(layout.buildDirectory.dir("dokka/javadoc"))
        }

        tasks.register<Jar>("sourcesJar") {
            archiveClassifier.set("sources")
            from(sourceSets["main"].allSource)
        }
        tasks.findByName("dokkaJavadocJar")?.dependsOn(tasks.named("dokkaJavadoc"))
        tasks.findByName("signMavenPublication")?.let { signMavenPublication ->
            tasks.findByName("dokkaJavadocJar")?.let {
                signMavenPublication.dependsOn(it)
            }
            signMavenPublication.dependsOn(tasks.named("javadocJar"))
            signMavenPublication.dependsOn(tasks.named("sourcesJar"))
        }
    }
}

// Release logic
val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")

configure<net.researchgate.release.ReleaseExtension> {
    buildTasks.set(listOf("publish"))
    git {
        requireBranch.set("")
        pushToRemote.set("origin")
    }
}

// Gradle wrapper configuration
tasks.named<Wrapper>("wrapper") {
    gradleVersion = "8.14.2"
    distributionType = Wrapper.DistributionType.ALL
}

subprojects {
    plugins.withId("signing") {
        val signingExt = extensions.getByType(SigningExtension::class.java)

        val raw = providers.gradleProperty("signingInMemoryKey").orNull
        val key = raw
            ?.replace("\\u000D", "\n")
            ?.replace("\\r\\n", "\n")
            ?.replace("\\n", "\n")
            ?.replace("\\r", "\n")
            ?.trim()

        val pass = providers.gradleProperty("signingInMemoryKeyPassword").orNull
        signingExt.useInMemoryPgpKeys(key, pass)

        plugins.withId("maven-publish") {
            val pubs = extensions.getByType(PublishingExtension::class.java).publications
            signingExt.sign(pubs)
        }
        plugins.withId("com.vanniktech.maven.publish") {
            extensions.findByType(PublishingExtension::class.java)?.publications?.let { pubs ->
                signingExt.sign(pubs)
            }
        }
    }
}