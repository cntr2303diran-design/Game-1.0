package id.diran.lisbon1944.engine

import id.diran.lisbon1944.engine.content.ContentLoader
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.internal.Economy
import id.diran.lisbon1944.engine.internal.Legality
import id.diran.lisbon1944.engine.internal.Scoring
import id.diran.lisbon1944.engine.internal.Setup
import id.diran.lisbon1944.engine.internal.Tx
import id.diran.lisbon1944.engine.internal.Views
import id.diran.lisbon1944.engine.internal.advance
import id.diran.lisbon1944.engine.internal.handle
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.District
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerView
import id.diran.lisbon1944.engine.model.RentQuote
import id.diran.lisbon1944.engine.model.ScoreBreakdown
import id.diran.lisbon1944.engine.rng.GameRng
import id.diran.lisbon1944.engine.rng.SplitMix64Rng
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * API publik mesin aturan. Semua fungsi murni: hasil hanya bergantung pada argumen (state, aksi, RNG).
 *
 * Pemakaian umum:
 * ```
 * val engine = GameEngine()
 * var state = engine.newGame(config, seed)
 * when (val r = engine.reduce(state, action)) { is Ok -> state = r.state; is Rejected -> show(r.reason) }
 * ```
 * Tanpa argumen rng, reduce melanjutkan RNG dari state.rngState sehingga replay & save/load deterministik.
 */
class GameEngine(val content: GameContent = ContentLoader.loadDefault()) {

    fun newGame(config: GameConfig, seed: Long): GameState = Setup.newGame(content, config, seed)

    fun reduce(state: GameState, action: Action, rng: GameRng = SplitMix64Rng.fromState(state.rngState)): ReduceResult {
        val a = Legality.normalize(action)
        Legality.check(state, content, a)?.let { return ReduceResult.Rejected(it) }
        val pending = state.pending ?: return ReduceResult.Rejected(RuleViolation(ViolationCode.NO_DECISION, "Tidak ada keputusan"))
        val tx = Tx(state, rng, content)
        tx.handle(pending, a)
        tx.advance()
        return ReduceResult.Ok(tx.commit().copy(actionCount = state.actionCount + 1), tx.events)
    }

    /**
     * Semua aksi legal untuk [playerId] saat ini (kosong bila bukan gilirannya memutuskan).
     * ProposeTrade tidak dienumerasi karena parametrik; validasinya lewat [checkAction].
     */
    fun legalActions(state: GameState, playerId: PlayerId): List<Action> = Legality.legal(state, content, playerId)

    /** null bila aksi legal; selain itu alasan penolakannya. */
    fun checkAction(state: GameState, action: Action): RuleViolation? = Legality.check(state, content, Legality.normalize(action))

    fun viewFor(state: GameState, playerId: PlayerId): PlayerView = Views.viewFor(state, content, playerId)

    /** Sewa untuk petak [tileIndex] bila [payer] berhenti di sana (null bila tak bertuan). */
    fun computeRent(
        state: GameState,
        tileIndex: Int,
        payer: PlayerId = PlayerId(state.currentPlayerIdx),
        cardMultiplier: Int = 1,
    ): RentQuote? = Economy.quote(state, content, tileIndex, payer, cardMultiplier)

    fun computeScores(state: GameState): List<ScoreBreakdown> = Scoring.compute(state, content)

    fun buildCost(state: GameState, district: District): Int = Economy.buildCost(state, content, district)

    fun purchasePrice(state: GameState, buyer: PlayerId, tileIndex: Int): Int = Economy.purchasePrice(state, content, buyer, tileIndex)

    fun unmortgageCost(state: GameState, tileIndex: Int): Int = Economy.unmortgageCost(state, content, tileIndex)

    fun checkInvariants(state: GameState): List<String> = Invariants.check(state, content)

    /** Jalankan tugas yang tertunda tanpa aksi pemain (dipakai fixture test dan pemulihan). */
    internal fun runTasks(state: GameState, rng: GameRng): GameState {
        val tx = Tx(state, rng, content)
        tx.advance()
        return tx.commit()
    }
}

/** Format JSON resmi untuk save game, replay, dan pengiriman ke server. */
object GameJson {
    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        allowStructuredMapKeys = true
    }

    fun encode(state: GameState): String = json.encodeToString<GameState>(state)

    fun decode(text: String): GameState = json.decodeFromString<GameState>(text)

    fun encodeView(view: PlayerView): String = json.encodeToString<PlayerView>(view)
}
