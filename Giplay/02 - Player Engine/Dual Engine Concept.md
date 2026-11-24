---
tags:
  - "#engine"
---

# ⚡ Dual Engine Concept

Salah satu fitur paling unggul dari Giplay adalah pendekatan **Dual Engine** (Dua Mesin Pemutar). Pengguna dapat memilih atau mengganti engine pemutaran secara instan di dalam [[Player Screen]].

---

## 🎯 Mengapa Harus Dual Engine?

Tidak ada satu pemutar video di Android yang sempurna untuk semua jenis video:

1. **[[ExoPlayer Engine]] (Google Media3)**
   * **Kelebihan:** Sangat hemat daya, integrasi perangkat keras (hardware decoding) kelas satu, performa mulus untuk format umum (MP4, WebM, MKV standar).
   * **Kelemahan:** Terbatas dalam merender efek teks subtitle anime tingkat lanjut (gaya font beranimasi, efek karaoke, rotasi teks format `.ass`).

2. **[[MPV Engine]] (libmpv)**
   * **Kelebihan:** Standar emas untuk pemutaran video anime. Mampu mengeksekusi script SSA/ASS secara presisi persis seperti tampilan di komputer/desktop.
   * **Kelemahan:** Menggunakan lebih banyak daya pemrosesan CPU/GPU pada video tertentu.

---

## ⚙️ Integrasi & Pengalihan

* Pilihan default disimpan dalam [[DataStore Preferences]].
* Kompatibel dengan sistem gestur [[Gesture & Controls]] dan sistem suara [[Audio Focus]].
* Navigasi trek tetap seragam melalui modal [[Track Selection & Subtitles]].

Tautan Utama: [[Giplay Dashboard]] | [[ExoPlayer Engine]] | [[MPV Engine]]
