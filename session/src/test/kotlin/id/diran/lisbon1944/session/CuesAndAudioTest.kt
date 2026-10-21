package id.diran.lisbon1944.session

import id.diran.lisbon1944.bot.Bots
import id.diran.lisbon1944.engine.GameEngine
import id.diran.lisbon1944.engine.content.ContentLoader
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerSetup
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.session.audio.Synth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CuesAndAudioTest {
    private val content = ContentLoader.loadDefault()
    private val engine = GameEngine(content)
    private val bots = Bots(content)

    @Test
    fun cuesComeOnlyFromVisibleEventsAndCoverAFullGame() {
        val config = GameConfig(
            players = (0 until 4).map { PlayerSetup("B$it", Faction.entries[it], isBot = true, botLevel = BotLevel.NORMAL) },
            balance = content.balance,
        )
        val s = GameSession.newGame(engine, bots, config, seed = 31)
        val seen = mutableSetOf<String>()
        var guard = 0
        while (!s.isOver && guard++ < 8000) {
            val r = s.stepBot() as? StepResult.Ok ?: continue
            for (viewer in 0 until 4) {
                val cues = Cue.from(r.events, PlayerId(viewer))
                cues.forEach { seen += it::class.simpleName!! }
                // Kartu Bukti privat (CARD_SEEN) tidak pernah menjadi isyarat bagi pemain lain.
                val privateForOthers = r.events.filter { !Cue.visible(it, PlayerId(viewer)) }
                assertTrue(Cue.from(privateForOthers, PlayerId(viewer)).isEmpty())
            }
        }
        assertTrue(s.isOver)
        for (k in listOf("Dice", "Money", "Front", "CardDrawn")) assertTrue(k in seen, "isyarat $k tidak pernah muncul: $seen")
        val stats = GameStats.from(engine.viewFor(s.state, PlayerId(0)).log)
        assertTrue(stats.biggestRent > 0)
        assertTrue(stats.frontsRevealed >= 1)
    }

    @Test
    fun rentTableMatchesFormula() {
        val t = content.board.filterIsInstance<PropertyTile>().first()
        val table = RentTable.property(t, content.balance)
        assertEquals(7, table.size)
        assertEquals(t.baseRent, table[0])
        assertTrue(table.zipWithNext().drop(1).all { (a, b) -> b >= a })
    }

    @Test
    fun synthProducesValidWavForEveryEffect() {
        for (k in Sfx.entries) {
            val pcm = Synth.sfx(k)
            assertTrue(pcm.size in 1000..(Synth.RATE * 3), "$k panjang ${pcm.size}")
            assertTrue(pcm.any { it.toInt() != 0 }, "$k sunyi")
            val wav = Synth.wav(pcm)
            assertEquals(44 + pcm.size * 2, wav.size)
            assertEquals("RIFF", String(wav, 0, 4))
            assertEquals("WAVE", String(wav, 8, 4))
        }
        val music = Synth.music()
        assertEquals(Synth.RATE * 16, music.size)
        assertTrue(music.maxOf { kotlin.math.abs(it.toInt()) } in 20_000..32_767)
    }
}
