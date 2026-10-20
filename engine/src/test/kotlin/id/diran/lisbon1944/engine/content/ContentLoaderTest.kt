package id.diran.lisbon1944.engine.content

import id.diran.lisbon1944.engine.model.CardEffect
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.Direction
import id.diran.lisbon1944.engine.model.District
import id.diran.lisbon1944.engine.model.EscapeRouteTile
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.FactionAbility
import id.diran.lisbon1944.engine.model.FrontEffect
import id.diran.lisbon1944.engine.model.FrontId
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.SpecialKind
import id.diran.lisbon1944.engine.model.SpecialTile
import id.diran.lisbon1944.engine.model.TargetScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentLoaderTest {

    private val content = ContentLoader.loadDefault()

    @Test
    fun `papan bawaan 40 petak dengan komposisi 4 sudut 18 bangunan 4 jalur 14 khusus`() {
        assertEquals(40, content.board.size)
        assertEquals(18, content.properties.size)
        assertEquals(listOf(5, 15, 25, 35), content.escapeRoutes.map { it.index })
        val corners = content.board.filter { it.index % 10 == 0 }.map { (it as SpecialTile).kind }
        assertEquals(listOf(SpecialKind.START, SpecialKind.JAIL, SpecialKind.CASINO, SpecialKind.ARREST), corners)
        val specialNonCorner = content.board.filterIsInstance<SpecialTile>().count { it.index % 10 != 0 }
        assertEquals(14, specialNonCorner)
    }

    @Test
    fun `distrik berisi properti sesuai tabel papan`() {
        val expected = mapOf(
            District.PELABUHAN to listOf(1, 3, 6),
            District.ALFAMA to listOf(8, 9, 11),
            District.BAIXA to listOf(13, 14, 16),
            District.CHIADO to listOf(18, 19, 21),
            District.AVENIDA to listOf(23, 24, 26),
            District.ESTORIL to listOf(31, 32, 37),
        )
        expected.forEach { (d, idx) -> assertEquals(idx, content.propertiesIn(d).map { it.index }, "distrik $d") }
    }

    @Test
    fun `nilai harga dan sewa properti persis tabel`() {
        val rows = listOf(
            // index, price, baseRent, hqRent
            Triple(1, 60, 4) to 250, Triple(3, 60, 4) to 250, Triple(6, 80, 6) to 320,
            Triple(8, 100, 6) to 550, Triple(9, 100, 6) to 550, Triple(11, 120, 8) to 600,
            Triple(13, 140, 10) to 750, Triple(14, 140, 10) to 750, Triple(16, 160, 12) to 900,
            Triple(18, 180, 14) to 950, Triple(19, 180, 14) to 950, Triple(21, 200, 16) to 1000,
            Triple(23, 220, 18) to 1050, Triple(24, 220, 18) to 1050, Triple(26, 240, 20) to 1100,
            Triple(31, 280, 22) to 1200, Triple(32, 300, 26) to 1300, Triple(37, 350, 35) to 1500,
        )
        rows.forEach { (t, hq) ->
            val p = content.tile(t.first) as PropertyTile
            assertEquals(t.second, p.price, "harga ${p.name}")
            assertEquals(t.third, p.baseRent, "sewa ${p.name}")
            assertEquals(hq, p.hqRent, "Markas ${p.name}")
        }
        assertEquals("Gudang 7", content.tile(1).name)
        assertEquals("Pantai Cascais", content.tile(37).name)
        content.escapeRoutes.forEach { assertEquals(200, (it as EscapeRouteTile).price) }
    }

    @Test
    fun `petak khusus pada indeks yang benar`() {
        val expected = mapOf(
            2 to SpecialKind.TELEGRAM, 4 to SpecialKind.WAR_TAX, 7 to SpecialKind.CIPHER,
            12 to SpecialKind.RADIO, 17 to SpecialKind.INTERROGATION, 22 to SpecialKind.CIPHER,
            27 to SpecialKind.TELEGRAM, 28 to SpecialKind.TELEGRAPH, 29 to SpecialKind.BLACK_MARKET,
            33 to SpecialKind.TELEGRAM, 34 to SpecialKind.INTERROGATION, 36 to SpecialKind.CIPHER,
            38 to SpecialKind.BRIBE, 39 to SpecialKind.EMBASSY,
        )
        expected.forEach { (i, k) -> assertEquals(k, (content.tile(i) as SpecialTile).kind, "petak $i") }
        assertEquals(200, (content.tile(4) as SpecialTile).amount)
        assertEquals(150, (content.tile(38) as SpecialTile).amount)
        assertEquals(150, (content.tile(29) as SpecialTile).amount)
    }

    @Test
    fun `kartu bukti 6 tersangka 6 lokasi 6 barang dan lokasi memetakan distrik`() {
        EvidenceType.entries.forEach { assertEquals(6, content.evidenceOfType(it).size) }
        assertEquals((1..6).map { CardId("S$it") }, content.evidenceOfType(EvidenceType.SUSPECT).map { it.id })
        assertEquals(CardId("L3"), content.locationCardFor(District.BAIXA).id)
        assertEquals(District.ESTORIL, content.evidence(CardId("L6")).district)
        assertEquals("Baron Klaus Richter", content.evidence(CardId("S1")).name)
        assertEquals("Foto Udara", content.evidence(CardId("I6")).name)
    }

    @Test
    fun `delapan faksi dengan kemampuan dan flag sekali pakai`() {
        assertEquals(Faction.entries.toSet(), content.factions.map { it.id }.toSet())
        assertEquals(FactionAbility.TELEGRAM_DRAW_TWO, content.faction(Faction.SWISS_JOURNALIST).ability)
        val oneShot = content.factions.filter { it.oneShot }.map { it.id }.toSet()
        assertEquals(setOf(Faction.MI6, Faction.NKVD, Faction.RESISTANCE), oneShot)
    }

    @Test
    fun `front berurutan dengan efek terstruktur`() {
        assertEquals((1..12).toList(), content.fronts.map { it.id.value })
        assertEquals("1944-06", content.fronts.first().month)
        assertEquals("1945-05", content.fronts.last().month)
        assertEquals(
            listOf(FrontEffect.DistrictRentMultiplier(listOf(District.PELABUHAN), 2, 1)),
            content.front(FrontId(1)).effects,
        )
        assertEquals(
            listOf(FrontEffect.OnRevealFactionBonus(Faction.RESISTANCE, 150), FrontEffect.FreeSuggestionEachPlayer(1)),
            content.front(FrontId(3)).effects,
        )
        assertEquals(listOf(FrontEffect.CloseTile(25)), content.front(FrontId(4)).effects)
        assertEquals(
            listOf(FrontEffect.OnRevealJailIfMoneyAbove(1500), FrontEffect.ReshuffleEvidence(EvidenceType.LOCATION)),
            content.front(FrontId(5)).effects,
        )
        assertEquals(listOf(FrontEffect.BuildCostMultiplier(3, 2)), content.front(FrontId(6)).effects)
        assertEquals(listOf(FrontEffect.NoStartSalary), content.front(FrontId(7)).effects)
        assertEquals(
            listOf(FrontEffect.DistrictRentMultiplier(listOf(District.AVENIDA, District.CHIADO), 3, 2)),
            content.front(FrontId(9)).effects,
        )
        assertEquals(listOf(FrontEffect.EndGame), content.front(FrontId(12)).effects)
    }

    @Test
    fun `kartu sandi dan telegram terbaca dengan efek yang tepat`() {
        assertEquals(24, content.cipher.size)
        assertEquals(24, content.telegram.size)
        assertEquals(listOf(CardEffect.GainIntel(2)), content.card(Deck.CIPHER, 17).effects)
        assertEquals(listOf(CardEffect.AddSuspicion(1)), content.card(Deck.TELEGRAM, 18).effects)
        assertEquals(
            listOf(CardEffect.MoveToTile(tile = 15, collectSalary = true, rentMultiplier = 2)),
            content.card(Deck.CIPHER, 7).effects,
        )
        assertEquals(
            listOf(CardEffect.MoveToTile(tile = 39, collectSalary = true, freeAccuse = true)),
            content.card(Deck.CIPHER, 8).effects,
        )
        assertEquals(listOf(CardEffect.MoveBack(3)), content.card(Deck.CIPHER, 5).effects)
        assertEquals(listOf(CardEffect.PeekRandomFromNeighbor(Direction.RIGHT)), content.card(Deck.CIPHER, 3).effects)
        assertTrue(content.card(Deck.CIPHER, 6).keep)
        assertTrue(content.card(Deck.TELEGRAM, 14).keep)
        assertEquals(
            listOf(CardEffect.ForceShowByType(TargetScope.ALL_OTHERS, EvidenceType.ITEM)),
            content.card(Deck.TELEGRAM, 3).effects,
        )
        assertEquals(listOf(CardEffect.PayPercentOfCashToVault(10)), content.card(Deck.TELEGRAM, 15).effects)
        assertEquals(listOf(CardEffect.ExtraSuggestion(3)), content.card(Deck.TELEGRAM, 16).effects)
    }

    @Test
    fun `balance sesuai BAGIAN 13`() {
        val b = content.balance
        assertEquals(1500, b.startingMoney)
        assertEquals(200, b.startSalary)
        assertEquals(225, b.ossSalary, "diturunkan dari 250 (DECISIONS D52)")
        assertEquals(
            mapOf(
                District.PELABUHAN to 50, District.ALFAMA to 50, District.BAIXA to 100,
                District.CHIADO to 100, District.AVENIDA to 150, District.ESTORIL to 200,
            ),
            b.buildCost,
        )
        assertEquals(listOf(5, 15, 35, 45), b.levelMultipliers)
        assertEquals(listOf(25, 50, 100, 200), b.escapeRouteRent)
        assertEquals(300, b.infoPaymentMaxRent)
        assertEquals(2, b.infoPaymentQuota)
        assertEquals(3, b.casinoPayout.seven)
        assertEquals(0.10, b.mortgageInterest)
        assertEquals(32, b.safeHouseStock)
        assertEquals(12, b.hqStock)
        assertEquals(50, b.score.correctGuess)
        assertEquals(200, b.familyMode.wrongAccusePenalty)
        assertEquals(5, b.quickMode.startWarStep)
    }

    @Test
    fun `json rusak gagal keras dengan jalur field`() {
        val src = ContentLoader.defaultSources()
        val broken = src.copy(board = src.board.replaceFirst("\"price\": 60,", "\"price\": \"enam puluh\","))
        val e = assertFailsWith<ContentFormatException> { ContentLoader.load(broken) }
        assertEquals("board.json", e.file)
        assertEquals("$.tiles[1].price", e.path)
    }

    @Test
    fun `field wajib hilang gagal keras`() {
        val src = ContentLoader.defaultSources()
        val broken = src.copy(balance = src.balance.replace("\"jailFine\": 50,", ""))
        val e = assertFailsWith<ContentFormatException> { ContentLoader.load(broken) }
        assertEquals("$.jailFine", e.path)
    }

    @Test
    fun `nilai enum tidak dikenal gagal keras`() {
        val src = ContentLoader.defaultSources()
        val broken = src.copy(board = src.board.replaceFirst("\"district\": \"PELABUHAN\"", "\"district\": \"BELEM\""))
        val e = assertFailsWith<ContentFormatException> { ContentLoader.load(broken) }
        assertTrue(e.message!!.contains("BELEM"))
    }

    @Test
    fun `efek kartu tidak dikenal gagal keras`() {
        val src = ContentLoader.defaultSources()
        val broken = src.copy(cipher = src.cipher.replace("\"goToJail\"", "\"teleport\""))
        assertFailsWith<ContentFormatException> { ContentLoader.load(broken) }
    }

    @Test
    fun `json tidak valid gagal keras`() {
        val src = ContentLoader.defaultSources()
        assertFailsWith<ContentFormatException> { ContentLoader.load(src.copy(front = "{ \"fronts\": [ ")) }
    }
}
