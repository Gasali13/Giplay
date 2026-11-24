---
tags:
  - "#arsitektur"
---

# 🏗️ Arsitektur Sistem Giplay

Arsitektur aplikasi **Giplay** mengadopsi pola **MVVM (Model - View - ViewModel)** yang dipadukan dengan prinsip *Clean Architecture* dan *Unidirectional Data Flow* menggunakan Kotlin Coroutines & StateFlow.

---

## 🏛️ Struktur Lapisan (Layers)

1. **Presentation Layer (UI & ViewModels)**
   * Berisi `Activity`, `Custom Views`, dan `ViewModels`.
   * Layar utama: [[Library Screen]], [[Episode List Screen]], dan [[Player Screen]].
   * Komponen UI hanya bertugas merender state dan meneruskan aksi pengguna ke `ViewModel`.

2. **Domain & Player Engine Layer**
   * Abstraksi engine pemutar video melalui [[Dual Engine Concept]].
   * Implementasi pemutar konkret: [[ExoPlayer Engine]] & [[MPV Engine]].
   * Manajemen interaksi fisik: [[Gesture & Controls]] dan sistem audio [[Audio Focus]].

3. **Data Layer (Repository & Data Sources)**
   * Menjadi satu-satunya sumber data (*Single Source of Truth*) melalui [[Media Repository]].
   * Penyimpanan lokal terstruktur menggunakan [[Room Database]].
   * Pengaturan preferensi persisten menggunakan [[DataStore Preferences]].
   * Pengurutan berkas cerdas menggunakan [[Natural Order Sorting]].

---

## 🔄 Aliran Data (Data Flow)

```text
[Storage / MediaStore]
         ↓
 [Media Repository] ←───→ [Room Database (Progress)]
         ↓
 [Library / Player ViewModel]
         ↓ StateFlow
 [Activity / UI Views]
```

Kembali ke [[Giplay Dashboard]] | Lanjut ke [[Tech Stack & Dependencies]]
