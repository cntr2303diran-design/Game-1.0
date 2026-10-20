package id.diran.lisbon1944.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Langkah kelanjutan engine. Engine menjalankan tugas dari depan tumpukan sampai ada PendingDecision
 * atau permainan berakhir. Karena tumpukan ini bagian dari GameState (dan serializable), permainan bisa
 * disimpan di tengah efek bersarang mana pun — mis. di tengah Konferensi Rahasia atau lelang kebangkrutan.
 */
@Serializable
sealed interface Task {
    @Serializable @SerialName("startTurn")
    data class StartTurn(val player: PlayerId) : Task

    /** Maju satu petak per eksekusi agar START dan Kedutaan yang dilewati bisa diproses. */
    @Serializable @SerialName("move")
    data class Move(
        val player: PlayerId,
        val steps: Int,
        val collectSalary: Boolean,
        val rentMultiplier: Int = 1,
        val freeAccuse: Boolean = false,
        val started: Boolean = false,
    ) : Task

    @Serializable @SerialName("passStart")
    data class PassStart(val player: PlayerId, val salary: Boolean) : Task

    @Serializable @SerialName("embassyPass")
    data class EmbassyPass(val player: PlayerId) : Task

    @Serializable @SerialName("land")
    data class Land(val player: PlayerId, val rentMultiplier: Int = 1, val freeAccuse: Boolean = false) : Task

    @Serializable @SerialName("investigate")
    data class Investigate(val player: PlayerId) : Task

    @Serializable @SerialName("business")
    data class Business(val player: PlayerId) : Task

    @Serializable @SerialName("nextTurn")
    data object NextTurn : Task

    @Serializable @SerialName("resolveCard")
    data class ResolveCard(val player: PlayerId, val deck: Deck, val cardId: Int, val effectIndex: Int = 0) : Task

    @Serializable @SerialName("returnCard")
    data class ReturnCard(val deck: Deck, val cardId: Int) : Task

    @Serializable @SerialName("pay")
    data class Pay(val from: PlayerId, val to: Creditor, val amount: Int, val reason: String) : Task

    /** [shower] menunjukkan 1 kartu (bertipe [cardType] bila diisi) kepada [viewer]. */
    @Serializable @SerialName("requestShow")
    data class RequestShow(val shower: PlayerId, val viewer: PlayerId, val cardType: EvidenceType?, val reason: ShowReason) : Task

    @Serializable @SerialName("suggestionAsk")
    data class SuggestionAsk(val asker: PlayerId, val named: List<CardId>, val remaining: List<PlayerId>) : Task

    @Serializable @SerialName("startAuction")
    data class StartAuction(val tile: Int, val order: List<PlayerId>) : Task

    @Serializable @SerialName("collectFinalGuesses")
    data class CollectFinalGuesses(val remaining: List<PlayerId>) : Task

    @Serializable @SerialName("finishWar")
    data object FinishWar : Task
}
