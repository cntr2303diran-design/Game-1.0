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
    val warClock: WarClockRules,
    /** Tuduhan Akhir baru boleh sejak langkah Jam Perang ini. */
    val accuseUnlockStep: Int,
    /** false = pemain yang "lewat" saat Dugaan hanya diketahui penanya. */
    val publicPasses: Boolean,
    val intel: IntelRules,
    val suspicion: SuspicionRules,
    val smugglerBlackMarketPrice: Int,
    /** Skala global sewa bangunan distrik (persen), diterapkan terakhir sebelum pembulatan. */
    val buildingRentPercent: Int,
    /** Dominasi Ekonomi: saat Front dibalik (sejak langkah [dominanceFromStep]), pemain yang kekayaannya ≥ persen ini dari
     *  total kekayaan meja menang Ekonomi. 0 = nonaktif (hanya "pemain terakhir yang tersisa"). */
    val dominancePercent: Int,
    val dominanceFromStep: Int,
    /** Dominasi Ekonomi hanya berlaku di meja dengan minimal sekian pemain. */
    val dominanceMinPlayers: Int,
    /** Setiap langkah Jam memakai kartu Front A atau B secara acak (rahasia sampai dibalik). */
    val frontVariants: Boolean,
    val tactics: TacticRules,
    val patrol: PatrolRules,
    val contact: ContactRules,
    val comeback: ComebackRules,
    val operationsEnabled: Boolean,
)

@Serializable
data class TacticRules(val enabled: Boolean, val handLimit: Int, val drawPerStartPass: Int, val maxPerTurn: Int, val startHand: Int)

/** Patroli PIDE: pion netral yang bergerak tiap awal putaran; berhenti di petaknya = Kecurigaan naik. */
@Serializable
data class PatrolRules(val enabled: Boolean, val startTile: Int, val suspicion: Int, val dice: Int)

/** Kontak Netral: pion netral; berhenti di petaknya = Intel + uang. */
@Serializable
data class ContactRules(val enabled: Boolean, val startTile: Int, val intel: Int, val money: Int, val dice: Int)

/** Bantuan Sekutu: saat Front dibalik, pemain dengan kekayaan terendah menerima bantuan. */
@Serializable
data class ComebackRules(val enabled: Boolean, val money: Int, val intel: Int)

@Serializable
data class WarClockRules(
    val mode: WarClockMode,
    /** START_PASSES: rata-rata lintasan START (total semua pemain) per langkah Jam — durasi tak bergantung jumlah pemain. */
    val passesPerStep: Double,
)

@Serializable
data class IntelRules(
    val start: Int,
    val max: Int,
    val perStartPass: Int,
    val suggestionCost: Int,
    val interrogationFree: Boolean,
    val decodeSuccess: Int,
    val defectorBonus: Int,
    /** Intel yang dihabiskan untuk Tuduhan Akhir ("dossier lengkap"). */
    val accuseCost: Int,
    /** Intel untuk "Bungkam": menolak menunjukkan kartu saat Dugaan (penanya tidak mendapat apa-apa). */
    val silenceCost: Int,
)

@Serializable
data class SuspicionRules(
    val limit: Int,
    val blackMarket: Int,
    val casinoWin: Int,
    val decayPerStartPass: Int,
    val bribeRelief: Int,
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
    val missionsEnabled: Boolean,
)

@Serializable
data class FamilyModeRules(val wrongAccusePenalty: Int)

@Serializable
data class QuickModeRules(val startProperties: Int, val startWarStep: Int)
