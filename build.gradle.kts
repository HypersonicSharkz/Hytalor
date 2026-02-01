import app.ultradev.hytalegradle.manifest.ManifestExtension

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.3.1"
    id("app.ultradev.hytalegradle") version "1.6.8"
}

group = "com.hypersonicsharkz"
version = "2.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
    implementation("com.jayway.jsonpath:json-path:2.10.0")

    testImplementation(files("D:\\Hytalor\\build\\hytale\\HytaleServer.jar"))
}

tasks.test {
    useJUnitPlatform()
}

hytale {
    // Add `--allow-op` to server args (allows you to run `/op self` in-game)
    allowOp.set(true)

    // Set the patchline to use, currently there are "release" and "pre-release"
    patchline.set("pre-release")

    // Load mods from the local Hytale installation
    includeLocalMods.set(false)

    serverJar.set(file("D:\\hytale-downloader\\2026.01.28-87d03be09\\Server\\HytaleServer.jar"))
    assetsZip.set(file("D:\\hytale-downloader\\2026.01.28-87d03be09\\Assets.zip"))

    // Replace the version in the manifest with the project version
    manifest {
        version.set(project.version.toString())
        authors.set(listOf(
            ManifestExtension.AuthorInfo("HypersonicSharkz", null, "https://www.curseforge.com/members/hypersonicsharkz")
        ))
        website.set("https://github.com/HypersonicSharkz/Hytalor")
    }
}