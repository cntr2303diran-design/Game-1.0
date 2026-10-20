# LISBON 1944: Kota Para Mata-Mata

Game Android (Kotlin + Jetpack Compose) — adaptasi digital board game 3–6 pemain yang
menggabungkan ekonomi properti, misteri deduksi, dan Jam Perang 12 langkah.
Desain asli: **@DIRAN**.

> Musim panas 1944. Map Merah berisi rencana operasi Sekutu dicuri. Temukan pencurinya,
> lokasinya, dan barang buktinya — sebelum perang berakhir.

## Status pengerjaan

| Tahap | Isi | Status |
|-------|-----|--------|
| 1 | Proyek 4 modul, model data, konten JSON, loader + validator | ✅ selesai |
| 2 | Mesin aturan (`reduce`, `legalActions`, `viewFor`, skor) | ⏳ berikutnya |
| 3 | Bot Easy / Normal / Hard + DeductionTracker | — |
| 4 | Simulasi keseimbangan (`:sim`) | — |
| 5 | UI inti (papan, HUD, sewa, lelang, privasi, save) | — |
| 6 | UI lengkap, audio, aksesibilitas, tutorial | — |
| 7 | Poles & rilis | — |

## Struktur modul

```
:engine  mesin aturan — Kotlin murni, tanpa dependensi Android
:bot     AI pemain komputer — hanya melihat PlayerView
:sim     CLI JVM untuk validasi konten & simulasi ribuan game
:app     aplikasi Android (Compose)
```

```
engine/src/main/kotlin/id/diran/lisbon1944/engine/
├── model/      Ids, Enums, Tiles, Cards (efek Front/Sandi/Telegram), Balance,
│               GameConfig, GameState, Actions, PendingDecision, PlayerView
├── content/    GameContent, ContentLoader, ContentValidator, JsonReader
└── rng/        GameRng + SplitMix64Rng (deterministik, status bisa disimpan)
engine/src/main/resources/content/
├── board.json  factions.json  evidence.json  front.json
└── cipher.json telegram.json  balance.json
```

## Cara build

Butuh JDK 17+ dan Android SDK (untuk `:app`).

```bash
./gradlew :engine:test :bot:test :sim:test   # semua test JVM
./gradlew :sim:run --args="--validate"       # validasi konten & ringkasan
./gradlew :app:assembleDebug                 # APK debug
```

Setiap push ke `main` menjalankan GitHub Actions (`.github/workflows/ci.yml`): test, validasi
konten, dan build APK debug. APK bisa diunduh dari tab **Actions → artifact
`lisbon1944-debug-apk`**.

## Mengubah keseimbangan & kartu

- Semua angka ada di `engine/src/main/resources/content/balance.json`.
- Papan, faksi, kartu Bukti, Front, Sandi, dan Telegram ada di file JSON sejenis.
- Validator gagal keras bila struktur salah (papan bukan 40 petak, distrik bukan 3 properti,
  Bukti bukan 6/6/6, Front bukan 12, Sandi/Telegram bukan 16, dll.) dan menyebut file + field.
- Uji balance alternatif tanpa mengubah bawaan:
  `./gradlew :sim:run --args="--validate --balance /path/balance.json"`

## Keputusan aturan

Lihat [DECISIONS.md](DECISIONS.md).

Semua tokoh, faksi, dan lencana dalam game ini fiktif/orisinal.
