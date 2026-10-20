package id.diran.lisbon1944.bot

import id.diran.lisbon1944.engine.GameEngine
import id.diran.lisbon1944.engine.ReduceResult
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.rng.SplitMix64Rng

/** Hasil satu permainan antar-bot. [aborted] = melewati batas aksi (anomali). */
data class MatchResult(val state: GameState, val actions: Int, val aborted: Boolean, val illegal: List<String>)

/**
 * Menjalankan satu permainan penuh dengan bot di semua kursi (tingkat dari PlayerSetup.botLevel, default Normal).
 * Bot hanya pernah menerima viewFor(state, kursinya). Dipakai oleh test, :sim, dan mode "tonton bot" di :app.
 */
class BotMatch(private val engine: GameEngine, private val bots: Bots = Bots(engine.content)) {

    fun play(
        config: GameConfig,
        seed: Long,
        maxActions: Int = DEFAULT_MAX_ACTIONS,
        onStep: ((GameState, Action) -> Unit)? = null,
    ): MatchResult {
        val seats = config.players.map { bots.create(it.botLevel ?: BotLevel.NORMAL) }
        val botRng = SplitMix64Rng.fromSeed(seed xor BOT_SALT)
        var state = engine.newGame(config, seed)
        var count = 0
        val illegal = ArrayList<String>()
        while (state.winner == null && count < maxActions) {
            val decider: PlayerId = state.pending?.decider ?: break
            val legal = engine.legalActions(state, decider)
            val action = seats[decider.value].decide(engine.viewFor(state, decider), legal, botRng)
            when (val r = engine.reduce(state, action)) {
                is ReduceResult.Ok -> {
                    onStep?.invoke(state, action)
                    state = r.state
                }
                is ReduceResult.Rejected -> {
                    illegal += "$action → ${r.reason}"
                    // Jangan macet: pakai aksi legal pertama.
                    state = (engine.reduce(state, legal.first()) as ReduceResult.Ok).state
                }
            }
            count++
        }
        return MatchResult(state, count, state.winner == null, illegal)
    }

    companion object {
        /** Permainan 4 jam ≈ 300+ giliran; di atas batas ini dianggap anomali. */
        const val DEFAULT_MAX_ACTIONS = 8_000
        private const val BOT_SALT = 0x5EED_B07L
    }
}
