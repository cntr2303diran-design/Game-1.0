package id.diran.lisbon1944.engine.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerSetup(
    val name: String,
    val faction: Faction,
    val isBot: Boolean = false,
    val botLevel: BotLevel? = null,
)

@Serializable
data class GameConfig(
    val players: List<PlayerSetup>,
    val balance: Balance,
    val mode: GameMode = GameMode.STANDARD,
    val doubleAgentVariant: Boolean = false,
    val warClockMode: WarClockMode = WarClockMode.FIRST_PASS_PER_ROUND,
    /** Jika diisi, menimpa balance.infoPaymentQuota (house rule = 1). */
    val infoPaymentQuotaOverride: Int? = null,
    /** Mode Kampanye: properti yang dimulai sebagai milik pemain (indeks petak → kursi). */
    val presetOwnership: Map<Int, Int> = emptyMap(),
) {
    val infoPaymentQuota: Int get() = infoPaymentQuotaOverride ?: balance.infoPaymentQuota

    /** Daftar pelanggaran; kosong berarti konfigurasi sah. */
    fun validate(): List<String> = buildList {
        if (players.size !in MIN_PLAYERS..MAX_PLAYERS) {
            add("Jumlah pemain harus $MIN_PLAYERS–$MAX_PLAYERS, ditemukan ${players.size}")
        }
        val dupFactions = players.groupBy { it.faction }.filterValues { it.size > 1 }.keys
        if (dupFactions.isNotEmpty()) add("Faksi harus unik, duplikat: ${dupFactions.joinToString()}")
        players.forEachIndexed { i, p ->
            if (p.name.isBlank()) add("Nama pemain #${i + 1} kosong")
            if (p.isBot && p.botLevel == null) add("Bot #${i + 1} (${p.name}) belum punya tingkat kesulitan")
            if (!p.isBot && p.botLevel != null) add("Pemain manusia #${i + 1} (${p.name}) tidak boleh punya tingkat bot")
        }
        if (doubleAgentVariant && players.size < 5) add("Varian Agen Ganda hanya untuk 5–6 pemain")
        // Game tanpa manusia sah (dipakai :sim); layar Setup di :app sendiri mewajibkan ≥1 manusia.
        infoPaymentQuotaOverride?.let { if (it < 0) add("infoPaymentQuotaOverride tidak boleh negatif") }
        presetOwnership.forEach { (tile, seat) ->
            if (seat !in players.indices) add("presetOwnership: petak $tile menunjuk kursi $seat yang tidak ada")
        }
    }

    companion object {
        const val MIN_PLAYERS = 3
        const val MAX_PLAYERS = 6
    }
}
