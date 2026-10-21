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

## Tahap 3 — Bot

| # | Topik | Keputusan | Alasan |
|---|-------|-----------|--------|
| D29 | Perhitungan deduksi | `DeductionTracker` menghitung **eksak** jumlah pembagian kartu yang konsisten untuk tiap 216 kombinasi amplop (memo atas sisa kartu), bukan sampling. | Lebih akurat daripada sampling dan tetap cepat (6 pemain < 1 detik di JVM); hasil deterministik untuk test. |
| D30 | Sumber constraint | Pasti: tangan sendiri, Kedutaan, `knowledge` view, tangan pemain bangkrut, amplop setelah Terbakar. "Tidak punya": lewat Dugaan, `no_card` bertipe. "Punya salah satu": menunjukkan kartu untuk Dugaan orang lain, Telegram Terbuka / Laporan Intelijen / Peta Kota. Perdagangan kartu Bukti menghapus constraint lama kedua pihak. | Semua dibaca dari `PlayerView` (log publik + pengetahuan sendiri). |
| D31 | Constraint bertentangan | Bila tak ada pembagian yang konsisten (kebohongan Agen Ganda, catatan basi), constraint dilonggarkan bertahap: buang "punya salah satu" → buang "tidak punya" → buang catatan selain tangan sendiri & Kedutaan. | Bot tidak boleh macet karena satu data salah. |
| D32 | Deduksi Normal vs Hard | Normal & Easy memakai deduksi dasar (tanpa "punya salah satu"); Hard memakai semua constraint. | "Deduksi dasar" vs "DeductionTracker penuh" (BAGIAN 10). |
| D33 | Dugaan maksimal-informasi | Per kategori pilih kartu dengan p(1−p) terbesar (p = peluang di amplop); kategori yang sudah terpecahkan → sebut kartu sendiri. NKVD memakai 2 tersangka saat ≥ 3 kandidat tersangka. | Heuristik informasi yang murah dan tidak membocorkan jawaban yang sudah diketahui. |
| D34 | Ambang tuduhan | Easy ≥ 0,95; Normal ≥ 0,9; Hard ≥ 0,9, ≥ 0,6 sejak Jam Perang 11, ≥ 0,4 bila skor proyeksi < 75% pemimpin sejak Jam Perang 11 (langkah 12 langsung mengakhiri permainan, jadi "di langkah 12" dibaca sebagai giliran terakhir sebelum Front #12). | BAGIAN 10. |
| D35 | Kebohongan Agen Ganda | Bot Agen Ganda berbohong sekali saat penanya adalah pemain dengan skor proyeksi tertinggi; Dugaannya sendiri menyebut kartu dengan peluang amplop terendah. | "arahkan menjauh dari jawaban, bohong saat paling merugikan pemain terdepan". |
| D36 | Perdagangan bot | Bot tidak mengajukan tawaran; hanya menjawab. Nilai = selisih uang + harga properti (+ bonus melengkapi distrik) ± 60 per kartu Bukti ± 50 per Surat Bebas; Hard menerima bila nilai naik. | Cukup untuk lawan manusia; tawaran proaktif bisa ditambah setelah simulasi Tahap 4. |
| D37 | Easy | Dengan peluang `randomness` memilih aksi legal acak berbobot (kecuali tuduhan, tebakan akhir, utang, dan fase Bisnis), sisanya aturan sederhana. | "acak berbobot di antara aksi legal". |

## Perombakan aturan — target permainan ±4 jam

Simulasi antar-bot di Tahap 3 menunjukkan misteri terpecahkan dalam 4–9 putaran (±18 menit), sehingga
ekonomi dan Jam Perang tidak pernah berperan. Atas permintaan pemilik proyek, aturan diubah dengan target
**durasi ±4 jam** (45 detik per giliran) dan ketiga cara menang benar-benar muncul. Semua angka di `balance.json`.

| # | Aturan / elemen | Isi | Alasan |
|---|-----------------|-----|--------|
| D38 | **Intel** (token baru) | Mulai 1, maks 4, +1 tiap melewati START, +1 saat Dekode berhasil. Dugaan dari properti sendiri dan Ruang Interogasi butuh 1 Intel; Tuduhan Akhir butuh 2 Intel. Dugaan gratis (Front Agu 1944, Rekrut Informan) tetap gratis. | Informasi menjadi sumber daya yang harus dikelola, bukan arus tanpa batas. |
| D39 | **Bungkam** | Saat diminta menunjukkan kartu, pemain boleh membayar 1 Intel untuk diam; Dugaan berlanjut ke pemain berikutnya, penanya tidak belajar apa pun dari pemain itu. | Pertahanan informasi; memberi pilihan bermakna bagi penjawab. |
| D40 | **Lewat Dugaan privat** | Pemain yang tidak punya kartu hanya diketahui penanya (dan dirinya), bukan semua pemain. | Pemain ketiga tidak lagi mendapat deduksi gratis. |
| D41 | **Kunci Tuduhan** | Tuduhan Akhir (Kedutaan, melintas, April) baru boleh sejak langkah Jam Perang 10. | Misteri tidak bisa mengakhiri permainan sebelum ekonomi berkembang. |
| D42 | **Map Merah berpindah** | Front Okt 1944 (Lokasi), Jan 1945 (Tersangka), Mar 1945 (Barang), Apr 1945 (Lokasi): kartu kategori itu dari amplop + tangan pemain aktif dikocok dan dibagi ulang; jumlah per pemain tetap; pengetahuan kategori itu dihapus (kecuali Kedutaan & tangan pemain bangkrut). | Misteri tetap hidup sampai akhir; jawaban bisa berubah. |
| D43 | **Jam Perang berbasis lintasan START** | Jam maju setiap rata-rata 5,3 lintasan START (total semua pemain; pecahan dibulatkan per langkah). Tidak bergantung jumlah pemain, sehingga durasi 3–6 pemain setara. Mode lama tetap tersedia lewat `GameConfig.warClockMode`. | Tempo dapat disetel tepat; dulu satu lintasan per putaran membuat 12 langkah ±12 putaran. |
| D44 | **Kecurigaan** (meteran baru) | Pasar Gelap +2, menang Kasino +1, beberapa kartu ±. Mencapai 6 → ditangkap (masuk Penjara, meteran 0). Uang Suap −2, melewati START −1. | Risiko nyata untuk aksi "kotor"; menambah ketegangan. |
| D45 | **Misi Rahasia** | 14 misi (`missions.json`); tiap pemain menerima 1 secara rahasia; poin 30–55 bila terpenuhi saat Akhir Perang. | Tujuan pribadi yang membuat jalur skor berbeda-beda. |
| D46 | **Dua faksi baru** | Penyelundup Spanyol (Pasar Gelap 100, tanpa Kecurigaan) dan Pembelot Abwehr (+2 Intel awal & batas). Total 8 faksi untuk 3–6 kursi. | Variasi pilihan faksi. |
| D47 | **16 kartu baru** | Sandi & Telegram masing-masing 24 kartu; efek baru `gainIntel`, `loseIntel`, `addSuspicion`, `reduceSuspicion`. | Mengaitkan kartu dengan elemen baru. |
| D48 | **Skala sewa bangunan** | `buildingRentPercent` = 48: sewa properti berbangunan (Rumah Aman/Markas) dikali 48%. Sewa dasar & Sel Mata-Mata tetap. | Dengan bot yang saling berdagang, sewa penuh membuat 75% permainan berakhir bangkrut sebelum 3 jam. |
| D49 | **Bot berdagang** | Normal/Hard menawar properti lawan yang melengkapi distriknya (harga × 1,6 / 1,8), maksimal 1 tawaran per giliran. | Tanpa perdagangan distrik hampir tak pernah lengkap sehingga ekonomi mati. |
| D50 | Test aturan dasar | Test nilai pasti BAGIAN 14 memakai "aturan dasar" (sewa 100%, Jam klasik, Tuduhan tanpa kunci/Intel, tanpa misi) lewat `GameStateFixture(classic = true)`; aturan baru diuji di `NewRulesTest`. | Nilai BAGIAN 14 tetap terverifikasi sebagai dasar; balance aktif boleh berubah. |

Hasil simulasi balance aktif (200 game per meja, bot 2 Hard + 2 Normal kecuali disebut):

| Meja | Median durasi | P10–P90 | Detektif | Akhir Perang | Ekonomi |
|------|---------------|---------|----------|--------------|---------|
| 4 pemain (2H 2N) | 240 mnt | 201–266 | 50% | 32% | 18% |
| 3 pemain (H N E) | 225 mnt | 155–264 | 27% | 29% | 44% |
| 5 pemain (2H 2N E) | 255 mnt | 222–270 | 21% | 69% | 10% |
| 6 pemain (2H 2N 2E) | 261 mnt | 237–279 | 24% | 72% | 4% |

Catatan: proporsi cara menang masih bergeser menurut jumlah pemain — dianalisis menyeluruh di Tahap 4.

## Tahap 4 — Simulasi keseimbangan

| # | Topik | Keputusan | Alasan |
|---|-------|-----------|--------|
| D51 | **Dominasi Ekonomi** (meja 5–6 pemain) | Saat Front dibalik sejak langkah 6, pemain aktif yang kekayaan bersihnya (uang + harga properti + biaya bangun dasar) ≥ 65% total meja langsung menang Ekonomi. Hanya untuk ≥ 5 pemain (`dominanceMinPlayers`). | Membangkrutkan 4–5 lawan hampir mustahil dalam 4 jam; tanpa aturan ini Ekonomi hanya 3–9% di meja besar. |
| D52 | OSS | Gaji START OSS 250 → 225. | OSS menang 31% di meja 4 pemain (1,26× rata-rata) dan 30% di meja 5 pemain. |
| D53 | Target keseimbangan | Durasi median 210–270 menit (menggantikan 60–120 karena target baru 4 jam); faksi ≤ 1,3× rata-rata; tiap cara menang ≥ 10%; tanpa anomali. | Sesuai permintaan pemilik proyek. |
| D54 | Batas anomali | 4.000 aksi (bukan 2.000). | Permainan 4 jam wajar berisi 1.100–1.500 aksi; 2.000 akan salah menandai meja 6 pemain. |
| D55 | Faksi per permainan | Dirotasi deterministik (`(game + kursi×3) mod 8`) sehingga tiap faksi duduk di setiap kursi sama sering. | Win rate faksi tidak tercampur efek posisi duduk. |
| D56 | Sewa dibayar uang | Dihitung dari transfer ber-alasan `rent` (termasuk setelah mengumpulkan dana). | Pembanding langsung untuk bayar-informasi. |

Hasil (balance aktif, seed 42; laporan lengkap di `docs/sim/`):

| Meja | Game | Median | P10–P90 | Detektif | Ekonomi | Skor Mei 1945 | Faksi maks | Target |
|------|------|--------|---------|----------|---------|---------------|------------|--------|
| 3 (H N N) | 300 | 224 mnt | 155–255 | 38% | 44% | 17% | 1,12× | ✓ |
| 4 (H H N N) | 1000 | 242 mnt | 203–268 | 48% | 19% | 33% | 1,11× | ✓ |
| 5 (H H N N E) | 300 | 255 mnt | 195–276 | 13% | 25% | 61% | 1,17× | ✓ |
| 6 (H H N N E E) | 300 | 259 mnt | 215–277 | 17% | 19% | 64% | 1,33× | ✗ (OSS 22% vs 16,7%; 300 game → galat ±0,15×) |

A/B `infoPaymentQuota = 1` (4 pemain, 1000 game): bayar-informasi turun 53 → 30 per game, bayar-uang naik 48 → 69;
durasi dan cara menang hampir tidak berubah (Detektif +3 poin, Skor −4 poin). Korelasi porsi bayar-informasi dengan
menang lemah (+0,13): bayar-informasi bukan strategi dominan. Kuota 2 dipertahankan.

Catatan: Kecurigaan tidak pernah memicu penangkapan pada bot (bot menghindarinya); elemen ini bekerja sebagai
pencegah, bukan hukuman yang sering terjadi.

## Ekspansi "Kota Para Mata-Mata" — lebih dramatis & interaktif

| # | Elemen | Isi | Alasan |
|---|--------|-----|--------|
| D57 | **Kartu Taktik** (`tactics.json`, 24 kartu, 12 jenis) | Mulai dengan 1, +1 tiap melewati START (maks 3 di tangan). Maksimal 1 dimainkan per giliran pada fase Bisnis; Penyamaran dimainkan saat harus membayar sewa. Jenis: Penyamaran, Jaringan Kurir, Suap Inspektur, Pengintaian, Tekanan Diplomatik, Pemerasan, Sabotase, Dana Gelap, Informan Bayaran, Sandi Cadangan, Mogok Buruh, Medali Sekutu. Kartu terpakai kembali ke bawah tumpukan. | Interaksi langsung antar-pemain dan momen balas dendam; tiap giliran punya pilihan taktis. |
| D58 | **Segel properti** | Sabotase/Mogok Buruh membuat petak tanpa sewa sampai akhir putaran berikutnya. | Cara menyerang pemimpin ekonomi tanpa menghancurkan permanen. |
| D59 | **Operasi Kota** (`operations.json`, 18) | 3 Operasi terbuka. Di akhir setiap giliran, pemain pertama (mulai pemain giliran) yang memenuhi syarat mengklaimnya: uang/Intel/Prestise; Operasi baru dibuka. | Perlombaan tujuan publik — semua pemain melihat siapa yang hampir menang. |
| D60 | **Prestise** | Poin kemenangan dari Operasi & Medali Sekutu, ditambahkan ke skor Mei 1945 (ikut ×0,5 bila Terbakar). | Jalur skor baru selain kekayaan. |
| D61 | **Patroli PIDE & Kontak Netral** | Dua pion netral bergerak tiap awal putaran (Patroli 2 dadu, Kontak 1 dadu). Berhenti di petak Patroli: Kecurigaan +2 (bisa langsung ditangkap). Berhenti di petak Kontak: +1 Intel & +50. | Papan terasa hidup; Kecurigaan kini benar-benar menimbulkan penangkapan (±0,7 per permainan). |
| D62 | **Front varian** | 12 Front baru (varian B). Tiap langkah memakai A atau B secara acak, rahasia sampai dibalik. Efek baru: semua +Intel, semua ±Kecurigaan, semua +uang. Map Merah tetap berpindah di langkah 5, 8, 10, 11 pada kedua varian. | 4.096 kombinasi alur perang — tiap sesi berbeda. |
| D63 | **Bantuan Sekutu** | Setiap Front dibalik (kecuali yang pertama), pemain aktif dengan kekayaan bersih terendah menerima 100 + 1 Intel. | Pemain tertinggal tetap punya harapan dalam permainan 4 jam. |
| D64 | **10 Misi baru** (24 total) | Syarat baru: Prestise, jumlah Operasi, Taktik dimainkan, jumlah Markas, distrik lengkap. | Selaras dengan elemen baru. |
| D65 | Sewa bangunan 48% → 58% | Elemen baru (Penyamaran, segel, bantuan) meredam kebangkrutan; Ekonomi turun ke 3,5% sebelum disetel ulang. | Menjaga ketiga cara menang ≥ 10%. |
| D66 | Bot | Bot menilai tiap Kartu Taktik (Intel, Kecurigaan, uang, Prestise, nilai informasi, target pemimpin, perkiraan sewa yang hilang) dan memainkan yang bernilai ≥ 20; Penyamaran untuk sewa ≥ maks(120, 20% uang). | Agar simulasi mencerminkan permainan dengan elemen baru. |

Hasil simulasi setelah ekspansi (seed 42):

| Meja | Game | Median | Detektif | Ekonomi | Skor Mei 1945 | Faksi maks | Ditangkap/game | Target |
|------|------|--------|----------|---------|---------------|------------|----------------|--------|
| 3 pemain | 300 | 222 mnt | 45% | 37% | 19% | 1,22× | 0,63 | ✓ |
| 4 pemain | 1000 | 238 mnt | 55% | 16% | 29% | 1,12× | 0,68 | ✓ |
| 5 pemain | 300 | 254 mnt | 22% | 23% | 54% | 1,31× | 0,73 | hampir (faksi) |
| 6 pemain | 300 | 258 mnt | 19% | 16% | 66% | 1,20× | 0,72 | ✓ |

## Tahap 5 — UI inti

| # | Keputusan | Alasan |
|---|-----------|--------|
| D67 | Modul baru **`:session`** (Kotlin murni): `GameSession` (pass-and-play, giliran bot, simpan/muat), `LogText`/`Names`/`ActionText` (teks id/en dari konten), `Choices` (mengelompokkan aksi legal jadi menu), `BoardGeometry`. | Semua logika layar bisa diuji di JVM tanpa Android; app hanya merender. |
| D68 | UI hanya membaca `PlayerView` + daftar aksi legal. Semua tombol berasal dari `legalActions`; satu-satunya aksi yang disusun UI adalah Tawaran Dagang, dan itu diperiksa `checkAction` sebelum dikirim. Panel "Semua aksi" menjamin setiap aksi legal selalu bisa dipilih. | Tidak mungkin UI menampilkan aksi ilegal atau membocorkan rahasia. |
| D69 | **Layar privasi**: bila keputusan pindah ke manusia lain (≥ 2 manusia), layar tertutup sampai "Saya siap". Sebelum ada yang membuka layar, sudut pandang tidak pernah memakai pemain yang sedang ditunggu. Dengan 1 manusia tidak ada layar privasi. | Pass-and-play aman tanpa mengintip. |
| D70 | **Simpan otomatis** ke 3 slot (JSON `{"version":1,"revealedTo":…,"state":…}` + file meta). Disimpan setelah tiap aksi manusia dan tiap 40 langkah bot; muat memeriksa versi dan invariant. "Lanjutkan" = slot terbaru yang belum selesai. | Permainan 4 jam harus bisa dijeda kapan saja; dites termasuk muat di tengah lelang. |
| D71 | Bot berjalan di `Dispatchers.Default` di balik `Mutex`, dengan jeda per langkah (Lambat 900 / Normal 380 / Cepat 120 / Instan 0 ms) dan tombol "Percepat bot". Langkah bot memakai RNG turunan seed + nomor aksi, sehingga permainan yang dimuat ulang berlanjut identik. | UI tetap responsif saat bot Sulit berpikir; deterministik. |
| D72 | Papan: tata letak 11×11 persegi (START kanan bawah, searah jarum jam), petak sebagai composable biasa (bukan Canvas) agar teks & ketukan mudah; cubit untuk zoom, geser untuk pan, ketuk petak untuk detail di tengah papan. Hanya API Compose stabil (tanpa FlowRow/ModalBottomSheet/TopAppBar eksperimental). | Bisa dibangun di CI tanpa opt-in eksperimental. |
| D73 | Teks UI statis di `strings.xml` (id + en) dibangkitkan dari satu tabel (`tools/strings.py`) agar kunci kedua bahasa selalu sama; teks konten (petak, kartu, Front, log) dari JSON lewat `Names`. | Lokalisasi konsisten. |
| D74 | Mode tanpa manusia diperbolehkan (menonton bot). | Demo & uji keseimbangan visual. |

## Tahap 6 — UI lengkap

| # | Keputusan | Alasan |
|---|-----------|--------|
| D75 | **Font** dibundel dari repositori resmi Google Fonts: Playfair Display SC (judul poster, OFL), Special Elite (teks kartu mesin ketik, Apache 2.0), Courier Prime (isi & angka tabular, OFL). Lisensi di `docs/licenses/`. | Arah visual BAGIAN 12.3 tanpa aset berlisensi bermasalah. |
| D76 | **Audio prosedural** (`session/audio/Synth.kt`): 11 efek (dadu di meja kayu, langkah, koin, jackpot, telegraf Morse, roda sandi, sirene jauh, stempel, pintu sel, kartu, alarm) dan musik fado ±16 detik (gitar arpeggio Karplus-Strong Am–Dm–E7–Am + melodi tremolo guitarra + derak gramofon). Disintesis sekali ke WAV di cache; SoundPool untuk efek, MediaPlayer berulang untuk musik. | Tidak ada aset pihak ketiga; sumber "buatan sendiri" terdokumentasi dan diuji di JVM. |
| D77 | **Isyarat animasi** (`Cue`) diturunkan dari daftar GameEvent yang boleh dilihat penonton: dadu berguling, uang melayang pembayar → penerima, kartu Sandi/Telegram dibalik, pita teleks Front, cap TERBAKAR/TERPECAHKAN/DITANGKAP, hujan koin jackpot. Diantrikan (maks 6) dan dimainkan satu per satu; ketuk untuk melewati. Bot menunggu cap/Front tampil. | BAGIAN 12.1 — UI selalu sinkron dengan engine; isyarat privat tidak pernah bocor (dites). |
| D78 | Pion berjalan petak demi petak (bunyi langkah), mundur ≤ 3 petak dianimasikan mundur, perpindahan lain (penjara, jalur, kartu) melompat. Kamera mengikuti pion aktif saat papan diperbesar; papan bergetar saat penangkapan. | Papan terasa hidup tanpa menunda permainan. |
| D79 | **Haptik**: lempar dadu sendiri (ringan), menerima sewa ≥ 200 dan tuduhan dibuka (kuat). | BAGIAN 12.4. |
| D80 | **Catatan Detektif** = grid 18 kartu × (tiap pemain + Amplop): status otomatis dari pengetahuan (● milik, ◉ dari Dugaan, $ dari sewa, ◍ publik), tanda manual ✓ ✗ ? per sel dan catatan teks per kartu (disimpan per permainan & kursi), tab Log Dugaan, dan panel **Kemungkinan Amplop** memakai `DeductionTracker` yang sama dengan bot (bisa dimatikan). | BAGIAN 12.2 no. 6. |
| D81 | **Aksesibilitas**: semua tombol ≥ 48dp, petak papan punya contentDescription (nama, distrik, pemilik, bangunan, pion), sel catatan dan meter Kecurigaan berlabel; skala teks 100–200% (papan tetap 100% karena bisa dicubit); HUD & tab menyesuaikan diri saat teks besar; mode buta warna (pola + ikon distrik, huruf faksi di pion); warna teks di atas warna faksi dipilih menurut luminans (kontras). Mode "Kurangi animasi". | BAGIAN 12.5. Diverifikasi lewat tangkapan layar teks 200% & lanskap. |
| D82 | **Bahasa** bisa dipilih di Pengaturan (Sistem / Indonesia / English); Activity dibuat ulang dengan Configuration ber-locale. Angka escudo memakai pemisah ribuan sesuai bahasa. | BAGIAN 12.2 no. 18. |
| D83 | **Arsip** (JSON Lines, 200 entri terakhir): riwayat, win rate per faksi, statistik pribadi. Layar Akhir menambah tabel skor rinci, pengungkapan Agen Ganda & kebohongannya, statistik (sewa terbesar, informasi bocor, dll.), Main Lagi, Bagikan Seed. | BAGIAN 12.2 no. 16 & 19. |
| D84 | **Cara Bermain**: 10 bagian aturan, glosarium (petak, Bukti, faksi, Taktik, Operasi, 24 Front) dari konten JSON, dan giliran latihan terpandu melawan 2 bot Mudah dengan gelembung petunjuk per jenis keputusan. | BAGIAN 12.2 no. 17. |
| D85 | **Mode Kampanye** di Setup: tiap kursi mulai dengan 2 properti di distrik berbeda, ditentukan seed (`CampaignPreset`). | Mode keempat BAGIAN 12.2 no. 3 kini bisa dipilih. |
| D86 | **Tangkapan layar otomatis**: test Robolectric dengan grafik native merender 30+ layar (termasuk setiap jenis keputusan, teks 200%, buta warna, lanskap, bahasa Indonesia) dan CI menerbitkannya ke cabang `screenshots`. | Tinjauan visual tanpa emulator; beberapa bug tata letak ditemukan & diperbaiki dari sini. |
| D87 | `:app:assembleRelease` (R8 + aturan ProGuard untuk kotlinx.serialization dan model) dibangun di CI; APK rilis diunggah sebagai artefak. | Syarat selesai Tahap 6. |
