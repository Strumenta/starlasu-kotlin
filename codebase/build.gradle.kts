plugins {
    kotlin("jvm")
    alias(libs.plugins.ktlint)
    id("signing")
    id("org.jetbrains.dokka")
    alias(libs.plugins.vanniktech.publish)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":lionweb"))
    implementation(kotlin("stdlib", libs.versions.kotlin.get()))
    implementation(kotlin("reflect", libs.versions.kotlin.get()))
    implementation(libs.starlasu.specs)
    implementation(libs.gson)

    testImplementation(kotlin("test", libs.versions.kotlin.get()))
    testImplementation(kotlin("test-junit", libs.versions.kotlin.get()))
}

tasks.named("dokkaJavadoc").configure {
    dependsOn(":core:compileKotlin")
    dependsOn(":lionweb:jar")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    coordinates(project.group as String, "starlasu-${project.name}", project.version as String)

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