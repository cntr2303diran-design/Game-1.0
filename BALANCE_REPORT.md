# Laporan Keseimbangan — LISBON 1944

Simulasi bot-vs-bot dengan aturan versi 4 jam + ekspansi "Kota Para Mata-Mata" (D38–D66).
Seed 42, mode Standar, 45 detik per giliran. Laporan lengkap: [`docs/sim/`](docs/sim).

| | 4 pemain | 6 pemain |
|---|---|---|
| Permainan | 1.000 | 1.000 |
| Bot | hard, hard, normal, normal | hard, hard, normal, normal, easy, easy |
| Durasi median | **238 menit** (P10 196 · P90 268) | **260 menit** (P10 221 · P90 279) |
| Giliran/game | 314 | 338 |
| Menang Detektif | 54,9% | 22,0% |
| Menang Ekonomi | 15,9% | 13,9% |
| Menang Skor Mei 1945 | 29,2% | 64,1% |
| Faksi terkuat / rata-rata | 1,12× (MI6) | 1,10× (MI6, Jurnalis Swiss) |
| Faksi terlemah / rata-rata | 0,91× (Jurnalis Swiss) | 0,85× (NKVD) |
| Bayar sewa: informasi / uang per game | 48,9 / 48,6 | 56,3 / 72,5 |
| Korelasi porsi bayar-informasi ↔ menang | +0,14 | +0,30 |
| Tuduhan per game (salah) | 0,59 (7,3%) | 0,27 (18,2%) |
| Jam Perang saat misteri terpecahkan | 10,2 | 10,4 |
| Bangkrut / ditangkap per game | 1,14 / 0,68 | 0,61 / 0,81 |
| Anomali (> 8.000 aksi) | 0 | 0 |

## Target

| Target | 4 pemain | 6 pemain |
|--------|----------|----------|
| Durasi median 210–270 menit | ✓ 238 | ✓ 260 |
| Tidak ada faksi > 1,3× rata-rata | ✓ 1,12× | ✓ 1,10× |
| Ketiga cara menang ≥ 10% | ✓ | ✓ |
| Tanpa anomali | ✓ | ✓ |

## A/B: `infoPaymentQuota = 1` (house rule)

| Metrik | 4p bawaan → kuota 1 | 6p bawaan → kuota 1 |
|--------|--------------------|--------------------|
| Bayar-informasi/game | 48,9 → 28,4 | 56,3 → 36,1 |
| Menang Detektif | 54,9% → 55,0% | 22,0% → 22,2% |
| Menang Ekonomi | 15,9% → 17,3% | 13,9% → 13,8% |
| Rasio faksi maks | 1,12 → 1,08 | 1,10 → 1,15 |

Kuota 1 mengurangi bayar-informasi ±40% tetapi hampir tidak mengubah cara menang atau durasi. **Kuota bawaan (2)
dipertahankan**; kuota 1 tetap tersedia sebagai house rule.

## Pengamatan

1. **Jumlah pemain menggeser cara menang.** Di meja 4 orang misteri sering terpecahkan (55%); di meja 6 orang tiap
   pemain memegang lebih sedikit kartu dan Dugaan lebih sering terbantah, sehingga permainan lebih sering berakhir
   dengan skor Mei 1945 (64%). Keduanya masih dalam target, tetapi pengalaman 6 pemain lebih "ekonomi + skor".
2. **Bayar-informasi lebih bernilai di meja besar** (korelasi +0,30 di 6p vs +0,14 di 4p) — pemilik properti
   mengumpulkan petunjuk dari banyak penyewa. Belum dominan, tetapi patut dipantau.
3. **Kursi 5–6 (bot Mudah) hanya menang ±2%** — ini mencerminkan level bot, bukan posisi duduk; kursi 1–4 (Sulit/Normal)
   berimbang 21–26%.
4. **Kecurigaan kini nyata**: ±0,7–0,8 penangkapan per game (Patroli PIDE), tanpa membuat permainan acak.
5. **Tuduhan salah 18% di 6p** (7% di 4p): Map Merah berpindah (langkah 5, 8, 10, 11) lebih sering "menjebak" di meja
   besar karena informasi lebih tersebar.

## Usulan (belum diterapkan — perlu playtest manusia)

| Usulan | Perkiraan efek | Alasan |
|--------|----------------|--------|
| 5–6 pemain: Tuduhan Akhir dibuka sejak langkah 9 (bukan 10) | Detektif 6p naik ke ±28–30% | Menyeimbangkan pengalaman antar jumlah pemain. |
| 5–6 pemain: `infoPaymentMaxRent` turun 10% | Korelasi info–menang 6p turun ke ±0,2 | Mencegah bayar-informasi menjadi strategi dominan di meja besar. |
| NKVD: kemampuan 2 Tersangka bisa dipakai dua kali di 6p | NKVD 0,85× → ±0,95× | Faksi terlemah di 6p; 4p sudah seimbang (1,00×). |
| Peringatan UI saat Map Merah berpindah ("kartu X mungkin sudah tidak di amplop") | Tuduhan salah 6p turun | Manusia lebih mudah lupa daripada bot. |

Simulasi ulang: `./gradlew :sim:run --args="--games 1000 --players 6 --bots hard,hard,normal,normal,easy,easy --seed 42 --out report.csv"`.
