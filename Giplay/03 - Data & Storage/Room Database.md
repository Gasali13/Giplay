---
tags:
  - "#data"
---

# 🗄️ Room Database (Histori & Progres)

Aplikasi Giplay menggunakan basis data SQLite lokal yang dibungkus oleh pustaka Google **AndroidX Room** (`AnimeDatabase.kt`).

---

## 📋 Entitas: `PlaybackProgressEntity`
Menyimpan posisi tontonan terakhir setiap berkas video:
* `videoUri` / `filePath`: Identitas unik berkas video.
* `lastPositionMs`: Titik waktu terakhir video ditonton (milidetik).
* `durationMs`: Total durasi video.
* `lastWatchedTimestamp`: Waktu terakhir ditonton untuk pengurutan riwayat terbaru.
* `isCompleted`: Menandai apakah episode sudah selesai ditonton (>90% durasi).

## ⚡ Data Access Object (`PlaybackProgressDao`)
* Mendukung operasi reaktif `Flow<List<PlaybackProgressEntity>>` sehingga status checklist atau progress bar di antarmuka langsung terbarui secara otomatis saat video dijeda.

## 🔗 Keterhubungan Komponen
* Dikonsumsi oleh: [[Media Repository]]
* Ditampilkan pada: [[Episode List Screen]] dan [[Library Screen]]
* Disimpan secara periodik oleh: [[Player Screen]]
