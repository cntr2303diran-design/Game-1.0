package id.diran.lisbon1944.engine.rng

/**
 * Satu-satunya sumber keacakan engine. Tidak ada Random global.
 * Status internal bisa diambil ([state]) dan dipulihkan ([SplitMix64Rng.fromState]) sehingga
 * replay, save/load, test, dan simulasi bersifat deterministik.
 */
interface GameRng {
    fun nextLong(): Long

    /** Bilangan bulat seragam dalam [0, bound). */
    fun nextInt(bound: Int): Int

    /** Status internal saat ini; disimpan ke GameState.rngState. */
    fun state(): Long

    /** Satu dadu enam sisi: 1..6. */
    fun rollDie(): Int = nextInt(6) + 1

    /** Fisher–Yates; tidak mengubah daftar masukan. */
    fun <T> shuffled(items: List<T>): List<T> {
        val result = items.toMutableList()
        for (i in result.lastIndex downTo 1) {
            val j = nextInt(i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result
    }

    /** Pilih satu elemen acak dari daftar tak kosong. */
    fun <T> pick(items: List<T>): T {
        require(items.isNotEmpty()) { "Tidak bisa memilih dari daftar kosong" }
        return items[nextInt(items.size)]
    }
}

/**
 * SplitMix64 (Steele, Lea & Flood 2014): kecil, cepat, statusnya cukup satu Long,
 * dan menghasilkan urutan identik di JVM maupun Android.
 */
class SplitMix64Rng private constructor(private var s: Long) : GameRng {

    override fun nextLong(): Long {
        s += GOLDEN_GAMMA
        var z = s
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    override fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound harus positif, ditemukan $bound" }
        // Rejection sampling pada 31 bit atas agar tidak bias.
        val limit = Int.MAX_VALUE - (Int.MAX_VALUE % bound)
        while (true) {
            val r = (nextLong() ushr 33).toInt()
            if (r < limit) return r % bound
        }
    }

    override fun state(): Long = s

    companion object {
        private const val GOLDEN_GAMMA = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

        /** RNG baru dari seed permainan. */
        fun fromSeed(seed: Long): SplitMix64Rng = SplitMix64Rng(seed)

        /** Lanjutkan RNG dari status yang disimpan di GameState.rngState. */
        fun fromState(state: Long): SplitMix64Rng = SplitMix64Rng(state)
    }
}
