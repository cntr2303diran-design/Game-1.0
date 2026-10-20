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
| 2 | Mesin aturan (`reduce`, `legalActions`, `viewFor`, skor) | ✅ selesai |
| 3 | Bot Easy / Normal / Hard + DeductionTracker | ✅ selesai |
| 4 | Simulasi keseimbangan (`:sim`) | ⏳ berikutnya |
| 5 | UI inti (papan, HUD, sewa, lelang, privasi, save) | — |
| 6 | UI lengkap, audio, aksesibilitas, tutorial | — |
| 7 | Poles & rilis | — |

## Aturan tambahan (versi 4 jam)

Selain aturan dasar (ekonomi properti, misteri amplop, Jam Perang 12 Front), versi ini menambahkan:

- **Intel** — token untuk mengajukan Dugaan (1), Bungkam (1), dan Tuduhan Akhir (2). +1 tiap melewati START.
- **Bungkam** — bayar 1 Intel agar tidak perlu menunjukkan kartu saat Dugaan.
- **Kecurigaan** — Pasar Gelap dan menang Kasino menaikkan meteran; penuh (6) berarti ditangkap.
- **Misi Rahasia** — 14 tujuan pribadi bernilai 30–55 poin di Akhir Perang.
- **Map Merah berpindah** — pada 4 Front, satu kategori kartu Bukti dibagi ulang (jawaban bisa berubah).
- **Tuduhan Akhir** baru dibuka sejak langkah Jam Perang 10; Jam maju tiap ±5 lintasan START.
- **8 faksi** (baru: Penyelundup Spanyol, Pembelot Abwehr) dan **48 kartu Sandi/Telegram**.

Detail dan alasan tiap keputusan: [DECISIONS.md](DECISIONS.md) (D38–D50).

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
├── rng/        GameRng + SplitMix64Rng (deterministik, status bisa disimpan)
├── internal/   Setup, Flow (giliran/gerak/Front), Cards, Mystery, Market,
│               Economy (sewa/bangun/gadai), Handlers, Legality, Views, Scoring
├── GameEngine.kt   API publik: newGame, reduce, legalActions, viewFor, computeRent, computeScores
├── Invariants.kt   pemeriksaan invariant BAGIAN 9
└── GameEvents.kt   kunci event stabil untuk UI
engine/src/main/resources/content/
├── board.json  factions.json  evidence.json  front.json
└── cipher.json telegram.json  balance.json  missions.json
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

## Memakai mesin aturan

```kotlin
val engine = GameEngine()                       // memuat konten bawaan
var state = engine.newGame(config, seed = 42L)
val me = PlayerId(0)
val view = engine.viewFor(state, me)            // satu-satunya yang boleh dibaca UI/bot
val legal = engine.legalActions(state, me)
when (val r = engine.reduce(state, legal.first())) {
    is ReduceResult.Ok -> state = r.state       // r.events untuk animasi
    is ReduceResult.Rejected -> println(r.reason)
}
val save = GameJson.encode(state)               // save/replay deterministik
```

## Bot

```kotlin
val bots = Bots(engine.content)                 // parameter dari bot/src/main/resources/bot-config.json
val bot = bots.create(BotLevel.HARD)
val action = bot.decide(engine.viewFor(state, me), engine.legalActions(state, me), rng)
val result = BotMatch(engine).play(config, seed) // satu permainan penuh antar-bot
```

Bot hanya menerima `PlayerView`. `DeductionTracker` menghitung peluang tiap kombinasi amplop dari
pengetahuan sendiri dan log publik.

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
