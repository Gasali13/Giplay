---
tags:
  - "#ui"
---

# 🎬 Player Screen (Layar Pemutar)

`PlayerActivity.kt` adalah inti pengalaman menonton video pada aplikasi Giplay.

---

## 🎨 Komponen Antarmuka
1. **Dynamic Video Surface**: Mampu beralih antara Surface ExoPlayer dan MPV Surface secara mulus melalui [[Dual Engine Concept]].
2. **Gesture Controller**: Mengintegrasikan sistem gesture [[Gesture & Controls]] (swipe volume, brightness, scrubbing, double-tap seek).
3. **Glowing Seekbar**: Indikator garis waktu modern melalui [[Glowing Seekbar]].
4. **HDR Badge**: Menampilkan lencana otomatis saat video terdeteksi berformat HDR (HDR10, HLG).
5. **Aspect Ratio Modal**: Pilihan rasio aspek (Fit Screen, Crop/Zoom, Stretch, 16:9, 4:3).
6. **Inactivity Prompt & Lock**: Tombol kunci layar dan prompt sleep timer saat pengguna tidak aktif.

## 🔗 Keterhubungan Komponen
* Dikendalikan oleh: [[Dual Engine Concept]]
* Menggunakan mesin: [[ExoPlayer Engine]] & [[MPV Engine]]
* Mengatur audio: [[Audio Focus]]
* Mengatur subtitle & audio track: [[Track Selection & Subtitles]]
* Menyimpan histori ke: [[Room Database]]
