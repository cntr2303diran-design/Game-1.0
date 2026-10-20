package id.diran.lisbon1944.bot

import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.District
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

enum class DeductionMode { BASIC, FULL }

/** Parameter perilaku satu tingkat bot (bot-config.json). Semua angka di sini, tidak di kode. */
data class BotParams(
    /** Pelacak dasar (tanpa constraint "punya salah satu") atau penuh. */
    val deduction: DeductionMode,
    /** Uang cadangan minimum setelah membeli/membangun. */
    val reserve: Int,
    /** Cadangan sejak langkah [lateStep] (siap Tuduhan April 1945). */
    val lateReserve: Int,
    val lateStep: Int,
    /** Easy: beli bila uang > harga + buyMargin. Normal/Hard: margin di atas cadangan. */
    val buyMargin: Int,
    val priorityDistricts: List<District>,
    val priorityUntilStep: Int,
    /** Nilai tawaran lelang maksimum = harga × faktor (+ bonus bila melengkapi distrik). */
    val auctionFactor: Double,
    val setBonus: Double,
    val buildReserve: Int,
    val stopBuildingStep: Int,
    val unmortgageReserve: Int,
    val jailPayBeforeStep: Int,
    /** Bayar informasi bila kartu yang ditunjukkan sudah diketahui ≥ N pemain lain … */
    val infoKnownBy: Int,
    /** … atau sewa > bagian ini dari uang tunai. */
    val infoRentShare: Double,
    val accuseThreshold: Double,
    val lateAccuseThreshold: Double,
    val lateAccuseStep: Int,
    val desperateAccuseThreshold: Double,
    /** "Kalah jauh" = skor proyeksi < pemimpin × (1 − desperateDeficit). */
    val desperateDeficit: Double,
    val suggestChance: Double,
    val decodeExtraRolls: Boolean,
    val casinoMinVault: Int,
    val casinoChance: Double,
    val blackMarketReserve: Int,
    val useAbility: Double,
    val tradeAcceptMargin: Int,
    /** Peluang mengambil aksi legal acak (khas Easy). */
    val randomness: Double,
)

data class BotConfig(val easy: BotParams, val normal: BotParams, val hard: BotParams) {
    fun params(level: BotLevel): BotParams = when (level) {
        BotLevel.EASY -> easy
        BotLevel.NORMAL -> normal
        BotLevel.HARD -> hard
    }

    companion object {
        fun loadDefault(): BotConfig {
            val text = BotConfig::class.java.getResourceAsStream("/bot-config.json")
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("bot-config.json tidak ditemukan di classpath")
            return parse(text)
        }

        fun parse(text: String): BotConfig {
            val root = Json.parseToJsonElement(text) as? JsonObject ?: error("bot-config.json: harus objek")
            fun level(name: String): BotParams {
                val o = root[name] as? JsonObject ?: error("bot-config.json: '$name' tidak ada")
                fun prim(k: String) = o[k] as? JsonPrimitive ?: error("bot-config.json: $name.$k tidak ada")
                fun int(k: String) = prim(k).intOrNull ?: error("bot-config.json: $name.$k harus bilangan bulat")
                fun dbl(k: String) = prim(k).doubleOrNull ?: error("bot-config.json: $name.$k harus angka")
                fun bool(k: String) = prim(k).booleanOrNull ?: error("bot-config.json: $name.$k harus boolean")
                val districts = (o["priorityDistricts"] as? JsonArray ?: error("bot-config.json: $name.priorityDistricts"))
                    .map { el -> District.valueOf((el as JsonPrimitive).content) }
                val deduction = when (prim("deduction").content) {
                    "basic" -> DeductionMode.BASIC
                    "full" -> DeductionMode.FULL
                    else -> error("bot-config.json: $name.deduction harus basic|full")
                }
                return BotParams(
                    deduction = deduction,
                    reserve = int("reserve"),
                    lateReserve = int("lateReserve"),
                    lateStep = int("lateStep"),
                    buyMargin = int("buyMargin"),
                    priorityDistricts = districts,
                    priorityUntilStep = int("priorityUntilStep"),
                    auctionFactor = dbl("auctionFactor"),
                    setBonus = dbl("setBonus"),
                    buildReserve = int("buildReserve"),
                    stopBuildingStep = int("stopBuildingStep"),
                    unmortgageReserve = int("unmortgageReserve"),
                    jailPayBeforeStep = int("jailPayBeforeStep"),
                    infoKnownBy = int("infoKnownBy"),
                    infoRentShare = dbl("infoRentShare"),
                    accuseThreshold = dbl("accuseThreshold"),
                    lateAccuseThreshold = dbl("lateAccuseThreshold"),
                    lateAccuseStep = int("lateAccuseStep"),
                    desperateAccuseThreshold = dbl("desperateAccuseThreshold"),
                    desperateDeficit = dbl("desperateDeficit"),
                    suggestChance = dbl("suggestChance"),
                    decodeExtraRolls = bool("decodeExtraRolls"),
                    casinoMinVault = int("casinoMinVault"),
                    casinoChance = dbl("casinoChance"),
                    blackMarketReserve = int("blackMarketReserve"),
                    useAbility = dbl("useAbility"),
                    tradeAcceptMargin = int("tradeAcceptMargin"),
                    randomness = dbl("randomness"),
                )
            }
            return BotConfig(level("easy"), level("normal"), level("hard"))
        }
    }
}
