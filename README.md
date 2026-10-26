# Giplay 🎬

**Giplay** adalah aplikasi pemutar video Android berkinerja tinggi yang dirancang khusus untuk memutar anime dan video lokal dengan dukungan **Dual Engine** (ExoPlayer & MPV).

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

## 📦 Menjalankan Proyek

1. Clone repositori ini:
   ```bash
   git clone https://github.com/Gasali13/Giplay.git
   ```
2. Buka proyek di **Android Studio** (Koala / Ladybug atau versi terbaru).
3. Sambungkan perangkat Android fisik dengan mode **USB Debugging** aktif atau gunakan Emulator Android (API 24+).
4. Klik tombol **Run** atau jalankan perintah Gradle:
   ```bash
   ./gradlew installDebug
   ```

---

## 📄 Lisensi

Proyek ini dibuat untuk keperluan penggunaan personal dan eksperimen pemutar video Android.
