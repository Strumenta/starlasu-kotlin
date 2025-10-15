plugins {
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm")
    id("com.github.gmazzo.buildconfig") version "3.1.0"
    id("maven-publish")
    id("com.gradle.plugin-publish") version "1.2.0"
    id("org.jetbrains.dokka")
    `kotlin-dsl`
}

val kspVersion = extra["kspVersion"] as String
val kotlin_version = extra["kotlin_version"] as String
val lionwebGenGradlePluginID = extra["lionwebGenGradlePluginID"] as String
val completeKspVersion = if (kspVersion.contains("-")) kspVersion else "$kotlin_version-$kspVersion"
val lionwebJavaVersion = libs.versions.lwjava.get()

dependencies {
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
    implementation(libs.lionweb.java)
    api(project(":lionweb-gen"))
    testImplementation(project(":lionweb-ksp"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlin_version")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    implementation(libs.gson)
    implementation(libs.starlasu.specs)
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:$completeKspVersion")
}

buildConfig {
    packageName("com.strumenta.kolasu.lionwebgen")
    buildConfigField("String", "PLUGIN_ID", "\"${lionwebGenGradlePluginID}\"")
    buildConfigField("String", "PLUGIN_GROUP", "\"${project.group}\"")
    buildConfigField("String", "PLUGIN_NAME", "\"${project.name}\"")
    buildConfigField("String", "PLUGIN_VERSION", "\"${project.version}\"")
    buildConfigField("String", "LIONCORE_VERSION", "\"${lionwebJavaVersion}\"")
}

gradlePlugin {
    website.set("https://github.com/strumenta/kolasu")
    vcsUrl.set("https://github.com/strumenta/kolasu.git")
    plugins {
        create("lionwebGenGradlePlugin") {
            id = lionwebGenGradlePluginID
            displayName = "Kolasu LionWeb Gen"
            description = "Kolasu LionWeb Gen"
            tags.set(listOf("parsing", "ast", "starlasu", "lionweb"))
            implementationClass = "com.strumenta.kolasu.lionwebgen.LionWebGradlePlugin"
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn("generateBuildConfig")
}

tasks.test {
    useJUnitPlatform()
}

tasks.findByName("dokkaJavadoc")!!.dependsOn("generateBuildConfig")
tasks.findByName("dokkaJavadoc")!!.dependsOn(":core:compileKotlin")
tasks.findByName("dokkaJavadoc")!!.dependsOn(":emf:compileKotlin")
tasks.findByName("dokkaJavadoc")!!.dependsOn(":lionweb:jar")
tasks.findByName("dokkaJavadoc")!!.dependsOn(":lionweb-gen:jar")

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    runKtlintCheckOverMainSourceSet {
        setSource(
            project.sourceSets.main.map { sourceSet ->
                sourceSet.allSource.filter { file ->
                    !file.path.contains("/generated/")
                }
            }
        )
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

plugins.withId("maven-publish") {
    val javaComponent = components.findByName("java") as? AdhocComponentWithVariants
    if (javaComponent != null) {
        javaComponent.withVariantsFromConfiguration(configurations["cliApiElements"]) { skip() }
        javaComponent.withVariantsFromConfiguration(configurations["cliRuntimeElements"]) { skip() }
    }
}
