# Giplay 🎬

**Pemutar video dan anime lokal untuk Android dengan Dual Engine (ExoPlayer + MPV), Jetpack Compose, dan Liquid Glass UI.**

[![Download APK](https://img.shields.io/badge/Download-Giplay%20APK-2ea44f?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Gasali13/Giplay/releases/latest)
[![Release](https://img.shields.io/github/v/release/Gasali13/Giplay?style=for-the-badge&color=0969da)](https://github.com/Gasali13/Giplay/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3ddc84?style=for-the-badge&logo=android&logoColor=white)](#%EF%B8%8F-spesifikasi-teknis)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7f52ff?style=for-the-badge&logo=kotlin&logoColor=white)](#%EF%B8%8F-spesifikasi-teknis)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge)](LICENSE)

---

## 📖 Tentang Giplay

**Giplay** adalah aplikasi pemutar video Android yang dirancang khusus untuk memutar koleksi anime dan video lokal dengan lancar. Aplikasi ini menggabungkan dua mesin pemutar (**AndroidX Media3 / ExoPlayer** dan **libmpv**) dalam antarmuka modern berbasis **Jetpack Compose** dengan efek visual **Liquid Glass AGSL** dan animasi halus bergaya iOS.

---

## 📸 Tangkapan Layar

> *Tambahkan file screenshot ke folder [`docs/screenshots/`](docs/screenshots/README.md) lalu hapus komentar HTML di bawah ini agar galeri tampil di README.*

<!--
| Library | Daftar Episode | Video Player | Panel Kontrol |
| :---: | :---: | :---: | :---: |
| ![Library](docs/screenshots/library.png) | ![Episode List](docs/screenshots/episodes.png) | ![Player](docs/screenshots/player.png) | ![Controls](docs/screenshots/controls.png) |
-->

---

## ✨ Fitur Utama

### ⚡ Dual Engine Pemutar (ExoPlayer + MPV)
Giplay mendukung perpindahan engine sesuai kebutuhan format video dan subtitle:

| Engine | Keunggulan Utama | Cocok Untuk |
| :--- | :--- | :--- |
| **ExoPlayer (Media3)** | Akselerasi hardware native, hemat daya, dukungan HDR10+ & Dolby Vision | Video MP4/MKV standar, HDR, dan pemutaran hemat baterai |
| **MPV (`libmpv`)** | Rendering subtitle **ASS/SSA** akurat (`libass`), dukungan format & codec luas (`FFmpeg`) | Koleksi anime dengan subtitle berformat/efek khusus & multi-track kompleks |

### 🎨 Antarmuka Jetpack Compose & Liquid Glass
- **Liquid Glass AGSL Shader:** Efek blur *real-time* di belakang kontrol pemutar (*PixelCopy* video backdrop) dan gradasi blur halus pada header.
- **Tata Letak Adaptif:** Bottom Sheet saat portrait dan Side Sheet saat landscape untuk pemilihan trek, kecepatan, dan pengaturan.
- **Animasi Halus Bergaya iOS:** Kurva *spring* dan *easing* konsisten di seluruh transisi, overlay, dan umpan balik sentuhan.
- **Glowing Seekbar:** Bilah progres kustom dengan efek pendaran cahaya.

### 🎮 Kontrol Pemutar & Picture-in-Picture (PiP)
- **Gestur Intuitif:** Geser untuk volume, kecerahan layar, dan *scrub seeking*; ketuk ganda untuk lompat waktu.
- **Kontrol Kecepatan & Skip Cepat:** *Press-and-hold* untuk kontrol kecepatan `0.25x–4.0x` serta tombol *skip* `+85 detik` (cocok untuk melewati OP/ED anime).
- **Picture-in-Picture Interaktif:** Rasio aspek otomatis mengikuti video dengan tombol kontrol `RemoteAction` (Mundur 10d, Putar/Jeda, Maju 10d, Episode Berikutnya).
- **Audio & Utilitas:** Manajemen *Audio Focus* (otomatis jeda saat telepon masuk / headset terlepas), kunci layar, dan *inactivity prompt*.

### 📂 Pustaka & Manajemen Media Lokal
- **Pengurutan Alami (*Natural Order*):** Urutan episode rapi (`Episode 1, 2, ..., 10` bukan `1, 10, 2`).
- **Riwayat & Progres Otomatis:** Posisi tontonan disimpan lokal menggunakan Room Database.
- **Kompatibilitas Storage Modern:** Mendukung Scoped Storage, akses media terbatas Android 14+ (*Visual User Selected*), dekode nama file *percent-encoded* UTF-8, serta pemutaran langsung dari File Manager eksternal (`ACTION_VIEW`).
- **Performa Cepat:** *Disk cache* thumbnail & durasi persisten dengan batas LRU `45 MB`.

---

## 🚀 Pembaruan Terbaru (v2.0)

- Migrasi penuh seluruh layar ke **Jetpack Compose + Liquid Glass AGSL UI**.
- Penambahan kontrol **Picture-in-Picture (`RemoteAction`)** dan rasio aspek otomatis.
- Perbaikan pembacaan nama file dari File Manager eksternal, manajemen siklus hidup `ParcelFileDescriptor` pada MPV, dan dukungan penuh izin media terbatas Android 14+.
- Optimasi performa (*disk cache* thumbnail LRU `45 MB`, isolasi state timeline player, dan penghapusan dependensi View lawas).

Lihat riwayat rilis lengkap di [GitHub Releases](https://github.com/Gasali13/Giplay/releases).

---

## 📲 Instalasi

1. Buka halaman [**Releases terbaru**](https://github.com/Gasali13/Giplay/releases/latest).
2. Unduh berkas `.apk` terbaru (khusus perangkat **`arm64-v8a`**, Android 7.0 atau lebih baru).
3. Pasang APK di perangkat Android Anda (aktifkan izin *Install unknown apps* jika diminta).

---

## 🛠️ Cara Build dari Source

### Prasyarat
- **JDK 17**
- **Android Studio** (disarankan Ladybug atau lebih baru) dengan **Android SDK 35**
- Perangkat/emulator Android berarsitektur `arm64-v8a` (API 24+)

### Langkah Build
1. **Clone repositori:**
   ```bash
   git clone https://github.com/Gasali13/Giplay.git
   cd Giplay
   ```
2. **Build APK Debug:**
   ```bash
   ./gradlew assembleDebug
   ```
   *(Di Windows PowerShell/CMD gunakan `.\gradlew.bat assembleDebug`)*
3. **Jalankan Unit Test:**
   ```bash
   ./gradlew testDebugUnitTest
   ```
4. **Build APK Release (dengan Keystore sendiri):**
   Salin [keystore.properties.example](keystore.properties.example) menjadi `keystore.properties`, isi kredensial keystore Anda, lalu jalankan:
   ```bash
   ./gradlew assembleRelease
   ```
   > **Catatan:** Jangan pernah men-commit `keystore.properties` atau berkas `.jks`/`.keystore` ke Git (sudah diabaikan otomatis di `.gitignore`). Jika `keystore.properties` tidak tersedia, build release akan menggunakan debug signing sebagai fallback lokal.

---

## ⚙️ Spesifikasi Teknis

| Komponen | Detail |
| :--- | :--- |
| **Versi** | `2.0` (`versionCode = 3`) |
| **Bahasa & UI** | Kotlin `2.0.21` · Jetpack Compose (BOM `2024.12.01`) · Material 3 |
| **SDK** | Min SDK `24` (Android 7.0) · Compile & Target SDK `35` (Android 15) |
| **Arsitektur** | MVVM · Kotlin Coroutines & Flow (`1.9.0`) |
| **Engine Pemutar** | AndroidX Media3 `1.5.1` (ExoPlayer) & `mpv-android-lib` `0.1.12` |
| **Penyimpanan** | Room Database `2.6.1` (KSP) & DataStore Preferences `1.1.1` |
| **Build System** | Gradle `8.10.2` · AGP `8.7.3` · Version Catalog (`libs.versions.toml`) |
| **Target ABI** | `arm64-v8a` (APK Release R8 teroptimasi ~43 MB) |

> **Dokumentasi Internal:** Catatan arsitektur mendalam, desain engine, dan roadmap tersedia di folder [`Giplay/`](Giplay/Giplay%20Dashboard.md) (diformat sebagai *Obsidian Vault*).

---

## 📄 Lisensi

Proyek ini dilisensikan di bawah **GNU General Public License v3.0 (GPL-3.0)** — lihat berkas [LICENSE](LICENSE) untuk rincian lengkapnya.
