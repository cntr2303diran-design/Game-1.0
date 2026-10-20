package id.diran.lisbon1944.engine.content

import id.diran.lisbon1944.engine.model.CardEffect
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.District
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.FrontEffect
import id.diran.lisbon1944.engine.model.FrontId
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.SpecialKind
import id.diran.lisbon1944.engine.model.SpecialTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentValidatorTest {

    private val good = ContentLoader.parse(ContentLoader.defaultSources())

    private fun assertInvalid(content: GameContent, fragment: String) {
        val e = assertFailsWith<ContentValidationException> { ContentValidator.validateOrThrow(content) }
        assertTrue(e.errors.any { it.contains(fragment) }, "diharapkan galat memuat '$fragment', ditemukan:\n${e.errors.joinToString("\n")}")
    }

    @Test
    fun `konten bawaan valid tanpa satu pun galat`() {
        assertEquals(emptyList(), ContentValidator.validate(good))
    }

    @Test
    fun `papan bukan 40 petak ditolak`() {
        assertInvalid(good.copy(board = good.board.dropLast(1)), "papan harus 40 petak")
    }

    @Test
    fun `indeks petak tidak berurutan ditolak`() {
        val swapped = good.board.toMutableList().also { val t = it[1]; it[1] = it[3]; it[3] = t }
        assertInvalid(good.copy(board = swapped), "petak ke-1 punya index 3")
    }

    @Test
    fun `distrik bukan 3 properti ditolak`() {
        val board = good.board.map { if (it is PropertyTile && it.index == 37) it.copy(district = District.AVENIDA) else it }
        val c = good.copy(board = board)
        assertInvalid(c, "distrik ESTORIL harus punya 3 properti, ditemukan 2")
        assertInvalid(c, "distrik AVENIDA harus punya 3 properti, ditemukan 4")
    }

    @Test
    fun `sudut di tempat salah ditolak`() {
        val board = good.board.map { if (it.index == 10) (it as SpecialTile).copy(kind = SpecialKind.CASINO) else it }
        assertInvalid(good.copy(board = board), "petak 10 harus sudut JAIL")
    }

    @Test
    fun `kartu bukti bukan 6-6-6 ditolak`() {
        val ev = good.evidence.map { if (it.id == CardId("I6")) it.copy(type = EvidenceType.SUSPECT, role = "x") else it }
        val c = good.copy(evidence = ev)
        assertInvalid(c, "harus 6 kartu SUSPECT, ditemukan 7")
        assertInvalid(c, "harus 6 kartu ITEM, ditemukan 5")
    }

    @Test
    fun `kartu lokasi ganda distrik ditolak`() {
        val ev = good.evidence.map { if (it.id == CardId("L2")) it.copy(district = District.PELABUHAN) else it }
        assertInvalid(good.copy(evidence = ev), "distrik tanpa kartu Lokasi: ALFAMA")
    }

    @Test
    fun `id kartu bukti duplikat ditolak`() {
        val ev = good.evidence.map { if (it.id == CardId("S2")) it.copy(id = CardId("S1")) else it }
        assertInvalid(good.copy(evidence = ev), "id kartu duplikat")
    }

    @Test
    fun `front bukan 12 ditolak`() {
        assertInvalid(good.copy(fronts = good.fronts.dropLast(1)), "harus 12 kartu Front, ditemukan 11")
    }

    @Test
    fun `front dikocok ditolak`() {
        val shuffled = good.fronts.toMutableList().also { val t = it[0]; it[0] = it[1]; it[1] = t }
        assertInvalid(good.copy(fronts = shuffled), "harus urut 1..12")
    }

    @Test
    fun `endGame selain front 12 ditolak`() {
        val fronts = good.fronts.map { if (it.id == FrontId(7)) it.copy(effects = listOf(FrontEffect.EndGame)) else it }
        assertInvalid(good.copy(fronts = fronts), "hanya Front #12")
    }

    @Test
    fun `sandi bukan 16 ditolak`() {
        assertInvalid(good.copy(cipher = good.cipher.drop(1)), "cipher.json: harus 16 kartu, ditemukan 15")
    }

    @Test
    fun `telegram bukan 16 ditolak`() {
        val extra = good.telegram + good.telegram.first().copy(id = 17)
        assertInvalid(good.copy(telegram = extra), "telegram.json: harus 16 kartu, ditemukan 17")
    }

    @Test
    fun `tujuan kartu di luar papan ditolak`() {
        val cipher = good.cipher.map {
            if (it.id == 13) it.copy(effects = listOf(CardEffect.MoveToTile(tile = 40, collectSalary = true))) else it
        }
        assertInvalid(good.copy(cipher = cipher), "tujuan 40 di luar papan")
    }

    @Test
    fun `keep tanpa jailPass ditolak`() {
        val telegram = good.telegram.map { if (it.id == 1) it.copy(keep = true) else it }
        assertInvalid(good.copy(telegram = telegram), "kartu 1 keep=true")
    }

    @Test
    fun `faksi hilang ditolak`() {
        assertInvalid(good.copy(factions = good.factions.filter { it.id != Faction.OSS }), "faksi hilang: OSS")
    }

    @Test
    fun `balance tidak masuk akal ditolak`() {
        val b = good.balance
        assertInvalid(good.copy(balance = b.copy(levelMultipliers = listOf(5, 15, 35))), "levelMultipliers harus 4 nilai")
        assertInvalid(good.copy(balance = b.copy(casinoMaxBet = 40)), "casinoMaxBet harus ≥ casinoMinBet")
        assertInvalid(good.copy(balance = b.copy(buildCost = b.buildCost - District.CHIADO)), "buildCost tidak lengkap: CHIADO")
        assertInvalid(good.copy(balance = b.copy(mortgageInterest = 1.5)), "mortgageInterest harus 0..1")
        assertInvalid(good.copy(balance = b.copy(quickMode = b.quickMode.copy(startWarStep = 13))), "startWarStep")
    }

    @Test
    fun `semua galat dikumpulkan sekaligus`() {
        val c = good.copy(cipher = good.cipher.drop(1), telegram = good.telegram.drop(2), fronts = good.fronts.dropLast(1))
        val errors = ContentValidator.validate(c)
        assertTrue(errors.size >= 3, errors.joinToString("\n"))
    }
}
