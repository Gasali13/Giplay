---
tags:
  - "#engine"
---

# 🎮 MPV Engine (libmpv)

**MPV Engine** dibangun di atas port C/C++ `mpv-android-lib` (versi 0.1.12), dirancang khusus untuk memutar format video anime berkode kompleks dengan subtitle beranimasi (SSA/ASS).

---

## 🔑 Fitur Utama
* **Penuh Dukungan Format SSA/ASS Subtitle**: Menggunakan mesin font bawaan untuk mempertahankan efek tipografi, warna, gradien, dan posisi subtitle anime aslinya.
* **Property Observer Reaktif**: Mengamati perubahan waktu (`time-pos`), durasi (`duration`), status pause, dan format warna langsung dari core MPV C-API.
* **Custom Shader & Scaler**: Kemampuan pemrosesan video tingkat lanjut dengan konfigurasi fleksibel.

## 🔗 Keterhubungan Komponen
* Dikendalikan dari: [[Player Screen]]
* Pengaturan trek subtitle: [[Track Selection & Subtitles]]
* Bagian dari arsitektur: [[Dual Engine Concept]]
* Berdampingan dengan: [[ExoPlayer Engine]]
