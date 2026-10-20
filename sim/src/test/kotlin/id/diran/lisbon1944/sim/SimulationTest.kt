package id.diran.lisbon1944.sim

import id.diran.lisbon1944.engine.content.ContentLoader
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.WinType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationTest {

    private val content = ContentLoader.loadDefault()
    private val args = SimArgs(games = 12, players = 4, bots = listOf(BotLevel.HARD, BotLevel.NORMAL, BotLevel.NORMAL, BotLevel.EASY), seed = 7, threads = 2)

    @Test
    fun `simulasi deterministik dan setiap permainan selesai`() {
        val sim = Simulation(content)
        val a = sim.run(args)
        val b = Simulation(content).run(args.copy(threads = 1))
        assertEquals(a, b, "hasil sama walau jumlah thread berbeda")
        assertEquals(12, a.size)
        a.forEach { r ->
            assertTrue(!r.aborted)
            assertTrue(r.winType != null && r.winners.isNotEmpty())
            assertTrue(r.turns > 0 && r.minutes > 0)
        }
    }

    @Test
    fun `metrik dan laporan lengkap termasuk A-B`() {
        val sim = Simulation(content)
        val base = sim.run(args)
        val quota1 = sim.run(args, quotaOverride = 1)
        val m = Metrics.of(base)
        val ab = Metrics.of(quota1)
        assertEquals(1.0, m.winShare.values.sum(), 1e-9)
        assertTrue(m.factionWinRate.isNotEmpty())
        assertEquals(4, m.seatWinRate.size)
        val text = Report.console(args, m, ab)
        listOf("Durasi", "Cara menang", "Win rate per faksi", "Win rate per posisi duduk", "Bayar sewa", "A/B", "Target keseimbangan")
            .forEach { assertTrue(it in text, "laporan tanpa bagian '$it'") }
    }

    @Test
    fun `permainan melewati batas aksi dicatat sebagai anomali beserta seed`() {
        val records = Simulation(content).run(args.copy(games = 3, maxActions = 50))
        val m = Metrics.of(records)
        assertEquals(listOf(7L, 8L, 9L), m.anomalies)
        assertTrue("ANOMALI" in Report.console(args, m, null))
    }

    @Test
    fun `CSV per permainan dan ringkasan ditulis`() {
        val dir = createTempDirectory()
        val out = File(dir, "report.csv")
        val log = StringBuilder()
        assertEquals(0, runSimulation(args.copy(games = 4, out = out.path), log, StringBuilder()))
        val lines = out.readLines()
        assertTrue(lines.first().startsWith("varian,game,seed"))
        assertEquals(1 + 4 + 4, lines.size, "header + 4 bawaan + 4 kuota1")
        val summary = File(dir, "report-summary.csv").readLines()
        assertTrue(summary.first() == "metrik,kuota_bawaan,kuota_1,selisih")
        assertTrue(summary.any { it.startsWith("Menit median") })
        assertTrue("Target keseimbangan" in log)
        dir.deleteRecursively()
    }

    @Test
    fun `korelasi Pearson benar`() {
        assertEquals(1.0, Metrics.correlation(listOf(1.0, 2.0, 3.0), listOf(2.0, 4.0, 6.0)), 1e-9)
        assertEquals(-1.0, Metrics.correlation(listOf(1.0, 2.0, 3.0), listOf(3.0, 2.0, 1.0)), 1e-9)
        assertEquals(0.0, Metrics.correlation(listOf(1.0, 1.0), listOf(0.0, 1.0)), 1e-9)
    }

    @Test
    fun `target dievaluasi`() {
        val m = Metrics.of(Simulation(content).run(args.copy(games = 4)))
        val results = Targets().evaluate(m)
        assertEquals(4, results.size)
        assertTrue(WinType.DETECTIVE in m.winShare)
    }

    private fun createTempDirectory(): File = kotlin.io.path.createTempDirectory("sim").toFile()
}
