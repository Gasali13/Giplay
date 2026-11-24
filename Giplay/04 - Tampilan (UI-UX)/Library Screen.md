---
tags:
  - "#ui"
---

# 📚 Library Screen (Koleksi Anime)

`LibraryActivity.kt` adalah gerbang utama aplikasi Giplay saat pengguna pertama kali membuka aplikasi.

---

## 🎨 Fitur Antarmuka
1. **Series Grid & List Layout**: Menampilkan poster/thumbnail video dengan judul seri dan jumlah episode.
2. **Alphabet Index Sidebar (`AlphabetIndexView`)**: Bilah alfabet di tepi kanan untuk melompat cepat dari A ke Z pada koleksi anime yang sangat banyak.
3. **Pencarian Cepat (*Instant Search*)**: Memfilter judul anime secara instan saat pengguna mengetik.
4. **Dialog Pengurutan (*Sort Options*)**: Mengubah urutan berdasarkan nama, tanggal ditambahkan, atau tanggal terakhir ditonton.

## 🔗 Keterhubungan Komponen
* Mengambil data dari: [[Media Repository]]
* Membuka layar: [[Episode List Screen]]
* Mengikuti aturan preferensi: [[DataStore Preferences]]
* Pusat aplikasi: [[Giplay Dashboard]]
