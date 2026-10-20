package id.diran.lisbon1944.engine

import id.diran.lisbon1944.engine.internal.Legality
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.GameMode
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerSetup
import id.diran.lisbon1944.engine.model.Visibility
import id.diran.lisbon1944.engine.rng.SplitMix64Rng
import id.diran.lisbon1944.engine.testing.TestEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Uji sifat: ribuan langkah acak tidak pernah melanggar invariant dan engine tidak pernah macet. */
class PropertyTest {

    private val engine = TestEngine.engine
    private val content = TestEngine.content

    private fun config(n: Int, mode: GameMode = GameMode.STANDARD, da: Boolean = false) = GameConfig(
        players = Faction.entries.take(n).mapIndexed { i, f -> PlayerSetup("Agen $i", f) },
        balance = content.balance,
        mode = mode,
        doubleAgentVariant = da,
    )

    /** Pemain acak yang sedikit condong mengakhiri fase Bisnis agar permainan bergerak maju. */
    private fun pick(state: GameState, chooser: SplitMix64Rng): Action {
        val decider = state.pending!!.decider
        val legal = engine.legalActions(state, decider)
        if (legal.isEmpty()) fail("Tidak ada aksi legal untuk ${state.pending}")
        if (state.pending is PendingDecision.BusinessPhase && chooser.nextInt(100) < 70) return Action.EndTurn(decider)
        if (state.pending is PendingDecision.RaiseFunds) {
            val bankrupt = legal.firstOrNull { it is Action.DeclareBankruptcy }
            if (bankrupt != null && chooser.nextInt(100) < 50) return bankrupt
        }
        return legal[chooser.nextInt(legal.size)]
    }

    private fun runRandom(
        seed: Long,
        n: Int,
        steps: Int,
        mode: GameMode = GameMode.STANDARD,
        da: Boolean = false,
        maxGames: Int = 3,
    ): Pair<List<Action>, GameState> {
        val chooser = SplitMix64Rng.fromSeed(seed * 7919)
        var state = engine.newGame(config(n, mode, da), seed)
        val actions = ArrayList<Action>()
        var games = 0
        while (actions.size < steps) {
            assertEquals(emptyList(), engine.checkInvariants(state), "invariant (seed $seed, langkah ${actions.size})")
            if (state.winner != null) {
                games++
                if (games >= maxGames) break
                state = engine.newGame(config(n, mode, da), seed + games * 1000)
                continue
            }
            val action = pick(state, chooser)
            when (val r = engine.reduce(state, action)) {
                is ReduceResult.Ok -> {
                    state = r.state
                    actions += action
                }
                is ReduceResult.Rejected -> fail("Aksi legal ditolak: $action → ${r.reason}")
            }
        }
        return actions to state
    }

    @Test
    fun `10000 langkah acak tidak melanggar invariant`() {
        var total = 0
        listOf(3 to 1L, 4 to 2L, 5 to 3L, 6 to 4L).forEach { (n, seed) -> total += runRandom(seed, n, 2_500, maxGames = 1_000).first.size }
        assertEquals(10_000, total)
    }

    @Test
    fun `mode cepat keluarga dan agen ganda tetap sehat`() {
        runRandom(21, 4, 1_500, GameMode.QUICK)
        runRandom(22, 4, 1_500, GameMode.FAMILY)
        runRandom(23, 5, 1_500, da = true)
    }

    @Test
    fun `aksi di luar daftar legal ditolak dan state tidak berubah`() {
        val chooser = SplitMix64Rng.fromSeed(5)
        var state = engine.newGame(config(4), 77)
        var checked = 0
        repeat(1_500) {
            if (state.winner != null) return@repeat
            val decider = state.pending!!.decider
            val legal = engine.legalActions(state, decider).toSet()
            // Kandidat semua pemain: bukan legal → wajib ditolak dengan alasan.
            state.players.forEach { pl ->
                val pool = Legality.candidates(state, content, pl.id) +
                    listOf(Action.RollDice(pl.id), Action.EndTurn(pl.id), Action.BuyProperty(pl.id), Action.PassBid(pl.id))
                pool.shuffledWith(chooser).take(12).forEach { a ->
                    if (a !in legal) {
                        val r = engine.reduce(state, a)
                        assertIs<ReduceResult.Rejected>(r, "aksi ilegal diterima: $a (pending ${state.pending})")
                        checked++
                    }
                }
            }
            state = (engine.reduce(state, pick(state, chooser)) as ReduceResult.Ok).state
        }
        assertTrue(checked > 1_000)
    }

    private fun <T> List<T>.shuffledWith(rng: SplitMix64Rng): List<T> = rng.shuffled(this)

    @Test
    fun `seed dan urutan aksi sama menghasilkan state identik`() {
        val (actions, final) = runRandom(31, 4, 800, maxGames = 1)
        var replay = engine.newGame(config(4), 31)
        actions.forEach { replay = (engine.reduce(replay, it) as ReduceResult.Ok).state }
        assertEquals(final, replay)
    }

    @Test
    fun `save dan load di tengah permainan lalu lanjut identik`() {
        val (actions, _) = runRandom(41, 5, 600, maxGames = 1)
        var a = engine.newGame(config(5), 41)
        var b = a
        actions.forEachIndexed { i, act ->
            a = (engine.reduce(a, act) as ReduceResult.Ok).state
            b = (engine.reduce(b, act) as ReduceResult.Ok).state
            if (i % 50 == 25) {
                val json = GameJson.encode(b)
                b = GameJson.decode(json)
                assertEquals(a, b, "state setelah save/load di langkah $i")
            }
        }
        assertEquals(a, b)
    }

    @Test
    fun `PlayerView tidak membocorkan informasi tersembunyi`() {
        val chooser = SplitMix64Rng.fromSeed(9)
        var state = engine.newGame(config(4), 55)
        repeat(1_200) {
            if (state.winner != null) return@repeat
            state.players.forEach { me ->
                val view = engine.viewFor(state, me.id)
                assertNull(view.revealedEnvelope.takeIf { !me.burned }, "amplop bocor ke ${me.id}")
                if (state.pending?.decider != me.id) assertNull(view.myDecision)
                view.log.forEach { e -> assertTrue(e.visibility.visibleTo(me.id), "event privat bocor: $e") }
                view.knowledge.forEach { k -> assertTrue(k in state.knowledge[me.id.value]) }
                view.opponents.forEach { o ->
                    if (!o.bankrupt) assertNull(o.publicHand)
                    assertEquals(state.player(o.id).hand.size, o.handCount)
                }
                val json = GameJson.encodeView(view)
                if (!me.burned) assertTrue("\"envelope\"" !in json, "kata kunci envelope muncul di PlayerView")
                // Kartu Bukti lawan tidak muncul kecuali ada di pengetahuan/log yang sah bagi viewer.
                val allowed = view.knowledge.map { it.cardId.value }.toSet() +
                    view.log.flatMap { it.payload.values }.flatMap { it.split(",") }.toSet() +
                    me.hand.map { it.value } +
                    // Kartu Lokasi distrik tempat berdiri & kartu yang disebut dalam Dugaan bersifat publik.
                    listOfNotNull((view.myDecision as? PendingDecision.SuggestionOffer)?.fixedLocation?.value) +
                    ((view.myDecision as? PendingDecision.RevealForSuggestion)?.named?.map { it.value } ?: emptyList())
                state.players.filter { it.id != me.id }.flatMap { it.hand }.forEach { card ->
                    if (card.value !in allowed) assertTrue("\"${card.value}\"" !in json, "kartu ${card.value} bocor ke ${me.id}")
                }
            }
            state = (engine.reduce(state, pick(state, chooser)) as ReduceResult.Ok).state
        }
    }

    @Test
    fun `event privat hanya untuk pihak yang terlibat`() {
        val (_, state) = runRandom(61, 4, 1_000)
        state.eventLog.filter { it.type == GameEvents.CARD_SEEN }.forEach { e ->
            val vis = assertIs<Visibility.Private>(e.visibility)
            assertTrue(PlayerId(e.payload.getValue("viewer").toInt()) in vis.playerIds)
            assertTrue(vis.playerIds.size <= 2)
        }
    }
}
