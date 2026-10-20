package id.diran.lisbon1944.sim

import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.GameMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SimArgsTest {

    @Test
    fun `validate dan balance terbaca`() {
        assertEquals(SimArgs(validateOnly = true, balancePath = "b.json"), SimArgs.parse(arrayOf("--validate", "--balance", "b.json")))
    }

    @Test
    fun `perintah simulasi lengkap BAGIAN 11 terbaca`() {
        val a = SimArgs.parse(
            "--games 1000 --players 4 --bots hard,hard,normal,normal --seed 42 --balance b.json --mode standard --out report.csv".split(" ").toTypedArray(),
        )
        assertEquals(1000, a.games)
        assertEquals(4, a.players)
        assertEquals(listOf(BotLevel.HARD, BotLevel.HARD, BotLevel.NORMAL, BotLevel.NORMAL), a.seatLevels)
        assertEquals(42L, a.seed)
        assertEquals("b.json", a.balancePath)
        assertEquals(GameMode.STANDARD, a.mode)
        assertEquals("report.csv", a.out)
        assertTrue(a.abTest)
    }

    @Test
    fun `argumen tidak dikenal atau tidak konsisten ditolak`() {
        assertFailsWith<IllegalArgumentException> { SimArgs.parse(arrayOf("--terbang")) }
        assertFailsWith<IllegalArgumentException> { SimArgs.parse(arrayOf("--validate", "--balance")) }
        assertFailsWith<IllegalArgumentException> { SimArgs.parse(emptyArray()) }
        assertFailsWith<IllegalArgumentException> { SimArgs.parse(arrayOf("--players", "7")) }
        assertFailsWith<IllegalArgumentException> { SimArgs.parse(arrayOf("--players", "4", "--bots", "hard,easy")) }
        assertFailsWith<IllegalArgumentException> { SimArgs.parse(arrayOf("--bots", "jenius,hard,hard,hard")) }
    }

    @Test
    fun `validasi konten bawaan sukses`() {
        val out = StringBuilder()
        assertEquals(0, runValidate(SimArgs(validateOnly = true), out))
        assertTrue(out.contains("konten valid"))
        assertTrue(out.contains("216 jawaban amplop"))
    }

    @Test
    fun `balance alternatif yang rusak ditolak`() {
        val f = File.createTempFile("balance", ".json")
        try {
            val text = javaClass.getResource("/content/balance.json")!!.readText().replace("\"hqStock\": 12", "\"hqStock\": 0")
            f.writeText(text)
            val out = StringBuilder()
            assertEquals(1, runValidate(SimArgs(validateOnly = true, balancePath = f.path), out))
            assertTrue(out.contains("hqStock harus positif"))
        } finally {
            f.delete()
        }
    }
}
