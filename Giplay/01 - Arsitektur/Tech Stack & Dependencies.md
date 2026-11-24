---
tags:
  - "#arsitektur"
---

# 🛠️ Tech Stack & Dependencies

Daftar spesifikasi teknologi dan dependensi yang digunakan pada Giplay (diatur secara terpusat pada `gradle/libs.versions.toml`).

---

## 📌 Fondasi Inti
* **Bahasa:** Kotlin `2.0.21`
* **Android Gradle Plugin (AGP):** `8.7.3`
* **Target SDK:** Android 15 (API 35)
* **Minimum SDK:** Android 7.0 (API 24)
* **KSP (Kotlin Symbol Processing):** `2.0.21-1.0.28` (untuk compiler Room)

---

## 📦 Pustaka Kunci & Modul

| Kategori | Pustaka | Versi | Peran Utama |
|---|---|---|---|
| **Player Engine** | `androidx.media3:media3-exoplayer` | `1.5.1` | Dipakai di [[ExoPlayer Engine]] untuk efisiensi decoding hardware. |
| **Player Engine** | `io.github.abdallahmehiz:mpv-android-lib` | `0.1.12` | Dipakai di [[MPV Engine]] untuk rendering subtitle ASS/SSA kompleks. |
| **Penyimpanan DB** | `androidx.room:room-runtime` | `2.6.1` | Basis data SQLite lokal pada [[Room Database]]. |
| **Preferensi** | `androidx.datastore:datastore-preferences` | `1.1.1` | Manajemen setting pada [[DataStore Preferences]]. |
| **Gambar & Poster** | `io.coil-kt:coil` | `2.7.0` | Pemuatan thumbnail asinkron pada [[Library Screen]]. |
| **Asinkron & Alur** | `kotlinx-coroutines-android` | `1.9.0` | Pemrosesan background I/O dan reaktivitas data. |

Terkait: [[Arsitektur Sistem]] | [[Dual Engine Concept]] | [[Giplay Dashboard]]
