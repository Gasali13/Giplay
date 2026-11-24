---
tags:
  - "#hub"
  - "#core"
title: Giplay - Video Player Dashboard
---

# 🎬 Giplay - Project Knowledge Hub

Selamat datang di Pusat Pengetahuan Proyek **Giplay**!  
Giplay adalah aplikasi pemutar video Android berkinerja tinggi yang dirancang khusus untuk memutar koleksi anime dan video lokal dengan dukungan **Dual Engine (ExoPlayer & MPV)** serta kontrol gestur terinspirasi oleh MX Player.

---

## 🧭 Peta Navigasi Sistem

### 🏗️ 1. Arsitektur & Teknologi
* [[Arsitektur Sistem]]: Gambaran pola arsitektur MVVM, pemisahan layer, dan aliran data reaktif.
* [[Tech Stack & Dependencies]]: Daftar pustaka utama (Media3, MPV, Room, DataStore, KSP, Coil).

### ⚡ 2. Mesin Pemutar (Player Engine)
* [[Dual Engine Concept]]: Alasan dan strategi pemilihan antara ExoPlayer & MPV.
* [[ExoPlayer Engine]]: Engine AndroidX Media3 untuk efisiensi daya & kompatibilitas tinggi.
* [[MPV Engine]]: Engine libmpv untuk rendering subtitle anime berformat ASS/SSA tingkat lanjut.
* [[Gesture & Controls]]: Kontrol swipe gesture volume, kecerahan, scrub seeking, dan double tap.
* [[Audio Focus]]: Penanganan audio focus, panggilan telepon masuk, dan pelepasan headset (*noisy event*).

### 💾 3. Data & Penyimpanan
* [[Room Database]]: Penyimpanan histori tontonan lokal dan persistensi progres durasi video.
* [[DataStore Preferences]]: Penyimpanan preferensi pengguna (engine default, gaya subtitle, sorting).
* [[Media Repository]]: Pemindaian file video pada Scoped Storage Android dan pengelompokan episode.
* [[Natural Order Sorting]]: Algoritma pengurutan episode yang akurat (Episode 1, 2, ... 10).

### 🎨 4. Tampilan & Pengalaman Pengguna (UI/UX)
* [[Library Screen]]: Galeri utama judul anime dilengkapi *Alphabet Fast Index* dan pencarian.
* [[Episode List Screen]]: Tampilan daftar berkas episode per judul dengan indikator progress tontonan.
* [[Player Screen]]: Tampilan pemutar video dengan kontrol dinamis, overlay, dan indikator HDR.
* [[Glowing Seekbar]]: Komponen visual seekbar kustom dengan efek pendaran cahaya.
* [[Track Selection & Subtitles]]: Pengaturan trek multi-audio dan kustomisasi gaya teks terjemahan.

### 📋 5. Manajemen & Perencanaan Proyek
* [[Roadmap & Rencana Fitur]]: Rencana pengembangan lanjutan (PiP, LAN Streaming, AniList sync).
* [[Changelog v2.0]]: Riwayat perubahan rilis mayor v2.0 (Jetpack Compose, Liquid Glass, PiP Actions, & optimasi).
* [[Changelog v1.0.1]]: Riwayat perubahan rilis stabil v1.0.1 dan optimasi biner arm64.

---

> [!TIP]
> **Cara melihat Jaring-jaring (Graph View):**  
> Tekan **`Ctrl + G`** pada keyboard Anda atau klik ikon jaring di sidebar sebelah kiri untuk melihat peta keterhubungan seluruh modul proyek ini secara visual.
