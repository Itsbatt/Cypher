import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "com.cypher"
version = "1.1.1"

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

// ---------------------------------------------------------------------------------------
// CEF natives inside the installer.
//
// jcefmaven downloads Chromium (~150 MB) the first time a song is streamed, which fails
// on offline machines, behind corporate proxies or when GitHub is blocked. The portable
// ZIP and the MSI therefore carry the already-extracted runtime: YouTube playback works
// from the first second, and `YouTubeEmbed` just points jcefmaven at the bundled folder.
//
// The natives jar is build-time only: it never ships inside the app image, so the
// distribution is the extracted tree instead of the archive plus the tree.
// ---------------------------------------------------------------------------------------
val cefNativesTag = "jcef-0b8e42e+cef-122.1.10+gc902316+chromium-122.0.6261.112"

val cefNatives by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    cefNatives("me.friwi:jcef-natives-windows-amd64:$cefNativesTag")
}

val cefBundleDir = layout.buildDirectory.dir("cef/windows")

/** Unpacks the CEF runtime that ships inside the Windows deliverables. */
val extractCef by tasks.registering {
    group = "distribution"
    description = "Extracts the CEF natives that are packaged with the installer."
    inputs.files(cefNatives)
    outputs.dir(cefBundleDir)
    doLast {
        val target = cefBundleDir.get().asFile
        target.deleteRecursively()
        target.mkdirs()

        // The natives artifact is a jar whose payload is the .tar.gz jcefmaven would
        // otherwise download, so the same archive is reused (no re-compression step).
        val staged = File(temporaryDir, "staged").apply { deleteRecursively(); mkdirs() }
        copy {
            from(zipTree(cefNatives.singleFile))
            into(staged)
        }
        val tarball = fileTree(staged).matching { include("**/*.tar.gz") }.files.firstOrNull()
            ?: error("No CEF natives .tar.gz inside ${cefNatives.singleFile.name}")
        copy {
            from(tarTree(resources.gzip(tarball)))
            into(target)
        }
        require(File(target, "libcef.dll").isFile && File(target, "jcef.dll").isFile) {
            "CEF extraction incomplete: libcef.dll or jcef.dll missing in $target"
        }
    }
}

/** Layout Compose expects for app resources: <root>/windows/<folder>. */
val cefResources by tasks.registering(Sync::class) {
    from(extractCef) { into("cef") }
    into(layout.buildDirectory.dir("cef-resources/windows"))
}

// The compose plugin copies appResourcesRootDir inside prepareAppResources, so that task
// (and every deliverable) must run after the CEF folder exists.
listOf(
    "prepareAppResources",
    "createDistributable",
    "packageMsi",
    "packageExe",
    "runDistributable"
).forEach { name ->
    tasks.matching { it.name == name }.configureEach { dependsOn(cefResources) }
}

compose.desktop {
    application {
        mainClass = "com.cypher.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            // Chromium travels with the app: no first-run download.
            appResourcesRootDir.set(layout.buildDirectory.dir("cef-resources"))
            // The OAuth loopback listener uses com.sun.net.httpserver, which jpackage
            // leaves out unless the module is requested explicitly.
            modules("jdk.httpserver", "java.net.http")
            packageName = "Cypher"
            packageVersion = "1.1.1"
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
