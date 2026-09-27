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

repositories {
    maven {
        // MixinBooter
        name = "CleanroomMC"
        url = uri("https://maven.cleanroommc.com")
        mavenContent {
            includeGroup("zone.rong")
        }
    }
}

// Mixin comes from MixinBooter, which players install next to Focalis. It is never bundled.
val mixinBooter = "zone.rong:mixinbooter:10.7"
// Has the Mixin annotation processor write the refmap and feeds its mappings to reobfJar.
modUtils.enableMixins(mixinBooter, "mixins.focalis.refmap.json")

// The coremod queues the Mixin config with MixinBooter. The jar still holds the @Mod. runClient runs the dev jar,
// where RFG's launcher finds the coremod through this same manifest.
tasks.jar.configure {
    manifest {
        attributes(
            "FMLCorePlugin" to "io.github.silentcall2598.focalis.core.FocalisLoadingPlugin",
            "FMLCorePluginContainsFMLMod" to "true"
        )
    }
}

// runObfClient loads mods from its mods folder like a real install. Forge loads coremods there in file name
// order, so MixinBooter gets its release name. Otherwise the Focalis coremod comes first and can't load.
tasks.named<Copy>("prepareObfModsFolder") {
    rename("^(mixinbooter-.+\\.jar)$", "!$1")
}

// tools/qa/run-qa.ps1 sets these. QA runs get their own game folder, so the normal run folder is never touched.
val qaScenario = providers.gradleProperty("qaScenario")
if (qaScenario.isPresent) {
    val qaGameDir = file(providers.gradleProperty("qaGameDir").get())
    val qaOutputDir = file(providers.gradleProperty("qaOutputDir").get())
    val qaFullscreen = providers.gradleProperty("qaFullscreen").getOrElse("false")
    tasks.withType<JavaExec>().matching { it.name == "runClient" || it.name == "runObfClient" }.configureEach {
        workingDir = qaGameDir
        systemProperty("focalis.qa.scenario", qaScenario.get())
        systemProperty("focalis.qa.output", qaOutputDir.absolutePath)
        systemProperty("focalis.qa.fullscreen", qaFullscreen)
    }
    // runObfClient loads the release jar from the mods folder of its game folder.
    tasks.withType<Copy>().matching { it.name == "prepareObfModsFolder" }.configureEach {
        into(qaGameDir.resolve("mods"))
    }
}

dependencies {
    implementation(mixinBooter)
    // The annotation processor needs ASM, which MixinBooter doesn't include. 5.2 is what Forge 1.12.2 ships.
    annotationProcessor(mixinBooter)
    annotationProcessor("org.ow2.asm:asm-debug-all:5.2")

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
