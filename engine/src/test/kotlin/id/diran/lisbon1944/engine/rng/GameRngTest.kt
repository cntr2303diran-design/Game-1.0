package id.diran.lisbon1944.engine.rng

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GameRngTest {

    @Test
    fun `seed sama menghasilkan urutan identik`() {
        val a = SplitMix64Rng.fromSeed(42)
        val b = SplitMix64Rng.fromSeed(42)
        repeat(1_000) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test
    fun `seed berbeda menghasilkan urutan berbeda`() {
        val a = SplitMix64Rng.fromSeed(1)
        val b = SplitMix64Rng.fromSeed(2)
        assertNotEquals((0 until 8).map { a.nextLong() }, (0 until 8).map { b.nextLong() })
    }

    @Test
    fun `nilai acuan SplitMix64 untuk seed 0`() {
        // Nilai referensi algoritma SplitMix64 (Vigna) untuk status awal 0.
        val rng = SplitMix64Rng.fromSeed(0)
        assertEquals(-0x1ddf57c684e23251L, rng.nextLong()) // 0xE220A8397B1DCDAF
        assertEquals(0x6e789e6aa1b965f4L, rng.nextLong())
    }

    @Test
    fun `status bisa disimpan dan dilanjutkan`() {
        val a = SplitMix64Rng.fromSeed(2026)
        repeat(37) { a.nextInt(6) }
        val resumed = SplitMix64Rng.fromState(a.state())
        repeat(500) { assertEquals(a.nextInt(1000), resumed.nextInt(1000)) }
    }

    @Test
    fun `dadu selalu 1 sampai 6 dan semua sisi muncul`() {
        val rng = SplitMix64Rng.fromSeed(7)
        val counts = IntArray(7)
        repeat(60_000) {
            val d = rng.rollDie()
            assertTrue(d in 1..6)
            counts[d]++
        }
        (1..6).forEach { side -> assertTrue(counts[side] in 9_000..11_000, "sisi $side muncul ${counts[side]}×") }
    }

    @Test
    fun `nextInt menolak batas tidak positif`() {
        assertFailsWith<IllegalArgumentException> { SplitMix64Rng.fromSeed(1).nextInt(0) }
    }

    @Test
    fun `shuffled adalah permutasi dan deterministik`() {
        val items = (1..18).toList()
        val s1 = SplitMix64Rng.fromSeed(99).shuffled(items)
        val s2 = SplitMix64Rng.fromSeed(99).shuffled(items)
        assertEquals(s1, s2)
        assertEquals(items, s1.sorted())
        assertNotEquals(items, s1)
        assertEquals((1..18).toList(), items, "daftar masukan tidak boleh berubah")
    }

    @Test
    fun `pick dari daftar kosong ditolak`() {
        assertFailsWith<IllegalArgumentException> { SplitMix64Rng.fromSeed(1).pick(emptyList<Int>()) }
    }
}
