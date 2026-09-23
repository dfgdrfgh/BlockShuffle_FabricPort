plugins {
    id("fabric-loom") version "1.14.10" apply false
    java
}

val minecraftVersion = providers.gradleProperty("mcVersion").getOrElse("1.21.11")
val fabricApiVersions = mapOf(
    "1.21" to "0.102.0", "1.21.1" to "0.116.17", "1.21.2" to "0.106.1",
    "1.21.3" to "0.114.1", "1.21.4" to "0.119.4", "1.21.5" to "0.128.2",
    "1.21.6" to "0.128.2", "1.21.7" to "0.129.0", "1.21.8" to "0.136.1",
    "1.21.9" to "0.134.1", "1.21.10" to "0.138.4", "1.21.11" to "0.141.6",
    "26.2" to "0.161.0"
)
val yarnBuilds = mapOf(
    "1.21" to 9, "1.21.1" to 3, "1.21.2" to 1, "1.21.3" to 2,
    "1.21.4" to 8, "1.21.5" to 1, "1.21.6" to 1, "1.21.7" to 8,
    "1.21.8" to 1, "1.21.9" to 1, "1.21.10" to 3, "1.21.11" to 3
)
val fabricApiVersion = requireNotNull(fabricApiVersions[minecraftVersion]) {
    "Unsupported Minecraft version: $minecraftVersion"
}
val requiredJava = if (minecraftVersion == "26.2") 25 else 21
apply(plugin = if (minecraftVersion == "26.2") "net.fabricmc.fabric-loom" else "fabric-loom")

group = "com.minecraft.mods"
version = "1.0.0-fabric.1+mc$minecraftVersion"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    add("minecraft", "com.mojang:minecraft:$minecraftVersion")
    if (minecraftVersion != "26.2") {
        add("mappings", "net.fabricmc:yarn:$minecraftVersion+build.${yarnBuilds.getValue(minecraftVersion)}:v2")
    }
    val fabricDependency = if (minecraftVersion == "26.2") "implementation" else "modImplementation"
    add(fabricDependency, "net.fabricmc:fabric-loader:0.19.5")
    add(fabricDependency, "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion+$minecraftVersion")
}

sourceSets {
    main {
        java.setSrcDirs(if (minecraftVersion == "26.2") {
            listOf("src/shared/java", "src/26_2/java")
        } else {
            listOf("src/shared/java", "src/main/java", if (minecraftVersion == "1.21.11") "src/modern/java" else "src/legacy/java",
                if (minecraftVersion in setOf("1.21.6", "1.21.7", "1.21.8")) "src/world_middle/java" else "src/world_regular/java")
        })
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(requiredJava))
    withSourcesJar()
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcVersion", minecraftVersion)
    inputs.property("javaVersion", requiredJava)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "mcVersion" to minecraftVersion, "javaVersion" to requiredJava)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(requiredJava)
}
