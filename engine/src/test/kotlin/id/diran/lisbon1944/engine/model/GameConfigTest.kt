package id.diran.lisbon1944.engine.model

import id.diran.lisbon1944.engine.content.ContentLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameConfigTest {

    private val balance = ContentLoader.loadDefault().balance

    private fun players(n: Int) = Faction.entries.take(n).mapIndexed { i, f -> PlayerSetup("Agen ${i + 1}", f) }

    @Test
    fun `3 sampai 6 pemain sah`() {
        (3..6).forEach { n -> assertEquals(emptyList(), GameConfig(players(n), balance).validate(), "$n pemain") }
    }

    @Test
    fun `2 atau 7 pemain ditolak`() {
        assertTrue(GameConfig(players(2), balance).validate().any { it.contains("Jumlah pemain") })
        val seven = players(6) + PlayerSetup("Agen 7", Faction.MI6)
        assertTrue(GameConfig(seven, balance).validate().any { it.contains("Jumlah pemain") })
    }

    @Test
    fun `faksi duplikat ditolak`() {
        val p = players(3).toMutableList().also { it[2] = it[2].copy(faction = Faction.MI6) }
        assertTrue(GameConfig(p, balance).validate().any { it.contains("Faksi harus unik") })
    }

    @Test
    fun `agen ganda hanya 5 sampai 6 pemain`() {
        assertTrue(GameConfig(players(4), balance, doubleAgentVariant = true).validate().any { it.contains("Agen Ganda") })
        assertEquals(emptyList(), GameConfig(players(5), balance, doubleAgentVariant = true).validate())
    }

    @Test
    fun `bot wajib punya tingkat dan manusia tidak boleh`() {
        val p = players(3).toMutableList()
        p[0] = p[0].copy(isBot = true)
        p[1] = p[1].copy(botLevel = BotLevel.HARD)
        val errors = GameConfig(p, balance).validate()
        assertTrue(errors.any { it.contains("belum punya tingkat") })
        assertTrue(errors.any { it.contains("tidak boleh punya tingkat bot") })
    }

    @Test
    fun `kuota bayar informasi bisa ditimpa house rule`() {
        assertEquals(2, GameConfig(players(4), balance).infoPaymentQuota)
        assertEquals(1, GameConfig(players(4), balance, infoPaymentQuotaOverride = 1).infoPaymentQuota)
    }
}
