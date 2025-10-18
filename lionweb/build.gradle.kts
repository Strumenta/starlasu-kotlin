plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
    alias(libs.plugins.vanniktech.publish)
    id("signing")
    id("org.jetbrains.dokka")
}

java {
    registerFeature("cli") {
        usingSourceSet(sourceSets["main"])
    }
}

dependencies {
    implementation(kotlin("stdlib", libs.versions.kotlin.get()))
    implementation(kotlin("reflect", libs.versions.kotlin.get()))
    "cliImplementation"(libs.clikt)
    testImplementation(kotlin("test-junit", libs.versions.kotlin.get()))
    testImplementation(libs.gson)
    implementation(libs.starlasu.specs)

    api(libs.lionweb.java)
    api(libs.lionweb.kotlin)

    api(project(":core"))
    implementation(libs.starlasu.specs)
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    coordinates(
        project.group.toString(),
        "starlasu-${project.name}",
        project.version.toString(),
    )

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
