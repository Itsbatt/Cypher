# Cypher v1.0.0 — Initial Public Release 🎵

Welcome to the first official release of **Cypher**, a modern, high-fidelity music player and resilient streaming client designed for Android 13+ (API 33-36).

---

## 📦 Downloads & Artifacts

| Asset | Description | Recommended For |
|---|---|---|
| **`Cypher-v1.0.0-release.apk`** | Production build, optimized, signed with universal debug keystore for easy installation. | **All users** |
| **`Cypher-v1.0.0-debug.apk`** | Debug build with extended logging and diagnostics enabled. | **Developers & Bug Reporting** |

> **System Requirements:** Android 13 (API 33) or higher (tested up to Android 16 / Vanilla Ice Cream & Baklava).

---

## ✨ What's New in v1.0.0

### 🎨 Visual Identity & AGSL Shaders
- **Cypher Dark Theme:** Default dark theme featuring deep violet and dark lavender accents (`#9D7BB0`) and OLED pitch-black background (`#0D0B11`).
- **AGSL Liquid Glass UI:** Hardware-accelerated background shaders (`RuntimeShader`) with real-time chromatic aberration, optical dispersion, and dynamic Palette color extraction.
- **Debounced Haptics:** Fluid elastic scrubbing with debounced haptic feedback preventing hardware saturation.

### ⚡ Media Engine & Bug Fixes
- **Scrubber & Timeline Stabilization:** Resolved dual-tracker race condition between background media session keeps and YouTube player timestamps, ensuring smooth, non-jumping duration scrubbing.
- **Synchronized Live Lyrics:** Smooth auto-scrolling lyrics powered by LRCLIB with user-scroll detection guards.
- **Classical Demo Track Purge:** Removed mock demo tracks (Beethoven, Satie, Chopin) on initial install and purged legacy entries from local databases.

### 🛡️ Streaming Resilience & Telemetry
- **Passive Telemetry Monitor:** Real-time health monitoring of playback streams detecting HTTP 403 or SABR disruptions.
- **4-Tier Fallback Mechanism:** Graceful degradation across `yt-dlp`, InnerTube direct TVHTML5 endpoint, and official IFrame carrier.

### 🔒 Storage & Database Refactor
- **Private Offline Vault:** Scoped Storage migration supporting removable MicroSD storage for FLAC, WAV, and MP3 files with automatic `.nomedia` generation.
- **Room v3 Relational Engine:** Clean single-user architecture utilizing `@Relation` and `@Junction` for playlists, tracks, and listening history.

---

## 🛠️ Verification & Testing
- Fully compiled with **JDK 21** and **Gradle 9.1.0**.
- Validated on a physical **Samsung Galaxy S22 5G** (`SM-S901E`, Android 16 / One UI 8 / SDK 36).

---

*Full project documentation: [PROJECT_DOCUMENTATION.md](PROJECT_DOCUMENTATION.md) | File mapping: [MAPEO_ARCHIVOS.md](MAPEO_ARCHIVOS.md)*
