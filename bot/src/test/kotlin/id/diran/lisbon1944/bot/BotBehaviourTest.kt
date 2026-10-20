package id.diran.lisbon1944.bot

import id.diran.lisbon1944.bot.BotTestSupport.c
import id.diran.lisbon1944.bot.BotTestSupport.p
import id.diran.lisbon1944.engine.GameEvents
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.FrontId
import id.diran.lisbon1944.engine.model.GameEvent
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerSetup
import id.diran.lisbon1944.engine.model.RentQuote
import id.diran.lisbon1944.engine.model.Visibility
import id.diran.lisbon1944.engine.rng.SplitMix64Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BotBehaviourTest {

    private val engine = BotTestSupport.engine
    private val bots = BotTestSupport.bots

    private fun decide(level: BotLevel, s: GameState, seed: Long = 1): Action {
        val d = s.pending!!.decider
        return bots.create(level).decide(engine.viewFor(s, d), engine.legalActions(s, d), SplitMix64Rng.fromSeed(seed))
    }

    private fun base(): GameState = BotTestSupport.deal(
        4, listOf("S6", "L6", "I6"),
        listOf("S1", "L1", "I1"), listOf("S3", "L3", "I3"), listOf("S4", "L4", "I4"), listOf("S5", "L5", "I5"),
        factions = listOf(Faction.MI6, Faction.OSS, Faction.NKVD, Faction.RESISTANCE),
    ).copy(currentPlayerIdx = 0, warStep = 3, activeFront = FrontId(3))

    /** P0 mengetahui letak semua kartu kecuali isi amplop. */
    private fun omniscient(s: GameState): GameState {
        var t = s
        listOf("S3", "L3", "I3").forEach { t = BotTestSupport.learn(t, 0, it, Holder.Player(p(1))) }
        listOf("S4", "L4", "I4").forEach { t = BotTestSupport.learn(t, 0, it, Holder.Player(p(2))) }
        listOf("S5", "L5", "I5").forEach { t = BotTestSupport.learn(t, 0, it, Holder.Player(p(3))) }
        return t
    }

    @Test
    fun `konfigurasi bot termuat dan tingkat berbeda`() {
        val cfg = BotConfig.loadDefault()
        assertEquals(DeductionMode.FULL, cfg.hard.deduction)
        assertEquals(DeductionMode.BASIC, cfg.easy.deduction)
        assertEquals(0.95, cfg.easy.accuseThreshold)
        assertEquals(0.6, cfg.hard.lateAccuseThreshold)
        assertEquals(0.4, cfg.hard.desperateAccuseThreshold)
        assertEquals(200, cfg.easy.buyMargin)
    }

    @Test
    fun `semua tingkat menuduh bila misteri terpecahkan`() {
        val s = omniscient(base()).copy(pending = PendingDecision.AccuseOffer(p(0), 0))
        BotLevel.entries.forEach { lvl ->
            assertEquals(Action.Accuse(p(0), c("S6"), c("L6"), c("I6")), decide(lvl, s), "tingkat $lvl")
        }
    }

    @Test
    fun `Easy tidak pernah menuduh di bawah 0,95`() {
        // Dua tersangka tersisa: peluang terbaik 0,5.
        var s = omniscient(base())
        s = s.copy(knowledge = s.knowledge.toMutableList().also { k -> k[0] = k[0].filterNot { it.cardId == c("S5") }.toSet() })
        s = s.copy(pending = PendingDecision.AccuseOffer(p(0), 0))
        repeat(30) { seed -> assertEquals(Action.DeclineAccuse(p(0)), decide(BotLevel.EASY, s, seed.toLong())) }
    }

    @Test
    fun `Hard menurunkan ambang saat kalah jauh di akhir perang`() {
        var s = omniscient(base())
        s = s.copy(knowledge = s.knowledge.toMutableList().also { k -> k[0] = k[0].filterNot { it.cardId == c("S5") }.toSet() })
        val early = s.copy(pending = PendingDecision.AccuseOffer(p(0), 0))
        assertEquals(Action.DeclineAccuse(p(0)), decide(BotLevel.HARD, early))
        val late = s.copy(
            warStep = 11, activeFront = FrontId(11), pending = PendingDecision.AccuseOffer(p(0), 0),
            players = s.players.mapIndexed { i, pl -> if (i == 1) pl.copy(money = 5000) else pl.copy(money = 300) },
        )
        assertIs<Action.Accuse>(decide(BotLevel.HARD, late))
        assertEquals(Action.DeclineAccuse(p(0)), decide(BotLevel.NORMAL, late), "Normal tetap butuh 0,9")
    }

    @Test
    fun `Easy membeli hanya bila uang lebih dari harga ditambah 200`() {
        val s = base().copy(pending = PendingDecision.BuyOrAuction(p(0), 37, 350))
        val rich = s.copy(players = s.players.map { if (it.id == p(0)) it.copy(money = 551) else it })
        val poor = s.copy(players = s.players.map { if (it.id == p(0)) it.copy(money = 550) else it })
        val cfg = BotConfig.loadDefault()
        val noRandom = StrategicBot(BotLevel.EASY, cfg.easy.copy(randomness = 0.0), BotTestSupport.content)
        fun pick(st: GameState) = noRandom.decide(engine.viewFor(st, p(0)), engine.legalActions(st, p(0)), SplitMix64Rng.fromSeed(1))
        assertEquals(Action.BuyProperty(p(0)), pick(rich))
        assertEquals(Action.DeclineBuy(p(0)), pick(poor))
    }

    @Test
    fun `Normal memprioritaskan Pelabuhan di awal`() {
        val s = base().copy(warStep = 2, activeFront = FrontId(2), pending = PendingDecision.BuyOrAuction(p(0), 1, 60))
        assertEquals(Action.BuyProperty(p(0)), decide(BotLevel.NORMAL, s))
    }

    @Test
    fun `menunjukkan kartu yang paling banyak sudah diketahui`() {
        val seen = GameEvent(0, GameEvents.CARD_SEEN, mapOf("viewer" to "1", "holder" to "0", "card" to "L1", "source" to "SUGGESTION"), Visibility.Private(setOf(p(0), p(1))))
        val s = base().copy(
            eventLog = listOf(seen),
            pending = PendingDecision.RevealForSuggestion(p(0), p(2), listOf(c("S1"), c("L1")), listOf(c("S1"), c("L1"), c("I5"))),
        )
        assertEquals(Action.RevealCard(p(0), c("L1")), decide(BotLevel.HARD, s))
    }

    @Test
    fun `bayar informasi bila kartunya sudah diketahui`() {
        val seen = GameEvent(0, GameEvents.CARD_SEEN, mapOf("viewer" to "2", "holder" to "0", "card" to "S1", "source" to "SUGGESTION"), Visibility.Private(setOf(p(0), p(2))))
        val quote = RentQuote(13, p(1), 10, infoAllowed = true, forcedLocation = false)
        val s = base().copy(eventLog = listOf(seen), pending = PendingDecision.RentChoice(p(0), quote, listOf(c("S1"), c("L1"), c("I1"))))
        assertEquals(Action.PayRentInfo(p(0), c("S1")), decide(BotLevel.HARD, s))
        // Normal butuh diketahui ≥ 2 pemain: sewa murah dibayar uang.
        assertEquals(Action.PayRentMoney(p(0)), decide(BotLevel.NORMAL, s))
    }

    @Test
    fun `Agen Ganda berbohong sekali kepada pemain terdepan`() {
        val s0 = base()
        val s = s0.copy(
            config = s0.config.copy(doubleAgentVariant = true),
            players = s0.players.mapIndexed { i, pl ->
                when (i) {
                    0 -> pl.copy(money = 4000)
                    1 -> pl.copy(isDoubleAgent = true)
                    else -> pl
                }
            },
            pending = PendingDecision.RevealForSuggestion(p(1), p(0), listOf(c("S3")), listOf(c("S3"), c("L6"), c("I6"))),
        )
        val lie = assertIs<Action.RevealCard>(decide(BotLevel.HARD, s))
        assertTrue(lie.cardId != c("S3"), "harus berbohong dengan kartu yang tidak disebut")
        val used = s.copy(players = s.players.mapIndexed { i, pl -> if (i == 1) pl.copy(doubleAgentLieUsed = true) else pl })
        assertEquals(Action.RevealCard(p(1), c("S3")), decide(BotLevel.HARD, used))
        // Penanya bukan pemimpin → tidak berbohong.
        val notLeader = s.copy(players = s.players.mapIndexed { i, pl -> if (i == 0) pl.copy(money = 10) else pl })
        assertEquals(Action.RevealCard(p(1), c("S3")), decide(BotLevel.HARD, notLeader))
    }

    @Test
    fun `Agen Ganda mengarahkan Dugaan menjauh dari jawaban`() {
        val s0 = omniscient(base())
        val s = s0.copy(
            players = s0.players.mapIndexed { i, pl -> if (i == 0) pl.copy(isDoubleAgent = true) else pl },
            pending = PendingDecision.SuggestionOffer(p(0), freeLocation = true),
        )
        val a = assertIs<Action.Suggest>(decide(BotLevel.HARD, s))
        assertTrue(c("S6") !in a.suspects && a.item != c("I6") && a.location != c("L6"), "Dugaan $a menyebut jawaban")
        val honest = assertIs<Action.Suggest>(decide(BotLevel.HARD, s.copy(players = s0.players)))
        assertEquals(listOf(c("S1")), honest.suspects, "kategori terpecahkan → sebut kartu sendiri")
    }

    @Test
    fun `NKVD memakai dua tersangka saat tersangka belum jelas`() {
        val s = base().copy(
            players = base().players.mapIndexed { i, pl -> if (i == 0) pl.copy(faction = Faction.NKVD) else pl },
            pending = PendingDecision.SuggestionOffer(p(0), freeLocation = true),
        )
        assertEquals(2, assertIs<Action.Suggest>(decide(BotLevel.HARD, s)).suspects.size)
    }

    @Test
    fun `bot tidak bisa membedakan dua state dengan view yang sama (view disamarkan)`() {
        // Ambil keadaan nyata di tengah permainan antar-bot.
        val match = BotMatch(engine)
        var mid: GameState? = null
        match.play(BotTestSupport.config(4), 77, maxActions = 60) { st, _ -> mid = st }
        val a = mid!!
        val viewer = a.pending!!.decider
        val known = a.knowledge[viewer.value].map { it.cardId }.toSet() + a.player(viewer).hand + a.embassyCards
        val other = a.players.first { it.id != viewer && !it.bankrupt && it.hand.any { c -> c !in known && c.value[0] == a.envelope.suspect.value[0] } }
        val swap = other.hand.first { it !in known && it.value[0] == a.envelope.suspect.value[0] }
        // B: tukar tersangka amplop dengan kartu lawan yang tidak diketahui viewer.
        val b = a.copy(
            envelope = a.envelope.copy(suspect = swap),
            players = a.players.map { pl -> if (pl.id == other.id) pl.copy(hand = pl.hand.map { if (it == swap) a.envelope.suspect else it }) else pl },
        )
        val va = engine.viewFor(a, viewer)
        val vb = engine.viewFor(b, viewer)
        assertEquals(va, vb, "view harus identik")
        BotLevel.entries.forEach { lvl ->
            val legal = engine.legalActions(a, viewer)
            assertEquals(legal, engine.legalActions(b, viewer))
            val da = bots.create(lvl).decide(va, legal, SplitMix64Rng.fromSeed(5))
            val db = bots.create(lvl).decide(vb, legal, SplitMix64Rng.fromSeed(5))
            assertEquals(da, db, "keputusan $lvl berbeda padahal view sama")
        }
    }

    @Test
    fun `HardBot tidak pernah memilih aksi ilegal dalam 1000 game`() {
        var aborted = 0
        var illegal = 0
        val match = BotMatch(engine)
        for (g in 1..1000) {
            val n = 3 + g % 4
            val cfg = BotTestSupport.config(n, BotLevel.HARD, factions = Faction.entries.shuffled(java.util.Random(g.toLong())).take(n))
            val r = match.play(cfg, g.toLong())
            illegal += r.illegal.size
            if (r.aborted) aborted++
            assertTrue(r.illegal.isEmpty(), "game $g: ${r.illegal.take(3)}")
        }
        assertEquals(0, illegal)
        assertTrue(aborted <= 10, "$aborted game melewati batas aksi")
    }

    @Test
    fun `campuran tingkat menyelesaikan permainan tanpa aksi ilegal`() {
        val match = BotMatch(engine)
        val levels = listOf(BotLevel.EASY, BotLevel.NORMAL, BotLevel.HARD, BotLevel.EASY, BotLevel.NORMAL)
        repeat(100) { g ->
            val cfg = BotTestSupport.config(5).copy(
                players = levels.mapIndexed { i, l -> PlayerSetup("Bot $i", Faction.entries[(i + g) % 6], true, l) },
            )
            val r = match.play(cfg, 1000L + g)
            assertTrue(r.illegal.isEmpty(), "game $g: ${r.illegal.take(3)}")
            assertTrue(!r.aborted)
        }
    }

    @Test
    fun `permainan antar-bot deterministik`() {
        val m = BotMatch(engine)
        val a = m.play(BotTestSupport.config(4, BotLevel.NORMAL), 9)
        val b = m.play(BotTestSupport.config(4, BotLevel.NORMAL), 9)
        assertEquals(a.state, b.state)
        assertEquals(a.actions, b.actions)
    }
}
