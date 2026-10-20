package id.diran.lisbon1944.session

import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.PlayerId

/**
 * Pengelompokan daftar aksi legal menjadi menu yang ramah UI. UI tidak pernah menyusun aksi sendiri
 * kecuali Tawaran Dagang (diperiksa lewat GameSession.check); semua pilihan berasal dari legalActions.
 */
object Choices {

    /** Menu tiga kategori (Dugaan, Tuduhan, Tebakan Akhir). [suspectSets] berisi set 1 atau 2 kartu (NKVD). */
    class TripleMenu<A : Action>(
        val suspectSets: List<List<CardId>>,
        val items: List<CardId>,
        val locations: List<CardId>,
        private val index: Map<Triple<List<CardId>, CardId, CardId>, A>,
    ) {
        val suspects: List<CardId> get() = suspectSets.filter { it.size == 1 }.map { it.first() }
        val allowsPair: Boolean get() = suspectSets.any { it.size == 2 }

        fun find(suspects: List<CardId>, item: CardId?, location: CardId?): A? {
            if (item == null || location == null || suspects.isEmpty()) return null
            return index[Triple(suspects.sortedBy { it.value }, item, location)]
        }
    }

    fun suggestionMenu(legal: List<Action>): TripleMenu<Action.Suggest>? {
        val all = legal.filterIsInstance<Action.Suggest>()
        if (all.isEmpty()) return null
        return TripleMenu(
            suspectSets = all.map { it.suspects.sortedBy { c -> c.value } }.distinct(),
            items = all.map { it.item }.distinct(),
            locations = all.map { it.location }.distinct(),
            index = all.associateBy { Triple(it.suspects.sortedBy { c -> c.value }, it.item, it.location) },
        )
    }

    fun accuseMenu(legal: List<Action>): TripleMenu<Action.Accuse>? {
        val all = legal.filterIsInstance<Action.Accuse>()
        if (all.isEmpty()) return null
        return TripleMenu(
            suspectSets = all.map { listOf(it.suspect) }.distinct(),
            items = all.map { it.item }.distinct(),
            locations = all.map { it.location }.distinct(),
            index = all.associateBy { Triple(listOf(it.suspect), it.item, it.location) },
        )
    }

    fun finalGuessMenu(legal: List<Action>): TripleMenu<Action.SubmitFinalGuess>? {
        val all = legal.filterIsInstance<Action.SubmitFinalGuess>()
        if (all.isEmpty()) return null
        return TripleMenu(
            suspectSets = all.map { listOf(it.suspect) }.distinct(),
            items = all.map { it.item }.distinct(),
            locations = all.map { it.location }.distinct(),
            index = all.associateBy { Triple(listOf(it.suspect), it.item, it.location) },
        )
    }

    fun bidAmounts(legal: List<Action>): List<Int> = legal.filterIsInstance<Action.Bid>().map { it.amount }.sorted()

    fun casinoAmounts(legal: List<Action>): List<Int> = legal.filterIsInstance<Action.CasinoBet>().map { it.amount }.sorted()

    /** Operasi bisnis yang sah per petak. */
    data class TileOps(
        val tile: Int,
        val build: Action.Build? = null,
        val sell: Action.SellBuilding? = null,
        val mortgage: Action.Mortgage? = null,
        val unmortgage: Action.Unmortgage? = null,
    ) {
        val any: Boolean get() = build != null || sell != null || mortgage != null || unmortgage != null
    }

    fun tileOps(legal: List<Action>): Map<Int, TileOps> {
        val out = sortedMapOf<Int, TileOps>()
        fun upd(tile: Int, f: (TileOps) -> TileOps) { out[tile] = f(out[tile] ?: TileOps(tile)) }
        for (a in legal) when (a) {
            is Action.Build -> upd(a.tile) { it.copy(build = a) }
            is Action.SellBuilding -> upd(a.tile) { it.copy(sell = a) }
            is Action.Mortgage -> upd(a.tile) { it.copy(mortgage = a) }
            is Action.Unmortgage -> upd(a.tile) { it.copy(unmortgage = a) }
            else -> Unit
        }
        return out
    }

    /** Kartu Taktik yang bisa dimainkan sekarang → semua varian target. */
    fun tactics(legal: List<Action>): Map<Int, List<Action.PlayTactic>> =
        legal.filterIsInstance<Action.PlayTactic>().groupBy { it.cardId }

    fun mi6Targets(legal: List<Action>): List<PlayerId> = legal.filterIsInstance<Action.UseMi6Ability>().map { it.targetPlayer }

    inline fun <reified T : Action> first(legal: List<Action>): T? = legal.firstOrNull { it is T } as T?

    inline fun <reified T : Action> all(legal: List<Action>): List<T> = legal.filterIsInstance<T>()
}
