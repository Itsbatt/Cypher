import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
  if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { load(it) }
  }
}
val debugSignRelease = providers.gradleProperty("cypherDebugSigning").orNull == "true"
// The emulator runs x86_64; shipping APKs stay ARM-only unless this flag is passed.
val emulatorAbi = providers.gradleProperty("cypherEmulatorAbi").orNull == "true"
val supportedAbis = buildList {
  add("arm64-v8a")
  add("armeabi-v7a")
  if (emulatorAbi) add("x86_64")
}

android {
  namespace = "com.cypher.music"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.cypher.music"
    minSdk = 33
    targetSdk = 36
    versionCode = 11
    versionName = "1.0.8"

    // Identifies this exact compilation inside the APK. Two builds that only differ in
    // timestamp get a different id, so a rebuilt 1.0.6 can be told apart from the first one.
    val buildStamp = providers.environmentVariable("CYPHER_BUILD_STAMP").orNull
      ?: System.currentTimeMillis().toString()
    buildConfigField(
      "String",
      "BUILD_ID",
      "\"" + Integer.toHexString((versionName + buildStamp).hashCode()) + "\""
    )

    javaCompileOptions {
      annotationProcessorOptions {
        arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
      }
    }

    ndk {
      abiFilters.addAll(supportedAbis)
    }
  }

  signingConfigs {
    if (keystorePropertiesFile.exists()) {
      create("release") {
        storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
        storePassword = keystoreProperties.getProperty("storePassword")
        keyAlias = keystoreProperties.getProperty("keyAlias")
        keyPassword = keystoreProperties.getProperty("keyPassword")
      }
    }
  }

  splits {
    abi {
      isEnable = true
      reset()
      include(*supportedAbis.toTypedArray())
      isUniversalApk = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = when {
        debugSignRelease -> signingConfigs.getByName("debug")
        keystorePropertiesFile.exists() -> signingConfigs.getByName("release")
        else -> null
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  sourceSets {
    getByName("androidTest") {
      assets.srcDir("$projectDir/schemas")
    }
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    jniLibs {
      useLegacyPackaging = true
    }
  }

}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  // Required transitively by appcompat (yt-dlp); pinned so lint accepts the ActivityResult APIs.
  implementation("androidx.fragment:fragment:1.8.5")

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  // Real org.json on the JVM so release parsing can be unit tested (Android only stubs it).
  testImplementation("org.json:json:20240303")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation("androidx.room:room-testing:2.6.1")

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Media3 & Background Audio
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.session)
  implementation(libs.media3.ui)
  implementation(libs.media3.common)
  implementation("androidx.media3:media3-datasource:1.5.1")
  implementation("androidx.media3:media3-database:1.5.1")
  implementation("androidx.media:media:1.7.0")
  implementation("com.google.guava:guava:33.3.1-android")
  implementation("androidx.palette:palette-ktx:1.0.0")

  // Image Loading
  implementation(libs.coil.compose)

  // YouTube Player Engine (Official IFrame audio/video stream)
  implementation(libs.youtube.player)

  // Networking
  implementation(libs.okhttp)

  // Extended Icons
  implementation(libs.androidx.compose.material.icons.extended)

  // Security & Crypto
  implementation("androidx.security:security-crypto:1.1.0")

  // Google Code Scanner (Play Services)
  implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

  // Room Database
  implementation("androidx.room:room-runtime:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")
  annotationProcessor("androidx.room:room-compiler:2.6.1")

  // QR Code Generation (ZXing)
  implementation("com.google.zxing:core:3.5.3")

  // yt-dlp on Android (Native Engine & FFmpeg)
  implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
  implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
}
