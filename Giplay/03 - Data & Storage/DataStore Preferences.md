---
tags:
  - "#data"
---

# ⚙️ DataStore Preferences

Sebagai pengganti SharedPreferences klasik yang lambat dan rentan *blocking*, Giplay mengimplementasikan **AndroidX DataStore Preferences** (`AppPreferences.kt`) untuk menyimpan seluruh konfigurasi pengguna secara asinkron dan thread-safe.

---

## 📌 Pengaturan yang Disimpan
1. **Engine Pilihan Pengguna**: Menentukan apakah [[ExoPlayer Engine]] atau [[MPV Engine]] yang menjadi pemutar default saat memutar video baru.
2. **Auto-Play Next Episode**: Apakah aplikasi harus memutar episode berikutnya secara otomatis ketika video sekarang selesai.
3. **Sort Mode**: Pilihan pengurutan daftar anime (A-Z, Tanggal Baru, Tanggal Lama).
4. **Preferensi Subtitle**: Ukuran teks, warna font, dan warna latar belakang subtitle kustom.

## 🔗 Keterhubungan Komponen
* Mempengaruhi perilaku: [[Dual Engine Concept]]
* Mengatur filter pada: [[Library Screen]]
* Mengatur tampilan pada: [[Track Selection & Subtitles]]
