package id.diran.lisbon1944.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import id.diran.lisbon1944.bot.Bots
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerSetup
import id.diran.lisbon1944.engine.rng.SplitMix64Rng
import id.diran.lisbon1944.ui.theme.LisbonTheme
import id.diran.lisbon1944.ui.theme.UiPrefs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Merender layar-layar utama dengan grafik native Robolectric dan menyimpannya sebagai PNG
 * (build/screenshots) — CI menerbitkannya ke cabang `screenshots` untuk ditinjau.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var vm: AppViewModel
    private val dir = File("build/screenshots").apply { mkdirs() }

    private fun idle(ms: Long = 0) {
        val end = System.currentTimeMillis() + ms
        do {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            rule.waitForIdle()
            if (ms > 0) Thread.sleep(10)
        } while (System.currentTimeMillis() < end)
    }

    private fun waitFor(ms: Long, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (true) {
            idle()
            if (cond()) return
            if (System.currentTimeMillis() > end) throw AssertionError("Timeout: $what (screen=${vm.screen}, err=${vm.loadError})")
            Thread.sleep(5)
        }
    }

    private fun shot(name: String) {
        idle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun clickText(text: String) {
        val nodes = rule.onAllNodesWithText(text, substring = true, ignoreCase = true)
        if (nodes.fetchSemanticsNodes().isNotEmpty()) nodes[0].performClick()
        idle()
    }

    private fun start(settings: Settings = Settings(speed = Speed.INSTANT)) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        SaveStore(app).saveSettings(settings)
        vm = AppViewModel(app)
        rule.setContent {
            val s = vm.settings
            LisbonTheme(textScale = s.textScale, prefs = UiPrefs(colorBlind = s.colorBlind, showPossibilities = s.showPossibilities, reduceMotion = true)) {
                LisbonApp(vm)
            }
        }
        waitFor(60_000, "konten") { vm.content != null }
    }

    @Test
    fun captureMainScreensAndAFullGame() {
        start()
        shot("01-splash")
        vm.skipSplash()
        waitFor(10_000, "menu") { vm.screen == Screen.MENU }
        shot("02-menu")
        vm.go(Screen.SETUP); idle(); shot("03-setup")
        vm.go(Screen.SETTINGS); idle(); shot("04-settings")
        vm.go(Screen.HOW_TO); idle(); shot("05-how-to")
        clickText("Glossary"); shot("06-glossary")
        vm.go(Screen.DOSSIER); idle(); shot("07-dossier")

        val content = vm.content!!
        vm.newGame(
            GameConfig(
                players = listOf(
                    PlayerSetup("Agent Lima", Faction.MI6),
                    PlayerSetup("Bot Ana", Faction.NKVD, isBot = true, botLevel = BotLevel.NORMAL),
                    PlayerSetup("Bot Rui", Faction.OSS, isBot = true, botLevel = BotLevel.NORMAL),
                    PlayerSetup("Bot Eva", Faction.SWISS_JOURNALIST, isBot = true, botLevel = BotLevel.NORMAL),
                ),
                balance = content.balance,
            ),
            seed = 7,
        )
        waitFor(60_000, "giliran manusia") { vm.ui?.let { !it.isBotTurn && it.view.myDecision != null } == true }
        shot("10-game-roll")
        val pilot = Bots(content).create(BotLevel.HARD)
        val rng = SplitMix64Rng.fromSeed(1)
        val captured = mutableSetOf<String>()
        var acts = 0
        while (acts < 5000) {
            waitFor(120_000, "keputusan") { vm.ui?.let { it.isOver || (!it.isBotTurn && it.legal.isNotEmpty() && it.view.myDecision != null) } == true }
            val ui = vm.ui!!
            if (ui.isOver) break
            val kind = ui.view.myDecision!!::class.simpleName!!
            if (kind !in captured && ui.view.myDecision !is PendingDecision.AwaitRoll) {
                captured += kind
                shot("11-decision-${kind.lowercase()}")
            }
            if (acts == 40) {
                for ((label, file) in listOf("Agents" to "20-tab-agents", "Notes" to "21-tab-notes", "Deeds" to "22-tab-deeds", "Cards" to "23-tab-cards", "Log" to "24-tab-log")) {
                    clickText(label)
                    shot(file)
                }
                clickText("Act")
            }
            val seq = ui.view.log.lastOrNull()?.seq
            vm.act(pilot.decide(ui.view, ui.legal, rng))
            acts++
            waitFor(60_000, "hasil aksi") { vm.ui?.view?.log?.lastOrNull()?.seq != seq || vm.ui?.message != null }
            if (vm.ui?.message != null) vm.act(vm.ui!!.legal.first())
        }
        waitFor(120_000, "layar akhir") { vm.screen == Screen.END }
        idle(500)
        shot("30-end")
        vm.go(Screen.ARCHIVE); idle(); shot("31-archive")
        assertTrue(vm.archive.isNotEmpty())
    }

    @Test
    fun captureAccessibilityVariants() {
        start(Settings(speed = Speed.INSTANT, colorBlind = true, textScale = 2f))
        vm.skipSplash()
        waitFor(10_000, "menu") { vm.screen == Screen.MENU }
        shot("40-menu-text200")
        val content = vm.content!!
        vm.newGame(
            GameConfig(
                players = listOf(
                    PlayerSetup("Agen", Faction.RESISTANCE),
                    PlayerSetup("Bot A", Faction.MI6, isBot = true, botLevel = BotLevel.EASY),
                    PlayerSetup("Bot B", Faction.OSS, isBot = true, botLevel = BotLevel.EASY),
                ),
                balance = content.balance,
            ),
            seed = 3,
        )
        waitFor(60_000, "giliran manusia") { vm.ui?.let { !it.isBotTurn && it.view.myDecision != null } == true }
        shot("41-game-colorblind-text200")
    }
}
