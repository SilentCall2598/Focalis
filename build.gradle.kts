plugins {
    java
    // RetroFuturaGradle is a maintained ForgeGradle fork that supports 1.12.2 on modern Gradle.
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.4"
}

group = "io.github.silentcall2598"
version = providers.gradleProperty("mod_version").get()

base {
    archivesName = "focalis"
}

java {
    toolchain {
        // Minecraft 1.12.2 runs on Java 8. Gradle itself may run on a newer JDK.
        languageVersion = JavaLanguageVersion.of(8)
    }
    withSourcesJar()
}

minecraft {
    mcVersion = "1.12.2"
    mcpMappingChannel = "stable"
    mcpMappingVersion = "39"
    username = "Developer"

    // Exposed to code as Tags.VERSION, which the @Mod annotation uses.
    injectedTags.put("VERSION", project.version)

    extraRunJvmArguments.add("-ea:${project.group}")
}

tasks.injectTags.configure {
    outputClassName = "io.github.silentcall2598.focalis.Tags"
}

tasks.processResources.configure {
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("mcmod.info") {
        expand(mapOf("version" to modVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// Only lint Focalis code. RFG also compiles the decompiled Minecraft sources with JavaCompile tasks.
listOf(tasks.compileJava, tasks.compileTestJava).forEach { task ->
    task.configure {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-options"))
    }
}

// The reobfuscated release jar is built from the jar task, so it inherits these files.
listOf(tasks.jar, tasks.named<Jar>("sourcesJar")).forEach { task ->
    task.configure {
        from(files("LICENSE", "COPYING")) {
            into("META-INF")
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Optional local pack for ShaderPackSmokeTest, e.g. ./gradlew test -PsmokeTestShaderPack=<folder or zip>
    providers.gradleProperty("smokeTestShaderPack").orNull?.let {
        systemProperty("focalis.smokeTestShaderPack", file(it).absolutePath)
    }
}
