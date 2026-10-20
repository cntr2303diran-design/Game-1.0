package id.diran.lisbon1944.session

import id.diran.lisbon1944.bot.Bots
import id.diran.lisbon1944.engine.GameEngine
import id.diran.lisbon1944.engine.GameJson
import id.diran.lisbon1944.engine.ReduceResult
import id.diran.lisbon1944.engine.RuleViolation
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.GameEvent
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerView
import id.diran.lisbon1944.engine.rng.SplitMix64Rng
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed interface StepResult {
    data class Ok(val events: List<GameEvent>) : StepResult
    data class Rejected(val reason: RuleViolation) : StepResult
    data object NotYourTurn : StepResult
}

/**
 * Sesi permainan di satu perangkat (pass-and-play + bot). Murni Kotlin agar logika app bisa diuji di JVM.
 *
 * Aturan tampilan:
 * - UI selalu merender [view] = viewFor(state, [viewer]); tidak pernah GameState langsung.
 * - Bila keputusan berpindah ke manusia lain (dan ada > 1 manusia), [needsPrivacy] = true: UI menampilkan
 *   layar privasi sampai pemain itu menekan "Saya siap" ([reveal]).
 * - Saat bot berpikir, layar tetap milik manusia terakhir yang terungkap.
 */
class GameSession private constructor(
    val engine: GameEngine,
    private val bots: Bots,
    state: GameState,
    revealedTo: PlayerId?,
) {
    var state: GameState = state
        private set

    /** Manusia yang layarnya sedang boleh ditampilkan. */
    var revealedTo: PlayerId? = revealedTo
        private set

    val humans: List<PlayerId> get() = state.players.filter { !it.isBot }.map { it.id }

    val decider: PlayerId? get() = state.pending?.decider

    val isOver: Boolean get() = state.winner != null

    val isBotTurn: Boolean get() = !isOver && decider?.let { state.player(it).isBot } == true

    val isHumanTurn: Boolean get() = !isOver && decider?.let { !state.player(it).isBot } == true

    /** Manusia yang sedang ditunggu tetapi belum menerima perangkat. */
    val needsPrivacy: Boolean
        get() = isHumanTurn && humans.size > 1 && decider != revealedTo

    /** Siapa yang layarnya dirender. */
    val viewer: PlayerId
        get() = when {
            isHumanTurn && !needsPrivacy -> decider!!
            revealedTo != null -> revealedTo!!
            // Belum ada yang membuka layar: jangan pernah memakai sudut pandang pemain yang sedang ditunggu.
            else -> humans.firstOrNull { it != decider } ?: humans.firstOrNull() ?: PlayerId(0)
        }

    init {
        if (humans.size == 1) this.revealedTo = humans.first()
    }

    fun reveal() {
        if (isHumanTurn) revealedTo = decider
    }

    fun view(): PlayerView = engine.viewFor(state, viewer)

    /** Aksi legal bagi viewer — kosong bila bukan gilirannya memutuskan atau layar privasi belum dibuka. */
    fun legal(): List<Action> =
        if (isHumanTurn && !needsPrivacy) engine.legalActions(state, decider!!) else emptyList()

    fun check(action: Action): RuleViolation? = engine.checkAction(state, action)

    fun act(action: Action): StepResult {
        if (!isHumanTurn || needsPrivacy || action.actorId != decider) return StepResult.NotYourTurn
        return apply(action)
    }

    /** Satu langkah bot (dipanggil UI dengan jeda sesuai kecepatan animasi). */
    fun stepBot(): StepResult {
        if (!isBotTurn) return StepResult.NotYourTurn
        val d = decider!!
        val pl = state.player(d)
        val bot = bots.create(pl.botLevel ?: BotLevel.NORMAL)
        val rng = SplitMix64Rng.fromSeed(state.seed * 31 + state.actionCount * 7919L + d.value)
        val legal = engine.legalActions(state, d)
        val action = bot.decide(engine.viewFor(state, d), legal, rng)
        val result = apply(action)
        if (result is StepResult.Rejected) return apply(legal.first())
        return result
    }

    private fun apply(action: Action): StepResult = when (val r = engine.reduce(state, action)) {
        is ReduceResult.Ok -> {
            state = r.state
            StepResult.Ok(r.events)
        }
        is ReduceResult.Rejected -> StepResult.Rejected(r.reason)
    }

    /** Serialisasi untuk simpan otomatis. */
    fun save(): String = buildJsonObject {
        put("version", SAVE_VERSION)
        revealedTo?.let { put("revealedTo", it.value) }
        put("state", Json.parseToJsonElement(GameJson.encode(state)))
    }.toString()

    fun meta(): SaveMeta = SaveMeta(
        players = state.players.map { it.name },
        warStep = state.warStep,
        round = state.roundNumber,
        actions = state.actionCount,
        finished = isOver,
    )

    companion object {
        const val SAVE_VERSION = 1

        fun newGame(engine: GameEngine, bots: Bots, config: GameConfig, seed: Long): GameSession =
            GameSession(engine, bots, engine.newGame(config, seed), null)

        fun of(engine: GameEngine, bots: Bots, state: GameState, revealedTo: PlayerId? = null): GameSession =
            GameSession(engine, bots, state, revealedTo)

        /** Muat dari teks simpanan; melempar IllegalArgumentException bila rusak. */
        fun load(engine: GameEngine, bots: Bots, text: String): GameSession {
            val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrElse { throw IllegalArgumentException("Simpanan rusak", it) }
            val version = root["version"]?.jsonPrimitive?.intOrNull ?: 0
            require(version == SAVE_VERSION) { "Versi simpanan $version tidak didukung" }
            val stateJson = root["state"] as? JsonObject ?: throw IllegalArgumentException("Simpanan tanpa state")
            val state = GameJson.decode(stateJson.toString())
            val revealed = (root["revealedTo"] as? JsonPrimitive)?.intOrNull?.let(::PlayerId)
            val problems = engine.checkInvariants(state)
            require(problems.isEmpty()) { "Simpanan tidak konsisten: ${problems.first()}" }
            return GameSession(engine, bots, state, revealed)
        }
    }
}

/** Ringkasan untuk daftar slot simpan. */
data class SaveMeta(
    val players: List<String>,
    val warStep: Int,
    val round: Int,
    val actions: Int,
    val finished: Boolean,
)
