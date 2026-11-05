# Cypher 🎵

> **Cypher** is a modern, high-fidelity music player and resilient streaming client for Android 13+ (API 33-36).  
> Built with **Jetpack Compose**, **Material 3**, **AndroidX Media3**, native **AGSL GPU shaders**, and **Room v3**.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2013%2B%20(API%2033%2B)-green.svg)](https://android.com)
[![Build Tool](https://img.shields.io/badge/Gradle-9.1.0-brightgreen.svg)](https://gradle.org)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.2%20%7C%20Java%2021-purple.svg)](https://kotlinlang.org)

---

## ✨ Features

- **Hybrid Streaming & Local Engine:** Seamlessly combines on-device lossless audio (FLAC, WAV, ALAC, MP3, AAC) with global YouTube Music streaming.
- **Stream Resilience & Telemetry:** Passive telemetry monitor detecting HTTP 403 or SABR disruptions with 4-tier automatic fallback (yt-dlp, InnerTube direct TVHTML5 endpoint, official IFrame carrier).
- **Private Offline Vault:** Scoped Storage support prioritizing removable MicroSD storage for heavy downloads with guaranteed `.nomedia` shielding.
- **AGSL Liquid Glass UI:** Real-time GPU-accelerated background shaders with optical dispersion, chromatic aberration, and dynamic color extraction using AndroidX Palette.
- **Live Synced Lyrics:** Real-time line-by-line synchronized lyrics powered by LRCLIB.
- **Room v3 Persistence:** Modern relational database architecture using `@Relation` and `@Junction` for playlists, tracks, listening history, and analytics.
- **Android Auto Ready:** Built on `MediaLibraryService` with automotive category hierarchy and lockscreen media transport controls.
- **Single-User Focus:** Clean, unencumbered experience with personalized welcome, listening stats, and customizable color themes (defaulting to Cypher Dark Lavender `#9D7BB0` and OLED pitch black `#0D0B11`).

---

## 🛠️ Tech Stack & Architecture

- **UI Layer:** Jetpack Compose, Material 3, Android Graphics Shading Language (AGSL `RuntimeShader`).
- **Media Engine:** AndroidX Media3 (`ExoPlayer`, `MediaLibrarySession`, `PlaybackService`).
- **Data & Persistence:** AndroidX Room v3, Coroutines, StateFlow.
- **Network & Streaming:** InnerTube direct JSON client, `yt-dlp-android`, `ffmpeg-kit`, Cloudflare DoH DNS.
- **Lyrics:** LRCLIB REST Client with synchronized `.lrc` parsing.

---

## 🚀 Building & Running

### Prerequisites
- **JDK 21**
- **Android SDK (Compile & Target 36, Min 33)**
- A physical or virtual device running Android 13 or newer.

### Commands

Clone the repository:
```bash
git clone https://github.com/Itsbatt/cypher.git
cd cypher
```

Build debug APK:
```powershell
.\gradlew.bat assembleDebug
```

Install onto your connected Android device:
```powershell
adb install -r -d app\build\outputs\apk\debug\app-debug.apk
```

---

## 📖 Project Documentation

For a detailed technical breakdown of the architecture, migrations, and complete development history, please consult:
- [**`PROJECT_DOCUMENTATION.md`**](PROJECT_DOCUMENTATION.md) — Comprehensive technical chronicle and architecture breakdown.
- [**`MAPEO_ARCHIVOS.md`**](MAPEO_ARCHIVOS.md) — Concise reference index of all 85+ components, screens, ViewModels, and services.

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.
