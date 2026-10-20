package id.diran.lisbon1944.engine.testing

import id.diran.lisbon1944.engine.GameEngine
import id.diran.lisbon1944.engine.ReduceResult
import id.diran.lisbon1944.engine.RuleViolation
import id.diran.lisbon1944.engine.content.ContentLoader
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.Balance
import id.diran.lisbon1944.engine.model.WarClockMode
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Envelope
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.FrontId
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.GameEvent
import id.diran.lisbon1944.engine.model.GameMode
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.KnownFact
import id.diran.lisbon1944.engine.model.Ownership
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerSetup
import id.diran.lisbon1944.engine.model.PlayerState
import id.diran.lisbon1944.engine.model.Task
import id.diran.lisbon1944.engine.model.TurnPhase
import id.diran.lisbon1944.engine.rng.GameRng
import kotlin.test.assertEquals
import kotlin.test.fail

object TestEngine {
    val content: GameContent by lazy { ContentLoader.loadDefault() }
    val engine: GameEngine by lazy { GameEngine(content) }
}

/** RNG test: dadu diambil dari antrean; semua pilihan acak lain mengambil indeks 0. */
class ScriptedRng(vararg dice: Int) : GameRng {
    private val queue = ArrayDeque(dice.toList())

    val remaining: Int get() = queue.size

    override fun rollDie(): Int = queue.removeFirstOrNull() ?: fail("ScriptedRng: antrean dadu habis")
    override fun nextLong(): Long = 0L
    override fun nextInt(bound: Int): Int = 0
    override fun state(): Long = 0L
}

/**
 * Penyusun keadaan permainan untuk skenario test. Mulai dari newGame() sungguhan lalu menimpa bagian yang
 * relevan — sehingga keadaan tetap memenuhi invariant (18 kartu, stok bangunan, tumpukan kartu).
 */
class GameStateFixture(
    players: Int = 4,
    factions: List<Faction> = DEFAULT_FACTIONS.take(players),
    mode: GameMode = GameMode.STANDARD,
    seed: Long = 11,
    doubleAgent: Boolean = false,
    quota: Int? = null,
    /** true = aturan dasar BAGIAN 5–6 (nilai test BAGIAN 14); false = balance.json saat ini. */
    classic: Boolean = true,
) {
    val engine: GameEngine = TestEngine.engine
    val content: GameContent = TestEngine.content

    var state: GameState = engine.newGame(
        GameConfig(
            players = factions.mapIndexed { i, f -> PlayerSetup("Agen ${i + 1}", f) },
            balance = if (classic) classicBalance() else content.balance,
            mode = mode,
            doubleAgentVariant = doubleAgent,
            infoPaymentQuotaOverride = quota,
            warClockMode = if (classic) WarClockMode.FIRST_PASS_PER_ROUND else null,
        ),
        seed,
    )

    fun id(i: Int) = PlayerId(i)
    fun p(i: Int): PlayerState = state.players[i]
    val pending: PendingDecision? get() = state.pending

    fun set(f: GameState.() -> GameState): GameStateFixture = apply { state = state.f() }

    fun player(i: Int, f: PlayerState.() -> PlayerState): GameStateFixture = apply {
        state = state.copy(players = state.players.toMutableList().also { it[i] = it[i].f() })
    }

    fun money(i: Int, amount: Int) = player(i) { copy(money = amount) }

    fun at(i: Int, tile: Int) = player(i) { copy(position = tile) }

    /** Atur amplop dan tangan tiap kursi (urut); kartu sisanya terbuka di Kedutaan. Pengetahuan disusun ulang. */
    fun cards(envelope: List<String>, vararg hands: List<String>): GameStateFixture = apply {
        val env = Envelope(CardId(envelope[0]), CardId(envelope[1]), CardId(envelope[2]))
        val handIds = state.players.indices.map { i -> hands.getOrNull(i)?.map(::CardId) ?: emptyList() }
        val used = env.cards + handIds.flatten()
        val embassy = content.evidence.map { it.id }.filter { it !in used }
        state = state.copy(
            envelope = env,
            embassyCards = embassy,
            players = state.players.mapIndexed { i, pl -> pl.copy(hand = handIds[i]) },
            knowledge = state.players.indices.map { i ->
                (handIds[i].map { KnownFact(it, Holder.Player(PlayerId(i)), KnowledgeSource.OWN_HAND) } +
                    embassy.map { KnownFact(it, Holder.Embassy, KnowledgeSource.EMBASSY) }).toSet()
            },
        )
    }

    /** Beri kepemilikan; stok bangunan disesuaikan agar invariant stok tetap terjaga. */
    fun own(tile: Int, owner: Int, level: Int = 0, mortgaged: Boolean = false): GameStateFixture = apply {
        val old = state.ownership[tile]
        var houses = state.houseStock
        var hqs = state.hqStock
        if (old != null) {
            if (old.level == Ownership.HQ_LEVEL) hqs++ else houses += old.level
        }
        if (level == Ownership.HQ_LEVEL) hqs-- else houses -= level
        state = state.copy(
            ownership = state.ownership + (tile to Ownership(PlayerId(owner), level, mortgaged)),
            houseStock = houses,
            hqStock = hqs,
        )
    }

    fun front(step: Int) = set { copy(warStep = step, activeFront = FrontId(step)) }

    /** Giliran [i] di fase Gerak. Jam Perang dianggap sudah maju pada putaran ini kecuali [clockReady]. */
    fun turn(i: Int, clockReady: Boolean = false) = set {
        copy(
            currentPlayerIdx = i, pending = PendingDecision.AwaitRoll(PlayerId(i)), tasks = emptyList(),
            phase = TurnPhase.MOVE, doublesCount = 0, warClockAdvancedThisRound = !clockReady,
        )
    }

    fun business(i: Int) = set {
        copy(
            currentPlayerIdx = i, pending = PendingDecision.BusinessPhase(PlayerId(i)), tasks = emptyList(),
            phase = TurnPhase.BUSINESS, doublesCount = 0,
        )
    }

    /** Taruh pemain [i] di [tile] lalu jalankan aksi petak + fase berikutnya sampai ada keputusan. */
    fun land(i: Int, tile: Int, rng: GameRng = ScriptedRng()): GameStateFixture = apply {
        state = state.copy(
            currentPlayerIdx = i, pending = null, phase = TurnPhase.TILE_ACTION, doublesCount = 0,
            tasks = listOf(Task.Land(PlayerId(i)), Task.Investigate(PlayerId(i))),
            propertySuggestionUsed = false,
            players = state.players.toMutableList().also { it[i] = it[i].copy(position = tile) },
        )
        state = engine.runTasks(state, rng)
    }

    /** Jalankan aksi yang wajib legal; kembalikan event-nya. Invariant dicek setiap kali. */
    fun act(action: Action, rng: GameRng = ScriptedRng(), checkInvariants: Boolean = true): List<GameEvent> =
        when (val r = engine.reduce(state, action, rng)) {
            is ReduceResult.Ok -> {
                state = r.state
                if (checkInvariants) assertEquals(emptyList(), engine.checkInvariants(state), "invariant setelah $action")
                r.events
            }
            is ReduceResult.Rejected -> fail("Aksi $action ditolak: ${r.reason} (pending ${state.pending})")
        }

    /** Aksi yang wajib ditolak; state tidak berubah. */
    fun reject(action: Action, rng: GameRng = ScriptedRng()): RuleViolation =
        when (val r = engine.reduce(state, action, rng)) {
            is ReduceResult.Ok -> fail("Aksi $action seharusnya ditolak")
            is ReduceResult.Rejected -> r.reason
        }

    companion object {
        /** Aturan dasar: sewa penuh, Tuduhan sejak awal tanpa Intel, Ruang Interogasi gratis, lewat Dugaan publik, tanpa misi. */
        fun classicBalance(): Balance {
            val b = TestEngine.content.balance
            return b.copy(
                buildingRentPercent = 100,
                accuseUnlockStep = 1,
                publicPasses = true,
                intel = b.intel.copy(accuseCost = 0, interrogationFree = true),
                score = b.score.copy(missionsEnabled = false),
            )
        }

        /** Faksi tanpa efek pasif pada uang/gerak lebih dulu, agar skenario test tidak terganggu. */
        val DEFAULT_FACTIONS = listOf(
            Faction.MI6, Faction.RESISTANCE, Faction.NKVD, Faction.SWISS_JOURNALIST, Faction.OSS, Faction.PORTUGAL_TRADER,
        )
    }
}

fun cid(id: String) = CardId(id)
