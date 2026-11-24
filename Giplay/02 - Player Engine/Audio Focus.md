---
tags:
  - "#engine"
---

# 🔊 Audio Focus & Headset Helper

Komponen `AudioFocusHelper.kt` bertugas memastikan aplikasi berinteraksi secara sopan dengan sistem audio sistem operasi Android.

---

## 🛡️ Skenario Penanganan

1. **AudioFocus Gain / Loss**:
   * Menjeda pemutaran video otomatis saat ada panggilan telepon masuk atau alarm berbunyi.
   * Melanjutkan pemutaran otomatis (*resume*) jika interupsi selesai dan fokus kembali.
2. **Becoming Noisy (Pencabutan Headset/Earphone)**:
   * Secara otomatis menghentikan (*pause*) video jika kabel earphone tercabut atau sambungan Bluetooth terputus, guna mencegah suara tiba-tiba keluar dari speaker ponsel di tempat umum.

## 🔗 Keterhubungan Komponen
* Diintegrasikan ke dalam: [[ExoPlayer Engine]] & [[MPV Engine]]
* Berada dalam alur: [[Player Screen]]
