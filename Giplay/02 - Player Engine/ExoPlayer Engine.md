---
tags:
  - "#engine"
---

# 🚀 ExoPlayer Engine (Media3)

**ExoPlayer Engine** diimplementasikan menggunakan pustaka resmi AndroidX Media3 (`androidx.media3:media3-exoplayer:1.5.1`).

---

## 🔑 Fitur Utama
* **Hardware Accelerated Decoding**: Memanfaatkan chipset MediaTek / Snapdragon / Exynos secara optimal untuk menghemat pemakaian baterai.
* **HDR Color Detection**: Mendeteksi metadata warna `ColorInfo` (HDR10, HLG, Dolby Vision) dan menampilkan indikator status di [[Player Screen]].
* **Seamless Track Selection**: Pergantian jalur audio dan subtitle secara instan tanpa perlu memuat ulang (*rebuffering*) video.

## 🔗 Keterhubungan Komponen
* Dikendalikan dari: [[Player Screen]]
* Pengendali interupsi suara: [[Audio Focus]]
* Bagian dari arsitektur: [[Dual Engine Concept]]
* Referensi pustaka: [[Tech Stack & Dependencies]]
