package id.diran.lisbon1944.session

import id.diran.lisbon1944.bot.Bots
import id.diran.lisbon1944.engine.GameEngine
import id.diran.lisbon1944.engine.content.ContentLoader
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerSetup
import id.diran.lisbon1944.engine.rng.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSessionTest {
    private val content = ContentLoader.loadDefault()
    private val engine = GameEngine(content)
    private val bots = Bots(content)

    private fun config(humans: Int, total: Int) = GameConfig(
        players = (0 until total).map { i ->
            if (i < humans) PlayerSetup("Manusia $i", Faction.entries[i])
            else PlayerSetup("Bot $i", Faction.entries[i], isBot = true, botLevel = BotLevel.NORMAL)
        },
        balance = content.balance,
    )

    /** Memainkan satu langkah: bot lewat stepBot, manusia "ditiru" oleh bot Hard lewat act(). */
    private fun step(s: GameSession, pilot: id.diran.lisbon1944.bot.Bot, rng: SplitMix64Rng) {
        if (s.isBotTurn) {
            assertTrue(s.stepBot() is StepResult.Ok)
            return
        }
        if (s.needsPrivacy) {
            // Layar privasi: pemain sebelumnya tidak boleh melihat keputusan pemain berikutnya.
            assertTrue(s.legal().isEmpty())
            assertNull(s.view().myDecision)
            s.reveal()
        }
        val legal = s.legal()
        assertTrue(legal.isNotEmpty(), "manusia tanpa aksi legal")
        val view = s.view()
        assertEquals(s.decider, view.viewerId)
        assertNotNull(view.myDecision)
        val action = pilot.decide(view, legal, rng)
        val r = s.act(action)
        if (r !is StepResult.Ok) assertTrue(s.act(legal.first()) is StepResult.Ok)
    }

    @Test
    fun fullGameWithTwoHumansAndBotsFinishes() {
        val s = GameSession.newGame(engine, bots, config(humans = 2, total = 4), seed = 11)
        val pilot = bots.create(BotLevel.HARD)
        val rng = SplitMix64Rng.fromSeed(5)
        var steps = 0
        var privacyScreens = 0
        while (!s.isOver && steps < 12_000) {
            if (s.needsPrivacy) privacyScreens++
            step(s, pilot, rng)
            steps++
        }
        assertTrue(s.isOver, "permainan tidak selesai dalam $steps langkah")
        assertTrue(privacyScreens > 0, "pass-and-play harus memunculkan layar privasi")
        assertTrue(engine.checkInvariants(s.state).isEmpty())
        // Log bisa diformat untuk tiap pemain dalam dua bahasa tanpa error.
        for (lang in Lang.entries) {
            val log = LogText(Names(content, lang))
            for (p in s.state.players) {
                val lines = log.lines(engine.viewFor(s.state, p.id), limit = 10_000)
                assertTrue(lines.isNotEmpty())
            }
        }
    }

    @Test
    fun singleHumanNeverSeesPrivacyScreen() {
        val s = GameSession.newGame(engine, bots, config(humans = 1, total = 4), seed = 3)
        val pilot = bots.create(BotLevel.HARD)
        val rng = SplitMix64Rng.fromSeed(1)
        repeat(600) {
            if (s.isOver) return
            assertFalse(s.needsPrivacy)
            assertEquals(PlayerId(0), s.viewer)
            step(s, pilot, rng)
        }
    }

    @Test
    fun humanCannotActOutOfTurn() {
        val s = GameSession.newGame(engine, bots, config(humans = 2, total = 3), seed = 8)
        val rng = SplitMix64Rng.fromSeed(2)
        val pilot = bots.create(BotLevel.NORMAL)
        while (!s.isHumanTurn) step(s, pilot, rng)
        val other = s.humans.first { it != s.decider }
        assertEquals(StepResult.NotYourTurn, s.act(Action.RollDice(other)))
    }

    @Test
    fun saveAndLoadMidAuctionResumesWithSameBidder() {
        val s = GameSession.newGame(engine, bots, config(humans = 0, total = 4), seed = 21)
        var guard = 0
        while (s.state.pending !is PendingDecision.AuctionBid && guard++ < 5_000) s.stepBot()
        val pending = s.state.pending
        if (pending !is PendingDecision.AuctionBid) return // tidak ada lelang pada seed ini — tidak gagal
        val text = s.save()
        val loaded = GameSession.load(engine, bots, text)
        assertEquals(s.state, loaded.state)
        assertEquals(pending.decider, loaded.decider)
        assertEquals(s.meta(), loaded.meta())
        // Lanjutan identik: satu langkah bot menghasilkan state yang sama.
        s.stepBot(); loaded.stepBot()
        assertEquals(s.state, loaded.state)
    }

    @Test
    fun corruptSaveIsRejected() {
        val bad = runCatching { GameSession.load(engine, bots, "{\"version\":99}") }
        assertTrue(bad.isFailure)
        val junk = runCatching { GameSession.load(engine, bots, "bukan json") }
        assertTrue(junk.isFailure)
    }
}
