# Giplay 🎬

[![Download APK](https://img.shields.io/badge/Download-Giplay%20APK%20(v1.0.0)-2ea44f?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Gasali13/Giplay/releases/latest)
[![Release](https://img.shields.io/github/v/release/Gasali13/Giplay?style=for-the-badge&color=blue)](https://github.com/Gasali13/Giplay/releases/latest)

**Giplay** adalah aplikasi pemutar video Android berkinerja tinggi yang dirancang khusus untuk memutar anime dan video lokal dengan dukungan **Dual Engine** (ExoPlayer & MPV). Proyek ini dibangun dengan bantuan **Antigravity** dan mengadopsi kenyamanan kontrol pemutar video dengan referensi utama dari **MX Player**.

---

## 💡 Inspirasi & Pengembangan

- 🤖 **Dibangun Menggunakan Antigravity:** Proyek ini dikembangkan, diaudit, dan dioptimalkan secara kolaboratif menggunakan **Google Antigravity**.
- Referensi sebagian besar **MX Player**

---

## ✨ Fitur Utama

- 🚀 **Dual Engine Playback:**
  - **ExoPlayer (AndroidX Media3):** Akselerasi perangkat keras optimal, efisiensi baterai, dan dukungan HDR10+.
  - **MPV (libmpv + libass):** Rendering subtitle tingkat lanjut (dukungan penuh font, animasi, dan styling ASS/SSA).
- 📑 **Library & Episode Scanner:**
  - Pemindaian cerdas folder video dan seri anime.
  - Pengurutan alami (Natural Order) berdasarkan Nama, Terbaru, atau Terlama.
  - Navigasi cepat dengan bilah indeks alfabetik (A-Z Fast Scroller).
- 🖐️ **Kontrol Gestur Interaktif:**
  - Geser vertikal di sisi kiri untuk mengatur kecerahan layar.
  - Geser vertikal di sisi kanan untuk mengatur volume.
  - Ketuk dua kali (Double-tap) untuk melompat 10 detik ke depan/belakang.
  - Tekan dan tahan (Long-press) untuk mempercepat video (2x Speed).
- 💬 **Subtitle & Audio:**
  - Deteksi otomatis subtitle eksternal (`.srt`, `.ass`, `.vtt`, `.ssa`).
  - Pemilih trek audio dan subtitle yang intuitif.
- 🕒 **Playback State:**
  - Melanjutkan posisi tonton terakhir secara otomatis (Resume Playback).
  - Indikator riwayat tontonan per episode.
- 📱 **Mode Tampilan Fleksibel:**
  - Berbagai pilihan rasio aspek: Fit, Fill, Stretch, Zoom, 16:9, dan 4:3.
  - Penguncian orientasi layar sensor otomatis.
  - Mode Picture-in-Picture (PiP).

---

## 🛠️ Spesifikasi Teknis

- **Bahasa:** Kotlin
- **Minimum SDK:** Android 7.0 (API Level 24)
- **Target SDK:** Android 15 (API Level 35)
- **Arsitektur:** MVVM (Model-View-ViewModel) dengan Kotlin Coroutines & Flow
- **Penyimpanan Lokal:** Room Database & DataStore Preferences
- **Build System:** Gradle Kotlin DSL (KSP)

---

## 📄 Lisensi

Proyek ini dibuat untuk keperluan penggunaan personal dan eksperimen pemutar video Android.
