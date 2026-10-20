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

## Tahap 2 — Mesin aturan

| # | Topik | Keputusan | Alasan |
|---|-------|-----------|--------|
| D15 | Efek bersarang | Engine memakai **tumpukan tugas** (`GameState.tasks`, sealed `Task`) yang ikut tersimpan. `reduce` menerapkan aksi lalu menjalankan tugas sampai ada `PendingDecision` baru atau permainan selesai. | Efek berantai (kartu → gerak → melintas Kedutaan → sewa → utang → lelang kebangkrutan) bisa berhenti di keputusan mana pun dan tetap bisa disimpan/dilanjutkan. |
| D16 | Gerak per petak | Gerak dijalankan satu petak per langkah tugas. | START dan Kedutaan yang **dilewati** (bukan hanya tempat berhenti) bisa diproses; tawaran Tuduhan 100 muncul tepat saat melintas, lalu gerak berlanjut. |
| D17 | Urutan START | Saat melewati START: **Jam Perang maju lebih dulu, lalu gaji**. Gaji ditolak bila Front aktif (yang baru dibalik) melarangnya atau perang sudah usai. | Front baru "langsung berlaku" (6.6). Konsekuensi: Razia Okt 1944 menilai uang sebelum gaji pemain yang menggerakkan Jam. |
| D18 | Perang usai | Begitu Front #12 dibalik, sisa gerak/efek dibatalkan; kartu yang sedang dijalankan kembali ke tumpukan; tiap pemain aktif yang belum Terbakar memasukkan Tebakan Akhir (mulai pemain aktif, searah jarum jam), lalu skor. | "Permainan langsung berakhir" (Front #12). |
| D19 | Pemenang skor seri | Seri skor → petunjuk benar terbanyak → uang tunai terbanyak → sama-sama menang. | 6.9. |
| D20 | Agen Ganda | Saat `RevealForSuggestion`, Agen Ganda boleh sekali menunjukkan kartu tangan **mana pun** (bohong); tercatat privat dan dibuka di akhir. Kartu "diarahkan" = kartu yang ia sebut dalam Dugaannya sendiri yang tidak ia pegang. Ia menang bila ada pemain lain Terbakar dengan tuduhan memuat kartu yang diarahkan, atau bila perang usai tanpa ia ketahuan. | BAGIAN 7 Agen Ganda; rumusan "diarahkan" dibuat terukur. |
| D21 | ProposeTrade | Tidak dienumerasi di `legalActions` (parametrik tak hingga); legalitasnya lewat `checkAction`. Perdagangan hanya di fase Bisnis pemain aktif, mitra menjawab langsung. | Daftar aksi tetap terbatas; UI membangun tawaran lalu memvalidasinya. |
| D22 | Dekode | Uang lemparan tambahan ditahan; gagal → ke operator, berhasil → ke bank. Operator bertangan kosong → +50 dari bank. Operator = pemain aktif di kanan. | 6.4. |
| D23 | Kartu dilihat acak | Mata Rahasia MI6: target tidak tahu kartu mana. Informan/Penyadapan/Dekode: pemilik kartu tahu kartu mana yang terlihat (secara fisik kartu diambil). Penyadapan seri terkaya → penarik memilih. | Konsisten dengan teks kemampuan MI6 ("pemain lain hanya tahu…"). |
| D24 | Rekrut Informan | Hak 1 Dugaan Lokasi bebas berlaku pada giliran ini dan 3 giliran berikutnya; hangus setelah dipakai. | "dalam 3 giliranmu berikutnya". |
| D25 | Terbakar | Pemain Terbakar tetap bermain ekonomi, wajib menjawab Dugaan, boleh mengajukan Dugaan, tidak bisa menuduh lagi, tidak memberi Tebakan Akhir; skornya ×0,5. | 6.7–6.9. |
| D26 | Kebangkrutan | Bangunan dijual ke bank setengah harga dasar; uang & properti (status gadai tetap) & Surat Bebas pindah ke kreditur pemain; kreditur bank/Brankas → properti dilelang (Surat Bebas kembali ke tumpukan). Kartu Bukti terbuka untuk semua. Tersisa 1 pemain aktif → kemenangan ekonomi. | 6.8. |
| D27 | Kasino & tuduhan keluarga | Kasino/Pasar Gelap tidak muncul di Mode Keluarga; tuduhan salah di Mode Keluarga: denda min(uang, 200) ke Brankas tanpa Terbakar. | BAGIAN 7 Mode Keluarga. |
| D28 | Keluar Penjara dengan kembar | Langsung jalan sesuai dadu, tanpa giliran tambahan. Gagal kembar ke-3 → wajib bayar 50 lalu jalan. | Aturan Monopoli klasik yang dirujuk 6.3. |
