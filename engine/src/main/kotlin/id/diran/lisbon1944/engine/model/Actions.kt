package id.diran.lisbon1944.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Semua aksi pemain. Serializable agar bisa dikirim ke server otoritatif di masa depan. */
@Serializable
sealed interface Action {
    val actorId: PlayerId

    // --- Gerak & penjara -------------------------------------------------------------
    @Serializable @SerialName("rollDice") data class RollDice(override val actorId: PlayerId) : Action
    @Serializable @SerialName("useEscapeRoute") data class UseEscapeRoute(override val actorId: PlayerId, val targetTile: Int) : Action
    @Serializable @SerialName("payJailFine") data class PayJailFine(override val actorId: PlayerId) : Action
    @Serializable @SerialName("useJailCard") data class UseJailCard(override val actorId: PlayerId) : Action
    @Serializable @SerialName("useResistanceAbility") data class UseResistanceAbility(override val actorId: PlayerId) : Action

    // --- Properti & lelang -----------------------------------------------------------
    @Serializable @SerialName("buyProperty") data class BuyProperty(override val actorId: PlayerId) : Action
    @Serializable @SerialName("declineBuy") data class DeclineBuy(override val actorId: PlayerId) : Action
    @Serializable @SerialName("bid") data class Bid(override val actorId: PlayerId, val amount: Int) : Action
    @Serializable @SerialName("passBid") data class PassBid(override val actorId: PlayerId) : Action

    // --- Sewa ------------------------------------------------------------------------
    @Serializable @SerialName("payRentMoney") data class PayRentMoney(override val actorId: PlayerId) : Action
    @Serializable @SerialName("payRentInfo") data class PayRentInfo(override val actorId: PlayerId, val cardId: CardId) : Action

    // --- Misteri ---------------------------------------------------------------------
    /** [suspects] berisi 1 kartu, atau 2 untuk kemampuan NKVD (urutan id naik). */
    @Serializable @SerialName("suggest")
    data class Suggest(
        override val actorId: PlayerId,
        val suspects: List<CardId>,
        val item: CardId,
        val location: CardId,
    ) : Action
    @Serializable @SerialName("skipSuggestion") data class SkipSuggestion(override val actorId: PlayerId) : Action
    @Serializable @SerialName("revealCard") data class RevealCard(override val actorId: PlayerId, val cardId: CardId) : Action
    /** Bungkam: bayar Intel untuk tidak menunjukkan kartu; Dugaan berlanjut ke pemain berikutnya. */
    @Serializable @SerialName("staySilent") data class StaySilent(override val actorId: PlayerId) : Action

    // --- Dekode ----------------------------------------------------------------------
    @Serializable @SerialName("decodePickNumber") data class DecodePickNumber(override val actorId: PlayerId, val n: Int) : Action
    @Serializable @SerialName("decodeRollExtra") data class DecodeRollExtra(override val actorId: PlayerId) : Action
    @Serializable @SerialName("decodeStop") data class DecodeStop(override val actorId: PlayerId) : Action

    // --- Kasino & Pasar Gelap --------------------------------------------------------
    @Serializable @SerialName("casinoBet") data class CasinoBet(override val actorId: PlayerId, val amount: Int) : Action
    @Serializable @SerialName("casinoSkip") data class CasinoSkip(override val actorId: PlayerId) : Action
    @Serializable @SerialName("blackMarketBuy") data class BlackMarketBuy(override val actorId: PlayerId, val targetPlayer: PlayerId) : Action
    @Serializable @SerialName("blackMarketChooseCard") data class BlackMarketChooseCard(override val actorId: PlayerId, val cardId: CardId) : Action
    @Serializable @SerialName("blackMarketSkip") data class BlackMarketSkip(override val actorId: PlayerId) : Action

    // --- Tuduhan ---------------------------------------------------------------------
    /** Biaya (0 / 100 melintas Kedutaan / 300 April 1945) dipotong otomatis sesuai keputusan yang sedang berjalan. */
    @Serializable @SerialName("accuse")
    data class Accuse(override val actorId: PlayerId, val suspect: CardId, val location: CardId, val item: CardId) : Action
    @Serializable @SerialName("declineAccuse") data class DeclineAccuse(override val actorId: PlayerId) : Action

    // --- Bisnis ----------------------------------------------------------------------
    @Serializable @SerialName("build") data class Build(override val actorId: PlayerId, val tile: Int) : Action
    @Serializable @SerialName("sellBuilding") data class SellBuilding(override val actorId: PlayerId, val tile: Int) : Action
    @Serializable @SerialName("mortgage") data class Mortgage(override val actorId: PlayerId, val tile: Int) : Action
    @Serializable @SerialName("unmortgage") data class Unmortgage(override val actorId: PlayerId, val tile: Int) : Action
    @Serializable @SerialName("proposeTrade") data class ProposeTrade(override val actorId: PlayerId, val offer: TradeOffer) : Action
    @Serializable @SerialName("respondTrade") data class RespondTrade(override val actorId: PlayerId, val accept: Boolean) : Action

    // --- Kemampuan & kartu -----------------------------------------------------------
    @Serializable @SerialName("useMi6Ability") data class UseMi6Ability(override val actorId: PlayerId, val targetPlayer: PlayerId) : Action
    @Serializable @SerialName("chooseTelegram") data class ChooseTelegram(override val actorId: PlayerId, val keepCardId: Int) : Action
    /** Konferensi Rahasia, Kurir, Telegram Terbuka, Laporan Intelijen, Peta Kota. */
    @Serializable @SerialName("chooseCardToShow") data class ChooseCardToShow(override val actorId: PlayerId, val cardId: CardId) : Action
    @Serializable @SerialName("chooseWiretapTarget") data class ChooseWiretapTarget(override val actorId: PlayerId, val target: PlayerId) : Action

    /** Mainkan Kartu Taktik dari tangan. [targetPlayer]/[targetTile] diisi sesuai TacticTarget kartu. */
    @Serializable @SerialName("playTactic")
    data class PlayTactic(
        override val actorId: PlayerId,
        val cardId: Int,
        val targetPlayer: PlayerId? = null,
        val targetTile: Int? = null,
    ) : Action

    // --- Akhir -----------------------------------------------------------------------
    @Serializable @SerialName("submitFinalGuess")
    data class SubmitFinalGuess(override val actorId: PlayerId, val suspect: CardId, val location: CardId, val item: CardId) : Action
    @Serializable @SerialName("declareBankruptcy") data class DeclareBankruptcy(override val actorId: PlayerId) : Action
    @Serializable @SerialName("endTurn") data class EndTurn(override val actorId: PlayerId) : Action
}
