---
tags:
  - "#ui"
---

# 📺 Episode List Screen

Ketika sebuah seri anime dipilih di [[Library Screen]], pengguna akan diarahkan ke `EpisodeListActivity.kt`.

---

## 🎨 Fitur Antarmuka
1. **Daftar Episode Terurut**: Episode ditampilkan secara teratur berkat algoritma [[Natural Order Sorting]].
2. **Indikator Progres Tontonan**: Progress bar mini di bawah setiap episode yang menunjukkan posisi tontonan terakhir yang disimpan oleh [[Room Database]].
3. **Badge "Tonton Selesai"**: Menandai episode yang sudah tamat ditonton.
4. **Tombol "Lanjut Tonton" (*Resume*)**: Melanjutkan episode yang belum selesai hanya dengan satu kali klik.

## 🔗 Keterhubungan Komponen
* Dibuka dari: [[Library Screen]]
* Meluncurkan: [[Player Screen]]
* Mengambil data progres dari: [[Room Database]]
