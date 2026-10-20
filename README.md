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
| 4 | Simulasi keseimbangan (`:sim`) | ✅ selesai |
| 5 | UI inti (papan, HUD, sewa, lelang, privasi, save) | ✅ selesai |
| 6 | UI lengkap, audio, aksesibilitas, tutorial | ⏳ berikutnya |
| 7 | Poles & rilis | — |

## Aturan tambahan (versi 4 jam)

Selain aturan dasar (ekonomi properti, misteri amplop, Jam Perang 12 Front), versi ini menambahkan:

- **Intel** — token untuk mengajukan Dugaan (1), Bungkam (1), dan Tuduhan Akhir (2). +1 tiap melewati START.
- **Bungkam** — bayar 1 Intel agar tidak perlu menunjukkan kartu saat Dugaan.
- **Kecurigaan** — Pasar Gelap dan menang Kasino menaikkan meteran; penuh (6) berarti ditangkap.
- **Misi Rahasia** — 14 tujuan pribadi bernilai 30–55 poin di Akhir Perang.
- **Map Merah berpindah** — pada 4 Front, satu kategori kartu Bukti dibagi ulang (jawaban bisa berubah).
- **Tuduhan Akhir** baru dibuka sejak langkah Jam Perang 10; Jam maju tiap ±5 lintasan START.
- **Dominasi Ekonomi** (5–6 pemain) — kekayaan ≥ 65% meja saat Front dibalik = menang Ekonomi.
- **8 faksi** (baru: Penyelundup Spanyol, Pembelot Abwehr) dan **48 kartu Sandi/Telegram**.
- **Kartu Taktik** (24) — Penyamaran, Pemerasan, Sabotase, Mogok Buruh, Pengintaian, dan lainnya.
- **Operasi Kota** (18) — perlombaan tujuan publik berhadiah uang, Intel, dan **Prestise**.
- **Patroli PIDE & Kontak Netral** — dua pion yang berkeliling papan tiap putaran.
- **24 Front** — tiap bulan varian A/B acak; **Bantuan Sekutu** untuk pemain tertinggal; **24 Misi Rahasia**.

Buku aturan lengkap untuk pemain: [docs/ATURAN.md](docs/ATURAN.md).

Detail dan alasan tiap keputusan: [DECISIONS.md](DECISIONS.md) (D38–D74).

## Memainkan di Android

Menu → **Permainan Baru**: pilih 3–6 pemain, tiap kursi Manusia atau Bot (Mudah/Normal/Sulit), faksi, mode, Agen Ganda (5–6 pemain), dan seed. Beberapa manusia bergantian di satu perangkat; layar privasi muncul tiap kali perangkat harus berpindah tangan. Permainan tersimpan otomatis di 3 slot dan bisa dilanjutkan dari menu.

Layar permainan: HUD (giliran, uang, Intel, Kecurigaan, Jam Perang), papan (cubit untuk zoom, ketuk petak untuk detail), dan tab **Aksi · Agen · Catatan · Kota · Log**. Catatan detektif terisi otomatis dari semua kartu yang pernah kamu lihat.

## Struktur modul

```
:engine  mesin aturan — Kotlin murni, tanpa dependensi Android
:bot     AI pemain komputer — hanya melihat PlayerView
:sim     CLI JVM untuk validasi konten & simulasi ribuan game
:session sesi permainan (pass-and-play, bot, simpan/muat, teks log id/en) — Kotlin murni
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
└── cipher.json telegram.json  balance.json  missions.json  tactics.json  operations.json
```

## Cara build

Butuh JDK 17+ dan Android SDK (untuk `:app`).

```bash
./gradlew :engine:test :bot:test :sim:test :session:test   # semua test JVM
./gradlew :sim:run --args="--validate"       # validasi konten & ringkasan
./gradlew :sim:run --args="--games 1000 --players 4 --bots hard,hard,normal,normal --seed 42 --out report.csv"
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

## Simulasi keseimbangan

```bash
./gradlew :sim:run --args="--games 1000 --players 4 --bots hard,hard,normal,normal \
    --seed 42 --balance path/balance.json --mode standard --out report.csv"
```

Laporan konsol + CSV: durasi (45 detik/giliran), cara menang, win rate per faksi & kursi, bayar-informasi vs
bayar-uang dan korelasinya dengan menang, tuduhan salah, langkah Jam saat misteri terpecahkan, A/B otomatis
`infoPaymentQuota = 1`, anomali (> 4.000 aksi) beserta seed, dan cek target keseimbangan. Hasil terbaru ada di
[`docs/sim/`](docs/sim) — ringkasannya di DECISIONS.md (D51–D56).

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
