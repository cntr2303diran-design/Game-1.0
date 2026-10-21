package id.diran.lisbon1944.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import id.diran.lisbon1944.bot.Bots
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.PlayerSetup
import id.diran.lisbon1944.engine.rng.SplitMix64Rng
import id.diran.lisbon1944.ui.theme.LisbonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Asap UI: merender layar Compose sungguhan selama permainan penuh.
 * Kursi manusia "dimainkan" oleh bot lewat ViewModel, sehingga setiap panel keputusan ikut dirender.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FullGameUiTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var vmRef: AppViewModel

    private fun state(): String {
        val vm = vmRef
        val ui = vm.ui
        return "screen=${vm.screen} err=${vm.loadError} content=${vm.content != null} busy=${vm.busy} " +
            (ui?.let { "round=${it.view.roundNumber} step=${it.view.warStep} waiting=${it.waitingOn} kind=${it.view.waitingKind} bot=${it.isBotTurn} over=${it.isOver} privacy=${it.needsPrivacy} legal=${it.legal.size} log=${it.view.log.size} msg=${it.message}" } ?: "ui=null")
    }

    private fun waitFor(ms: Long, what: String, cond: () -> Boolean) {
        // Looper utama Robolectric (mode PAUSED) harus dijalankan manual agar coroutine Main berlanjut.
        val end = System.currentTimeMillis() + ms
        while (true) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            rule.waitForIdle()
            if (cond()) return
            if (System.currentTimeMillis() > end) throw AssertionError("Timeout menunggu $what — ${state()}")
            Thread.sleep(2)
        }
    }

    private fun setUp(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        SaveStore(app).saveSettings(Settings(speed = Speed.INSTANT))
        val vm = AppViewModel(app)
        vmRef = vm
        rule.setContent { LisbonTheme { LisbonApp(vm) } }
        waitFor(60_000, "konten") { vm.content != null }
        return vm
    }

    private fun config(vm: AppViewModel, humans: Int, n: Int) = GameConfig(
        players = (0 until n).map {
            if (it < humans) PlayerSetup("Agen ${it + 1}", Faction.entries[it])
            else PlayerSetup("Bot ${it + 1}", Faction.entries[it], isBot = true, botLevel = BotLevel.NORMAL)
        },
        balance = vm.content!!.balance,
    )

    @Test
    fun botsOnlyGameRunsToTheEndScreen() {
        val vm = setUp()
        vm.newGame(config(vm, humans = 0, n = 4), seed = 5)
        waitFor(300_000, "layar akhir") { vm.screen == Screen.END }
        assertNotNull(vm.ui?.view?.winner)
        assertTrue(vm.slots.any { it?.meta?.finished == true })
    }

    @Test
    fun humanGameWithAllPanelsRunsToTheEnd() {
        val vm = setUp()
        vm.newGame(config(vm, humans = 2, n = 4), seed = 9)
        waitFor(60_000, "layar permainan") { vm.screen == Screen.GAME && vm.ui != null }
        val pilot = Bots(vm.content!!).create(BotLevel.HARD)
        val rng = SplitMix64Rng.fromSeed(3)
        var acts = 0
        var privacy = 0
        var tabsVisited = false
        while (acts < 6_000) {
            waitFor(120_000, "giliran manusia (aksi $acts)") {
                val ui = vm.ui
                ui != null && (ui.isOver || ui.needsPrivacy || (!ui.isBotTurn && ui.legal.isNotEmpty() && ui.view.myDecision != null))
            }
            val ui = vm.ui!!
            if (ui.isOver) break
            if (ui.needsPrivacy) {
                privacy++
                rule.onAllNodesWithText("ready", substring = true, ignoreCase = true).let {
                    if (it.fetchSemanticsNodes().isNotEmpty()) it[0].performClick() else vm.reveal()
                }
                waitFor(20_000, "buka privasi") { vm.ui?.needsPrivacy == false }
                continue
            }
            if (!tabsVisited) {
                // Render setiap tab sekali, lalu kembali ke tab Aksi.
                for (label in listOf("Agents", "Notes", "Deeds", "Cards", "Log", "Act")) {
                    val nodes = rule.onAllNodesWithText(label, substring = true)
                    if (nodes.fetchSemanticsNodes().isNotEmpty()) nodes[0].performClick()
                    rule.waitForIdle()
                }
                tabsVisited = true
            }
            val seq = ui.view.log.lastOrNull()?.seq
            vm.act(pilot.decide(ui.view, ui.legal, rng))
            acts++
            waitFor(60_000, "hasil aksi $acts") { vm.ui?.view?.log?.lastOrNull()?.seq != seq || vm.ui?.message != null }
            if (vm.ui?.message != null) {
                // Aksi bot ditolak (seharusnya tidak terjadi) — pakai aksi legal pertama agar tidak macet.
                vm.act(vm.ui!!.legal.first())
            }
        }
        waitFor(120_000, "akhir permainan") { vm.ui?.isOver == true }
        assertTrue("layar privasi harus muncul pada pass-and-play", privacy > 0)
        
        assertEquals(true, vm.ui?.isOver)
    }

    @Test
    fun settingsAndNotebookMarksRespondToTaps() {
        val vm = setUp()
        vm.go(Screen.SETTINGS)
        waitFor(10_000, "pengaturan") { vm.screen == Screen.SETTINGS }
        rule.onAllNodesWithText("Colour-blind mode", substring = true)[0].performClick()
        waitFor(5_000, "mode buta warna") { vm.settings.colorBlind }
        rule.onAllNodesWithText("200%", substring = true)[0].performClick()
        waitFor(5_000, "teks 200%") { vm.settings.textScale == 2f }
        vm.updateSettings(vm.settings.copy(textScale = 1f))

        vm.newGame(config(vm, humans = 1, n = 3), seed = 4)
        waitFor(60_000, "giliran manusia") { vm.ui?.let { !it.isBotTurn && it.view.myDecision != null } == true }
        rule.onAllNodesWithText("Notes", substring = true)[0].performClick()
        rule.waitForIdle()
        val cells = rule.onAllNodesWithContentDescription(": —", substring = true)
        assertTrue("sel catatan kosong harus ada", cells.fetchSemanticsNodes().isNotEmpty())
        cells[0].performClick()
        waitFor(5_000, "tanda ✓") { vm.marks().values.any { it == "✓" } }
    }
}
