package id.diran.lisbon1944.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EvidenceCard(
    val id: CardId,
    val type: EvidenceType,
    val name: String,
    val nameEn: String,
    /** Hanya untuk kartu Lokasi: distrik yang diwakili. */
    val district: District? = null,
    val role: String? = null,
    val roleEn: String? = null,
    /** Nama aset potret/ikon di :app. */
    val art: String? = null,
    val blurb: String,
    val blurbEn: String,
)

@Serializable
data class FactionDef(
    val id: Faction,
    val name: String,
    val nameEn: String,
    val organization: String,
    val accentColor: String,
    val badgeIcon: String,
    val ability: FactionAbility,
    val oneShot: Boolean,
    val abilityText: String,
    val abilityTextEn: String,
)

// ---------------------------------------------------------------------------
// Kartu Front
// ---------------------------------------------------------------------------

@Serializable
data class FrontCard(
    val id: FrontId,
    /** "YYYY-MM" */
    val month: String,
    val title: String,
    val titleEn: String,
    val text: String,
    val textEn: String,
    val effects: List<FrontEffect>,
)

/** Efek Front sebagai data terstruktur. "OnReveal…" dijalankan sekali saat kartu dibalik; sisanya berlaku selama aktif. */
@Serializable
sealed interface FrontEffect {
    @Serializable @SerialName("districtRentMultiplier")
    data class DistrictRentMultiplier(val districts: List<District>, val numerator: Int, val denominator: Int) : FrontEffect

    @Serializable @SerialName("escapeRouteRentMultiplier")
    data class EscapeRouteRentMultiplier(val numerator: Int, val denominator: Int) : FrontEffect

    @Serializable @SerialName("onRevealPayIfOwnsInDistrict")
    data class OnRevealPayIfOwnsInDistrict(val district: District, val amount: Int) : FrontEffect

    @Serializable @SerialName("onRevealFactionBonus")
    data class OnRevealFactionBonus(val faction: Faction, val amount: Int) : FrontEffect

    @Serializable @SerialName("freeSuggestionEachPlayer")
    data class FreeSuggestionEachPlayer(val count: Int) : FrontEffect

    @Serializable @SerialName("closeTile")
    data class CloseTile(val tile: Int) : FrontEffect

    @Serializable @SerialName("onRevealJailIfMoneyAbove")
    data class OnRevealJailIfMoneyAbove(val threshold: Int) : FrontEffect

    @Serializable @SerialName("buildCostMultiplier")
    data class BuildCostMultiplier(val numerator: Int, val denominator: Int) : FrontEffect

    @Serializable @SerialName("noStartSalary")
    data object NoStartSalary : FrontEffect

    @Serializable @SerialName("onRevealSecretConference")
    data object OnRevealSecretConference : FrontEffect

    /** Biaya diambil dari balance.aprilAccuseCost. */
    @Serializable @SerialName("accuseFromAnywhere")
    data object AccuseFromAnywhere : FrontEffect

    @Serializable @SerialName("endGame")
    data object EndGame : FrontEffect

    /** "Map Merah berpindah": kartu kategori ini dari amplop + tangan pemain aktif dikocok dan dibagi ulang. */
    @Serializable @SerialName("reshuffleEvidence")
    data class ReshuffleEvidence(val category: EvidenceType) : FrontEffect
}

// ---------------------------------------------------------------------------
// Kartu Sandi & Telegram
// ---------------------------------------------------------------------------

@Serializable
data class DeckCard(
    val deck: Deck,
    val id: Int,
    val title: String,
    val titleEn: String,
    val text: String,
    val textEn: String,
    /** true = kartu disimpan pemain (Surat Bebas Diplomatik), tidak langsung dibuang. */
    val keep: Boolean = false,
    val effects: List<CardEffect>,
)

@Serializable
sealed interface CardEffect {
    /** Maju (searah jarum jam) ke petak tujuan lalu jalankan efek petak itu. */
    @Serializable @SerialName("moveToTile")
    data class MoveToTile(
        val tile: Int,
        val collectSalary: Boolean,
        /** Pengali sewa khusus kartu (Kereta Malam 2×). */
        val rentMultiplier: Int = 1,
        /** Tuduhan Akhir tanpa biaya di petak tujuan (Undangan Kedutaan). */
        val freeAccuse: Boolean = false,
    ) : CardEffect

    @Serializable @SerialName("moveToNearest")
    data class MoveToNearest(val kind: SpecialKind, val collectSalary: Boolean) : CardEffect

    @Serializable @SerialName("moveBack")
    data class MoveBack(val steps: Int) : CardEffect

    @Serializable @SerialName("goToJail")
    data object GoToJail : CardEffect

    @Serializable @SerialName("gainFromBank")
    data class GainFromBank(val amount: Int) : CardEffect

    @Serializable @SerialName("payToVault")
    data class PayToVault(val amount: Int) : CardEffect

    @Serializable @SerialName("payToBank")
    data class PayToBank(val amount: Int) : CardEffect

    @Serializable @SerialName("payPerBuilding")
    data class PayPerBuilding(val perSafeHouse: Int, val perHq: Int) : CardEffect

    @Serializable @SerialName("payPercentOfCashToVault")
    data class PayPercentOfCashToVault(val percent: Int) : CardEffect

    @Serializable @SerialName("payEachOtherPlayer")
    data class PayEachOtherPlayer(val amount: Int) : CardEffect

    @Serializable @SerialName("collectFromEachOtherPlayer")
    data class CollectFromEachOtherPlayer(val amount: Int) : CardEffect

    @Serializable @SerialName("peekRandomFromNeighbor")
    data class PeekRandomFromNeighbor(val direction: Direction) : CardEffect

    @Serializable @SerialName("peekRandomFromRichest")
    data object PeekRandomFromRichest : CardEffect

    @Serializable @SerialName("showOwnCardToNeighbor")
    data class ShowOwnCardToNeighbor(val direction: Direction) : CardEffect

    @Serializable @SerialName("forceShowByType")
    data class ForceShowByType(val targets: TargetScope, val cardType: EvidenceType) : CardEffect

    @Serializable @SerialName("jailPass")
    data object JailPass : CardEffect

    @Serializable @SerialName("skipNextSuggestion")
    data object SkipNextSuggestion : CardEffect

    @Serializable @SerialName("extraSuggestion")
    data class ExtraSuggestion(val turns: Int) : CardEffect

    @Serializable @SerialName("gainIntel")
    data class GainIntel(val amount: Int) : CardEffect

    @Serializable @SerialName("loseIntel")
    data class LoseIntel(val amount: Int) : CardEffect

    @Serializable @SerialName("addSuspicion")
    data class AddSuspicion(val amount: Int) : CardEffect

    @Serializable @SerialName("reduceSuspicion")
    data class ReduceSuspicion(val amount: Int) : CardEffect
}

// ---------------------------------------------------------------------------
// Misi Rahasia
// ---------------------------------------------------------------------------

@Serializable
data class MissionCard(
    val id: Int,
    val title: String,
    val titleEn: String,
    val text: String,
    val textEn: String,
    val points: Int,
    val condition: MissionCondition,
)

/** Syarat misi, diperiksa saat skor Akhir Perang. */
@Serializable
sealed interface MissionCondition {
    @Serializable @SerialName("ownDistrict") data class OwnDistrict(val district: District) : MissionCondition
    @Serializable @SerialName("escapeRoutes") data class EscapeRoutes(val count: Int) : MissionCondition
    @Serializable @SerialName("cashAtLeast") data class CashAtLeast(val amount: Int) : MissionCondition
    @Serializable @SerialName("buildingPoints") data class BuildingPoints(val count: Int) : MissionCondition
    @Serializable @SerialName("knownCards") data class KnownCards(val count: Int) : MissionCondition
    @Serializable @SerialName("suspicionAtMost") data class SuspicionAtMost(val max: Int) : MissionCondition
    @Serializable @SerialName("propertyCount") data class PropertyCount(val count: Int) : MissionCondition
    @Serializable @SerialName("intelAtLeast") data class IntelAtLeast(val count: Int) : MissionCondition
    @Serializable @SerialName("correctGuesses") data class CorrectGuesses(val count: Int) : MissionCondition
}
