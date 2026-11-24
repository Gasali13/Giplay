---
tags:
  - "#engine"
---

# 🖐️ Gesture & Controls System

Sistem gestur pada Giplay (`GestureManager.kt`) mengadopsi standar kontrol kenyamanan yang dipopulerkan oleh pemutar video legendaris seperti **MX Player**.

---

## 🕹️ Gestur yang Didukung

1. **Vertikal Kiri**: Mengatur tingkat kecerahan layar (*Brightness Control*) dengan overlay persentase.
2. **Vertikal Kanan**: Mengatur volume perangkat (*Volume Control*) tanpa harus menekan tombol fisik.
3. **Horizontal (Scrubbing)**: Menggeser posisi video (*Seek Time*) secara presisi dengan tampilan target waktu.
4. **Double Tap**:
   * Ketuk ganda di sisi kiri: Mundur 10 detik.
   * Ketuk ganda di sisi kanan: Maju 10 detik.
5. **Press & Hold (Tahan Sentuhan)**: Mempercepat pemutaran video secara instan (2.0x playback speed) untuk melewati intro/rekap cepat.
6. **Lock Mode**: Mengunci layar agar sentuhan tidak sengaja tidak mengganggu pemutaran video.

## 🔗 Keterhubungan Komponen
* Diaplikasikan pada: [[Player Screen]]
* Berpasangan dengan: [[Glowing Seekbar]]
* Kompatibel dengan: [[Dual Engine Concept]]
