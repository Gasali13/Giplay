---
tags:
  - "#ui"
---

# 💬 Track Selection & Subtitle Styling

Fitur manajemen trek audio dan terjemahan (`bottom_sheet_tracks.xml` & `bottom_sheet_subtitle_style.xml`) memungkinkan fleksibilitas penuh saat menonton film atau serial anime luar negeri.

---

## 🎧 Pemilihan Trek Audio
* Mendukung video multi-bahasa (misal: Audio Bahasa Jepang & Audio Dubbing Indonesia/Inggris).
* Deteksi nama bahasa dan codec audio otomatis (AAC, AC3, DTS, Opus, FLAC).

## 🔤 Kustomisasi Subtitle
* Pemilihan trek terjemahan internal (embedded) maupun berkas eksternal (`.srt`, `.ass`, `.vtt`).
* Kustomisasi visual: Ukuran font, warna teks, ketebalan outline/stroke, dan bayangan teks.
* Rendering penuh script ASS jika menggunakan [[MPV Engine]].

## 🔗 Keterhubungan Komponen
* Diakses dari: [[Player Screen]]
* Didukung oleh: [[ExoPlayer Engine]] & [[MPV Engine]]
* Pengaturan disimpan di: [[DataStore Preferences]]
