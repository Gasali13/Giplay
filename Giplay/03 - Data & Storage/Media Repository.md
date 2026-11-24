---
tags:
  - "#data"
---

# 📁 Media Repository & Storage Scanner

`MediaRepository.kt` adalah komponen inti dari lapisan data (*Data Layer*) yang bertanggung jawab memindai berkas video pada penyimpanan perangkat Android.

---

## 🔍 Cara Kerja Pemindaian
1. **Scoped Storage & MediaStore**:
   * Melakukan query ke API `MediaStore.Video.Media` Android untuk mendapatkan metadata berkas (nama, path, ukuran, durasi, tanggal modifikasi).
2. **Pengelompokan Anime Otomatis**:
   * Mengurai folder direktori dan nama file untuk mengelompokkan video ke dalam satu seri anime yang utuh (*Series Grouping*).
3. **Penyelarasan dengan Database**:
   * Menggabungkan informasi berkas video dengan data progres dari [[Room Database]].
4. **Pengurutan Episode**:
   * Memanggil utilitas [[Natural Order Sorting]] agar urutan episode tersusun rapi.

## 🔗 Keterhubungan Komponen
* Menyediakan data untuk: [[Library Screen]] dan [[Episode List Screen]]
* Mengakses data dari: [[Room Database]]
* Merujuk pada arsitektur: [[Arsitektur Sistem]]
