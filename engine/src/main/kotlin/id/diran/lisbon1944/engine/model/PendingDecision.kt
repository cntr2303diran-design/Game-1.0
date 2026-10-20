package id.diran.lisbon1944.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RentQuote(
    val tile: Int,
    val ownerId: PlayerId,
    val amount: Int,
    val infoAllowed: Boolean,
    /** true = Sel Mata-Mata: wajib menunjukkan kartu Lokasi bila punya. */
    val forcedLocation: Boolean,
    val infoBlockedReason: InfoBlockedReason? = null,
)

@Serializable
enum class InfoBlockedReason { RENT_TOO_HIGH, QUOTA_EXHAUSTED, EMPTY_HAND, NOT_APPLICABLE }

@Serializable
sealed interface Creditor {
    @Serializable @SerialName("bank") data object Bank : Creditor
    @Serializable @SerialName("vault") data object Vault : Creditor
    @Serializable @SerialName("player") data class Player(val id: PlayerId) : Creditor
}

@Serializable
enum class ShowReason { SECRET_CONFERENCE, COURIER, OPEN_TELEGRAM, INTELLIGENCE_REPORT, CITY_MAP }

/**
 * Siapa yang harus memutuskan sekarang. Hanya satu PendingDecision aktif kecuali game berakhir.
 * Data di dalamnya (mis. allowedCards) bersifat privat untuk [decider] — PlayerView hanya
 * menyertakannya utuh bagi decider itu sendiri.
 */
@Serializable
sealed interface PendingDecision {
    val decider: PlayerId

    /** Fase Gerak: lempar dadu atau pindah lewat Jalur Pelarian. */
    @Serializable @SerialName("awaitRoll") data class AwaitRoll(override val decider: PlayerId) : PendingDecision
    @Serializable @SerialName("jailChoice") data class JailChoice(override val decider: PlayerId, val turnsServed: Int) : PendingDecision
    @Serializable @SerialName("buyOrAuction") data class BuyOrAuction(override val decider: PlayerId, val tile: Int, val price: Int) : PendingDecision
    @Serializable @SerialName("auctionBid") data class AuctionBid(override val decider: PlayerId, val tile: Int, val minBid: Int) : PendingDecision
    @Serializable @SerialName("rentChoice") data class RentChoice(
        override val decider: PlayerId,
        val quote: RentQuote,
        val allowedInfoCards: List<CardId>,
    ) : PendingDecision
    /** Fase Penyelidikan atau hak Dugaan dari petak/kartu. [freeLocation] = Lokasi bebas. */
    @Serializable @SerialName("suggestionOffer") data class SuggestionOffer(
        override val decider: PlayerId,
        val freeLocation: Boolean,
        val fixedLocation: CardId? = null,
    ) : PendingDecision
    @Serializable @SerialName("revealForSuggestion") data class RevealForSuggestion(
        override val decider: PlayerId,
        val asker: PlayerId,
        val allowedCards: List<CardId>,
    ) : PendingDecision
    @Serializable @SerialName("decodeOperatorPick") data class DecodeOperatorPick(override val decider: PlayerId, val asker: PlayerId) : PendingDecision
    @Serializable @SerialName("decodeContinue") data class DecodeContinue(override val decider: PlayerId, val extraRollsUsed: Int, val paid: Int) : PendingDecision
    @Serializable @SerialName("casinoChoice") data class CasinoChoice(override val decider: PlayerId, val minBet: Int, val maxBet: Int, val step: Int) : PendingDecision
    @Serializable @SerialName("blackMarketChoice") data class BlackMarketChoice(override val decider: PlayerId, val candidates: List<PlayerId>) : PendingDecision
    @Serializable @SerialName("blackMarketPickCard") data class BlackMarketPickCard(override val decider: PlayerId, val buyer: PlayerId, val allowed: List<CardId>) : PendingDecision
    @Serializable @SerialName("accuseOffer") data class AccuseOffer(override val decider: PlayerId, val cost: Int) : PendingDecision
    @Serializable @SerialName("tradeResponse") data class TradeResponse(override val decider: PlayerId, val offer: TradeOffer) : PendingDecision
    @Serializable @SerialName("telegramKeepChoice") data class TelegramKeepChoice(override val decider: PlayerId, val options: List<Int>) : PendingDecision
    @Serializable @SerialName("showCardTo") data class ShowCardTo(
        override val decider: PlayerId,
        val target: PlayerId,
        val allowedCards: List<CardId>,
        val reason: ShowReason,
    ) : PendingDecision
    @Serializable @SerialName("raiseFunds") data class RaiseFunds(override val decider: PlayerId, val amountOwed: Int, val creditor: Creditor) : PendingDecision
    @Serializable @SerialName("finalGuess") data class FinalGuess(override val decider: PlayerId) : PendingDecision
    @Serializable @SerialName("businessPhase") data class BusinessPhase(override val decider: PlayerId) : PendingDecision
}
