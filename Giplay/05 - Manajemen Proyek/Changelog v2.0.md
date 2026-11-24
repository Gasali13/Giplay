---
tags:
  - "#project"
---

# 📦 Changelog Rilis v2.0

Pembaruan besar arsitektur antarmuka ke **Jetpack Compose + Liquid Glass**, penyempurnaan *dual engine*, perbaikan bug, dan optimasi performa (`versionCode = 3`, `versionName = "2.0"`).

---

## 🚀 Ringkasan Pembaruan
* **UI Jetpack Compose & Liquid Glass Shader (AGSL)**:
  * Migrasi menyeluruh layar Library, Episode List, dan Player ke Jetpack Compose dengan panel adaptif (Bottom Sheet di portrait, Side Sheet di landscape) serta *real-time video backdrop blur* (`PixelCopy`).
  * Animasi dan transisi halus bergaya iOS (*critically damped springs* & *ease-out*).
* **Peningkatan Player & Mode Picture-in-Picture**:
  * Tombol kontrol interaktif `RemoteAction` (Mundur 10d, Putar/Jeda, Maju 10d, Episode Berikutnya) dan rasio aspek otomatis mengikuti ukuran video.
  * Isolasi state timeline untuk mencegah *full-screen recomposition* selama pemutaran.
* **Perbaikan Bug & Keandalan Media**:
  * Dekode nama file *URL/percent-encoded* UTF-8 dan pembacaan `OpenableColumns.DISPLAY_NAME` dari intent `ACTION_VIEW`.
  * Penutupan siklus hidup `ParcelFileDescriptor` (`fd://`) secara aman pada MPV agar tidak terjadi kebocoran deskriptor.
  * Dukungan penuh izin akses video terbatas Android 14+ (*Visual User Selected*).
* **Optimasi Performa & Ukuran Proyek**:
  * *Disk cache* thumbnail & durasi persisten dengan batas LRU `45 MB`, filter pencarian di *background thread*, dan pembersihan dependensi/resource View lawas.

---

Tautan: [[Giplay Dashboard]] | [[Roadmap & Rencana Fitur]] | [[Changelog v1.0.1]]
