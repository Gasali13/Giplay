# Giplay 🎬

[![Download APK](https://img.shields.io/badge/Download-Giplay%20APK%20(v2.0)-2ea44f?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Gasali13/Giplay/releases/latest)
[![Release](https://img.shields.io/github/v/release/Gasali13/Giplay?style=for-the-badge&color=blue)](https://github.com/Gasali13/Giplay/releases/latest)

**Giplay** adalah aplikasi pemutar video Android berkinerja tinggi yang dirancang khusus untuk memutar koleksi anime dan video lokal dengan dukungan **Dual Engine** (ExoPlayer & MPV), antarmuka modern berbasis **Jetpack Compose**, serta efek visual **Liquid Glass AGSL** dan animasi halus bergaya iOS.

---

## ✨ Pembaruan Utama v2.0

- **Migrasi Penuh ke Jetpack Compose & Liquid Glass UI:** Seluruh layar Library, Daftar Episode, Player, Bottom/Side Sheet adaptif, dan dialog dirancang ulang dengan Jetpack Compose + shader AGSL real-time (*PixelCopy* video backdrop blur pada kontrol pemutar & gradasi blur halus pada header).
- **Gerakan & Transisi Halus Bergaya iOS:** Kurva *spring* dan *easing* seragam di seluruh navigasi, sheet, press feedback, dan overlay.
- **Kontrol Pemutar & PiP Lengkap:** *Gesture* volume/kecerahan/seek, *press-and-hold speed control* (0.25x–4.0x), *skip* +85 detik, deteksi lagu latar, kunci layar, *inactivity prompt*, serta mode **Picture-in-Picture** dengan rasio aspek otomatis dan tombol kontrol interaktif (`RemoteAction`).
- **Dukungan Storage & Judul Lebih Baik:** Dekode nama file *percent-encoded* (UTF-8), pembacaan nama file asli dari File Manager eksternal, dukungan akses media terbatas Android 14+, serta *disk cache* thumbnail & durasi persisten.

---

## 🛠️ Spesifikasi Teknis

- **Versi:** 2.0 (`versionCode = 3`)
- **Bahasa & UI:** Kotlin 2.0.21 + Jetpack Compose (BOM 2024.12.01) & Material 3
- **Minimum SDK:** Android 7.0 (API Level 24)
- **Target SDK:** Android 15 (API Level 35)
- **Arsitektur:** MVVM (Model-View-ViewModel) dengan Kotlin Coroutines & Flow
- **Penyimpanan Lokal:** Room Database (migrasi skema otomatis) & DataStore Preferences
- **Build System:** Gradle 8.10.2 + AGP 8.7.3 (Gradle Version Catalog `libs.versions.toml`)
- **Engine Pemutar:** AndroidX Media3 1.5.1 (HDR10+ / Dolby Vision) & MPV Android Lib 0.1.12 (ASS/SSA)
- **Arsitektur CPU:** `arm64-v8a` (APK Release R8 teroptimasi ~43 MB)

---

## 📄 Lisensi

Proyek ini dibuat untuk keperluan penggunaan personal dan eksperimen pemutar video Android.
