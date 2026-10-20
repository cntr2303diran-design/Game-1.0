# DECISIONS — Tafsiran Aturan & Keputusan Teknis

Dokumen ini mencatat setiap tafsiran yang tidak tertulis eksplisit di desain asli
(prompt-lisbon-1944, BAGIAN 7) beserta alasannya. Bila memungkinkan, keputusan dijadikan
nilai konfigurasi.

## Tahap 1 — Model data & konten

| # | Topik | Keputusan | Alasan |
|---|-------|-----------|--------|
| D1 | Arah "kiri" / "kanan" | **Kiri = kursi berikutnya** (indeks +1, searah jarum jam); **kanan = kursi sebelumnya**. Enum `Direction` dan `TargetScope`. | Dugaan berjalan "ke kiri" dan juga "searah jarum jam" (6.4) — keduanya hanya konsisten bila kiri = pemain berikutnya. |
| D2 | Gaji saat kartu "maju ke …" tanpa menyebut gaji (Sandi #9 Panggilan Interogasi, Sandi #10 Tiket ke Estoril) | Tetap menerima gaji bila melewati START (`collectSalary: true`). Hanya kartu mundur (Sandi #5) dan masuk Penjara (Sandi #2) yang tidak. | Konsisten dengan kartu "maju" lainnya; dapat diubah per kartu di `cipher.json`. |
| D3 | Biaya Tuduhan April 1945 | Efek Front `accuseFromAnywhere` tidak membawa angka; biayanya dari `balance.aprilAccuseCost` (300). | Semua angka keseimbangan di `balance.json` (prinsip 7). |
| D4 | Stok bangunan | `safeHouseStock` (32) dan `hqStock` (12) ditambahkan ke `balance.json`. | BAGIAN 5.1 menyebut batas stok; menjadikannya data memungkinkan uji keseimbangan. |
| D5 | Fase Gerak & Penyelidikan sebagai keputusan | Ditambah `PendingDecision.AwaitRoll` dan `PendingDecision.SuggestionOffer` (+ aksi `SkipSuggestion`). | BAGIAN 9 tidak punya keputusan untuk "lempar dadu" dan "Dugaan opsional", padahal prinsip 6 mewajibkan engine selalu tahu siapa yang memutuskan. |
| D6 | Status RNG dalam save | `GameState` menyimpan `seed` dan `rngState`. App/sim membuat `SplitMix64Rng.fromState(state.rngState)` sebelum `reduce`, lalu engine menulis status terbaru kembali. | Save/load di tengah game harus melanjutkan urutan acak yang sama (prinsip 2 & 3). |
| D7 | Algoritma RNG | SplitMix64 (status = satu `Long`). | Deterministik lintas JVM/Android, status mudah diserialisasi, cukup berkualitas untuk dadu & pengocokan. |
| D8 | Format efek kartu | Efek Front, Sandi, dan Telegram adalah sealed class dengan diskriminator `"type"` (sama dengan format polimorfik kotlinx.serialization). | Prinsip 8: konten di JSON, tetapi tetap terstruktur dan tervalidasi. |
| D9 | Pembacaan konten | Loader membaca pohon `JsonElement` secara eksplisit (bukan `decodeFromString`). | Pesan galat menunjuk file + jalur field (mis. `board.json: $.tiles[1].price`), dan validator mengumpulkan **semua** pelanggaran sekaligus. |
| D10 | Lokasi file konten | `engine/src/main/resources/content/*.json`, dibaca sebagai resource Java. | Satu sumber kebenaran untuk engine, bot, sim, dan APK (resource JAR ikut dikemas Android). |
| D11 | `keep` pada kartu | `keep = true` wajib bila dan hanya bila kartu punya efek `jailPass` (dicek validator). | Mencegah kartu Surat Bebas terbuang atau kartu biasa tersimpan karena salah ketik. |
| D12 | Game tanpa manusia | `GameConfig.validate()` mengizinkan semua pemain bot (untuk `:sim`). Layar Setup di `:app` yang mewajibkan ≥ 1 manusia. | Simulasi ribuan game membutuhkan meja penuh bot. |
| D13 | `PlayerView` | Didefinisikan di Tahap 1: lawan hanya terlihat sebagai `OpponentView` (jumlah kartu, bukan isinya; tangan publik hanya bila bangkrut); `myDecision` hanya untuk decider; amplop hanya terlihat setelah game selesai atau viewer Terbakar. | Antarmuka `Bot` (`:bot`) bergantung pada tipe ini; kontrak kerahasiaan ditetapkan sejak awal. |
| D14 | Versi toolchain | Kotlin 2.1.0, AGP 8.7.3, Gradle 8.14.3, compile/targetSdk 35, JVM target 17. | Kombinasi stabil yang saling kompatibel; dapat dinaikkan di `gradle/libs.versions.toml`. |
