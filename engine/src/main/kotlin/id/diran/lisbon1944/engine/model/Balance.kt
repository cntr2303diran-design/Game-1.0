package id.diran.lisbon1944.engine.model

import kotlinx.serialization.Serializable

/** Semua angka keseimbangan (balance.json). Disalin ke GameConfig agar save lama tetap konsisten. */
@Serializable
data class Balance(
    val startingMoney: Int,
    val startSalary: Int,
    val ossSalary: Int,
    val buildCost: Map<District, Int>,
    val levelMultipliers: List<Int>,
    val monopolyBaseMultiplier: Int,
    val escapeRouteRent: List<Int>,
    val escapeRouteTravelCost: Int,
    val infoPaymentMaxRent: Int,
    val infoPaymentQuota: Int,
    val suggestionNoCardReward: Int,
    val blackMarketPrice: Int,
    val decodeExtraRollCost: Int,
    val decodeMaxExtraRolls: Int,
    val decodeEmptyHandReward: Int,
    val casinoMinBet: Int,
    val casinoMaxBet: Int,
    val casinoBetStep: Int,
    val casinoPayout: CasinoPayout,
    val sevenWinsVault: Boolean,
    val jailFine: Int,
    val maxJailTurns: Int,
    val embassyPassCost: Int,
    val aprilAccuseCost: Int,
    val mortgageInterest: Double,
    val portugalDiscount: Double,
    val auctionStart: Int,
    val auctionStep: Int,
    val warTax: Int,
    val bribe: Int,
    val burnPenaltyFraction: Double,
    val safeHouseStock: Int,
    val hqStock: Int,
    val score: ScoreRules,
    val familyMode: FamilyModeRules,
    val quickMode: QuickModeRules,
)

@Serializable
data class CasinoPayout(val seven: Int, val eleven: Int, val doubles: Int)

@Serializable
data class ScoreRules(
    val cashPer: Int,
    val propertyPer: Int,
    val safeHouse: Int,
    val hq: Int,
    val correctGuess: Int,
    val burnedFactor: Double,
)

@Serializable
data class FamilyModeRules(val wrongAccusePenalty: Int)

@Serializable
data class QuickModeRules(val startProperties: Int, val startWarStep: Int)
