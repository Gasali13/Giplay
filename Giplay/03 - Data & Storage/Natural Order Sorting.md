---
tags:
  - "#data"
---

# 🔢 Natural Order Sorting

Dalam pengurutan alfabet biasa (ASCII sorting), berkas sering kali terurut secara keliru:
* *Alphabetical:* `Episode 1`, `Episode 10`, `Episode 11`, `Episode 2` (Episode 2 terlempar ke bawah!).
* *Natural Order:* `Episode 1`, `Episode 2`, `Episode 10`, `Episode 11` (Sesuai pemikiran manusia).

---

## 💡 Implementasi Giplay
Giplay mengimplementasikan pembanding `NaturalOrderComparator.kt` yang mengenali urutan angka desimal di dalam nama berkas secara cerdas.

Hal ini krusial untuk pemutar video anime agar pengguna tidak menonton episode yang tertukar urutannya!

## 🔗 Keterhubungan Komponen
* Digunakan oleh: [[Media Repository]]
* Memastikan kerapian di: [[Episode List Screen]]
