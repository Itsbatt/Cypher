# Cypher 🎵

> **Cypher** is a modern, high-fidelity music player and resilient streaming client for Android 13+ (API 33-36).
> Built with **Jetpack Compose**, **Material 3**, **AndroidX Media3**, native **AGSL GPU shaders**, and **Room (Kotlin)**.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2013%2B%20(API%2033%2B)-green.svg)](https://android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.2%20%7C%20Java%2021-purple.svg)](https://kotlinlang.org)

---

## ✨ Features

- **Hybrid streaming & local engine:** on-device lossless audio (FLAC, WAV, ALAC, MP3, AAC) plus YouTube Music streaming through InnerTube/yt-dlp with automatic fallbacks.
- **Audio DSP:** -14 LUFS loudness normalization, 5-band equalizer with presets and bass boost.
- **Gestures:** swipe the artwork or the MiniPlayer left/right to change track, with a short slide transition while the incoming artwork follows the finger.
- **Queue:** search inside the queue and reorder upcoming tracks with a long-press drag.
- **Spotify migration without an account:** import a ZIP of playlists or a single CSV/JSON export, or paste a public playlist link. No OAuth, no Client ID, no Premium.
- **Private offline vault:** downloads on internal or removable MicroSD storage with `.nomedia` shielding.
- **AGSL Liquid Glass UI:** GPU-accelerated background shaders with palette-driven ambient colors.
- **Live synced lyrics** from LRCLIB, in-app equalizer, sleep timer, Android Auto media library, QR playlist sharing and Cypher Wrapped.
- **In-app updates:** checks GitHub Releases, verifies the APK signature/hash and hands it to the system installer.

---

## 📦 Download

Get the latest signed APK from the **[Releases page](https://github.com/Itsbatt/Cypher/releases/latest)**:

- `app-arm64-v8a-release.apk` — 64-bit ARM devices (most phones).
- `app-universal-release.apk` — every supported ABI.
- `SHA256SUMS.txt` — checksums for both files.

Verify before installing:

```bash
sha256sum -c SHA256SUMS.txt
```

Current version: **1.0.8** (`versionCode 10`). The app also offers the update in Settings when a newer release is published.

---

## 🚀 Building

### Prerequisites
- **JDK 17+** (the project builds with the bundled Adoptium 17)
- **Android SDK** with compile/target SDK 36, min SDK 33
- A physical or virtual device running Android 13 or newer

```bash
git clone https://github.com/Itsbatt/Cypher.git
cd Cypher
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

Release builds are signed with the keystore declared in `keystore.properties` (both files are git-ignored).

---

## 📖 Documentation

- [`PROJECT_DOCUMENTATION.md`](PROJECT_DOCUMENTATION.md) — architecture, data model and release history.
- [`docs/releases/`](docs/releases/) — notes for every published version.

## 📄 License

GNU General Public License v3.0 — see [`LICENSE`](LICENSE).
