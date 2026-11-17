import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "com.cypher"
version = "1.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    // QR for sharing a playlist without any server.
    implementation("com.google.zxing:core:3.5.3")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Tag reading (mp3, m4a, flac, ogg) without native code.
    implementation("net.jthink:jaudiotagger:3.0.1")
    // Native Windows calls (DWM dark caption) without shipping a custom runtime.
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    // Embedded Chromium (JCEF) for the YouTube player: the phone uses an iframe and the
    // desktop has to do the same, because YouTube needs MSE/mp4-audio that JavaFX lacks.
    implementation("me.friwi:jcefmaven:122.1.10")
    // Audio playback for the desktop: JavaFX media ships its own decoders.
    implementation("org.openjfx:javafx-base:21.0.5:win")
    implementation("org.openjfx:javafx-graphics:21.0.5:win")
    implementation("org.openjfx:javafx-media:21.0.5:win")

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.cypher.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            // The OAuth loopback listener uses com.sun.net.httpserver, which jpackage
            // leaves out unless the module is requested explicitly.
            modules("jdk.httpserver", "java.net.http")
            packageName = "Cypher"
            packageVersion = "1.1.0"
            description = "Cypher Desktop"
            vendor = "itsbatt"
            windows {
                // Same logo as the app window and the Android launcher.
                iconFile.set(project.file("packaging/cypher.ico"))
                // Per-user install: no administrator prompt, and uninstalling only removes
                // the app and its shortcuts, never %APPDATA%\Cypher (the library).
                perUserInstall = true
                menuGroup = "Cypher"
                shortcut = true
                dirChooser = true
                // Stable identity so upgrades replace the previous version instead of
                // leaving two Cypher entries in Apps & features.
                upgradeUuid = "7c1f9a52-6f1b-4c59-9b0e-2f5f2f7b6a10"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

/** Portable build: the app image created by jpackage, zipped, no Java required. */
val portableZip by tasks.registering(Zip::class) {
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app"))
    archiveFileName.set("Cypher-${project.version}-windows-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}

tasks.register("packagePortable") {
    dependsOn(portableZip)
    doLast {
        println("Portable: " + layout.buildDirectory.dir("dist").get().asFile.absolutePath)
    }
}
