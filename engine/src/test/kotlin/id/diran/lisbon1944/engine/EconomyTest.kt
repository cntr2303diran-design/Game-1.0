package id.diran.lisbon1944.engine

import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.District
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.testing.GameStateFixture
import id.diran.lisbon1944.engine.testing.ScriptedRng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EconomyTest {

    private val p0 = PlayerId(0)

    @Test
    fun `bangun level 2 di Gudang 7 saat Dermaga Ikan level 0 ditolak`() {
        val f = GameStateFixture().front(2).own(1, 0, level = 1).own(3, 0).own(6, 0).business(0)
        assertEquals(ViolationCode.BUILD_UNEVEN, f.reject(Action.Build(p0, 1)).code)
        f.act(Action.Build(p0, 3))
        assertEquals(1, f.state.ownership.getValue(3).level)
        assertEquals(1450, f.p(0).money)
    }

    @Test
    fun `biaya bangun Avenida saat Front Nov 1944 = 225`() {
        val f = GameStateFixture().front(6).own(23, 0).own(24, 0).own(26, 0).business(0)
        assertEquals(225, f.engine.buildCost(f.state, District.AVENIDA))
        f.act(Action.Build(p0, 23))
        assertEquals(1500 - 225, f.p(0).money)
        assertEquals(150, f.engine.buildCost(f.front(7).state, District.AVENIDA))
    }

    @Test
    fun `stok Rumah Aman 0 maka bangun ditolak`() {
        val f = GameStateFixture().front(2).own(1, 0).own(3, 0).own(6, 0).business(0).set { copy(houseStock = 0) }
        assertEquals(ViolationCode.BUILD_NO_STOCK, f.reject(Action.Build(p0, 1)).code)
    }

    @Test
    fun `tidak boleh bangun tanpa Sel Mata-Mata atau dengan properti tergadai`() {
        val f = GameStateFixture().front(2).own(1, 0).own(3, 0).business(0)
        assertEquals(ViolationCode.BUILD_NOT_CELL, f.reject(Action.Build(p0, 1)).code)
        f.own(6, 0, mortgaged = true)
        assertEquals(ViolationCode.BUILD_MORTGAGED, f.reject(Action.Build(p0, 1)).code)
    }

    @Test
    fun `Markas mengembalikan 4 Rumah Aman ke stok`() {
        val f = GameStateFixture().front(2).own(1, 0, level = 4).own(3, 0, level = 4).own(6, 0, level = 4).business(0)
        val houses = f.state.houseStock
        f.act(Action.Build(p0, 1))
        assertEquals(5, f.state.ownership.getValue(1).level)
        assertEquals(houses + 4, f.state.houseStock)
        assertEquals(11, f.state.hqStock)
    }

    @Test
    fun `jual bangunan setengah harga dasar dan harus merata`() {
        val f = GameStateFixture().front(6).own(23, 0, level = 2).own(24, 0, level = 1).own(26, 0, level = 1).business(0)
        assertEquals(ViolationCode.SELL_UNEVEN, f.reject(Action.SellBuilding(p0, 24)).code)
        f.act(Action.SellBuilding(p0, 23))
        assertEquals(1500 + 75, f.p(0).money, "setengah harga dasar 150, bukan harga inflasi")
    }

    @Test
    fun `gadai dan tebus dengan bunga 10 persen dibulatkan ke atas`() {
        val f = GameStateFixture().front(2).own(1, 0).business(0)
        f.act(Action.Mortgage(p0, 1))
        assertEquals(1530, f.p(0).money)
        assertEquals(33, f.engine.unmortgageCost(f.state, 1))
        f.act(Action.Unmortgage(p0, 1))
        assertEquals(1497, f.p(0).money)
        val g = GameStateFixture().front(2).own(5, 0).business(0)
        g.act(Action.Mortgage(p0, 5))
        assertEquals(110, g.engine.unmortgageCost(g.state, 5))
    }

    @Test
    fun `tidak boleh gadai bila distrik masih punya bangunan`() {
        val f = GameStateFixture().front(2).own(1, 0, level = 1).own(3, 0).own(6, 0).business(0)
        assertEquals(ViolationCode.DISTRICT_HAS_BUILDINGS, f.reject(Action.Mortgage(p0, 3)).code)
    }

    @Test
    fun `Portugal membeli Pantai Cascais seharga 315`() {
        val f = GameStateFixture(factions = listOf(Faction.PORTUGAL_TRADER, Faction.MI6, Faction.NKVD, Faction.RESISTANCE)).front(2)
        f.land(0, 37)
        assertEquals(315, assertIs<PendingDecision.BuyOrAuction>(f.pending).price)
        f.act(Action.BuyProperty(p0))
        assertEquals(1185, f.p(0).money)
        assertEquals(p0, f.state.ownership.getValue(37).ownerId)
    }

    @Test
    fun `lelang searah jarum jam dengan penolak terakhir`() {
        val f = GameStateFixture().front(2)
        f.land(0, 1)
        f.act(Action.DeclineBuy(p0))
        assertEquals(listOf(1, 2, 3, 0).map(::PlayerId), f.state.auction!!.order)
        assertEquals(PendingDecision.AuctionBid(PlayerId(1), 1, 10), f.pending)
        f.act(Action.Bid(PlayerId(1), 10))
        assertEquals(PendingDecision.AuctionBid(PlayerId(2), 1, 20), f.pending)
        assertEquals(ViolationCode.INVALID_AMOUNT, f.reject(Action.Bid(PlayerId(2), 15)).code)
        f.act(Action.PassBid(PlayerId(2)))
        f.act(Action.Bid(PlayerId(3), 50))
        f.act(Action.PassBid(PlayerId(0)))
        assertEquals(PendingDecision.AuctionBid(PlayerId(1), 1, 60), f.pending)
        f.act(Action.PassBid(PlayerId(1)))
        assertEquals(PlayerId(3), f.state.ownership.getValue(1).ownerId)
        assertEquals(1450, f.p(3).money)
        assertNull(f.state.auction)
        assertIs<PendingDecision.BusinessPhase>(f.pending)
    }

    @Test
    fun `lelang tanpa penawar membuat properti tetap tak bertuan`() {
        val f = GameStateFixture(players = 3).front(2)
        f.land(0, 1)
        f.act(Action.DeclineBuy(p0))
        repeat(3) { f.act(Action.PassBid(f.pending!!.decider)) }
        assertNull(f.state.ownership[1])
    }

    @Test
    fun `pindah Jalur Pelarian membayar 50 tanpa gaji`() {
        val f = GameStateFixture().front(2).own(5, 0).own(35, 0).at(0, 35).turn(0)
        f.act(Action.UseEscapeRoute(p0, 5))
        assertEquals(5, f.p(0).position)
        assertEquals(1450, f.p(0).money)
        assertEquals(ViolationCode.ROUTE_NOT_AVAILABLE, GameStateFixture().front(2).own(5, 0).at(0, 35).turn(0).reject(Action.UseEscapeRoute(p0, 5)).code)
    }

    @Test
    fun `Pesawat Amfibi tidak bisa menjadi tujuan saat ditutup`() {
        val f = GameStateFixture().front(4).own(5, 0).own(25, 0).at(0, 5).turn(0)
        assertEquals(ViolationCode.ROUTE_NOT_AVAILABLE, f.reject(Action.UseEscapeRoute(p0, 25)).code)
    }

    @Test
    fun `perdagangan memindahkan uang properti dan kartu Bukti secara rahasia`() {
        val f = GameStateFixture().front(2)
            .cards(listOf("S1", "L1", "I1"), listOf("S2", "L2"), listOf("S3"), listOf("S4"), listOf("S5"))
            .own(1, 0).business(0)
        val offer = id.diran.lisbon1944.engine.model.TradeOffer(
            from = p0, to = PlayerId(1), giveMoney = 100, giveProperties = listOf(1), giveEvidence = listOf(id.diran.lisbon1944.engine.testing.cid("L2")),
            takeEvidence = listOf(id.diran.lisbon1944.engine.testing.cid("S3")),
        )
        f.act(Action.ProposeTrade(p0, offer))
        assertEquals(PendingDecision.TradeResponse(PlayerId(1), offer), f.pending)
        assertNull(f.engine.viewFor(f.state, PlayerId(2)).trade, "pihak ketiga tidak melihat isi tawaran")
        f.act(Action.RespondTrade(PlayerId(1), true))
        assertEquals(1400, f.p(0).money)
        assertEquals(1600, f.p(1).money)
        assertEquals(PlayerId(1), f.state.ownership.getValue(1).ownerId)
        assertTrue(id.diran.lisbon1944.engine.testing.cid("L2") in f.p(1).hand)
        assertTrue(id.diran.lisbon1944.engine.testing.cid("S3") in f.p(0).hand)
        assertTrue(f.state.eventLog.filter { it.type == GameEvents.CARD_SEEN }.none { it.visibility.visibleTo(PlayerId(2)) })
        assertIs<PendingDecision.BusinessPhase>(f.pending)
    }

    @Test
    fun `tawaran dagang tidak sah ditolak`() {
        val f = GameStateFixture().front(2).own(1, 0, level = 0).own(3, 0).own(6, 0).business(0)
        val bad = id.diran.lisbon1944.engine.model.TradeOffer(from = p0, to = PlayerId(1), giveMoney = 5000)
        assertEquals(ViolationCode.INVALID_TRADE, f.reject(Action.ProposeTrade(p0, bad)).code)
        val empty = id.diran.lisbon1944.engine.model.TradeOffer(from = p0, to = PlayerId(1))
        assertEquals(ViolationCode.INVALID_TRADE, f.reject(Action.ProposeTrade(p0, empty)).code)
    }

    @Test
    fun `bangkrut ke pemain lain memindahkan aset dan membuka kartu`() {
        val f = GameStateFixture().front(2)
            .cards(listOf("S1", "L1", "I1"), listOf("S2", "L2"), listOf("S3"), listOf("S4"), listOf("S5"))
            .own(1, 1, level = 4).own(3, 1, level = 4).own(6, 1, level = 5).own(13, 0, mortgaged = true)
            .money(0, 10)
        f.land(0, 6)
        f.act(Action.PayRentMoney(p0))
        val debt = assertIs<PendingDecision.RaiseFunds>(f.pending)
        assertEquals(320, debt.amountOwed)
        f.act(Action.DeclareBankruptcy(p0))
        assertTrue(f.p(0).bankrupt)
        assertEquals(1510, f.p(1).money)
        assertEquals(PlayerId(1), f.state.ownership.getValue(13).ownerId)
        assertTrue(f.state.ownership.getValue(13).mortgaged, "status gadai tetap")
        (1..3).forEach { viewer ->
            assertTrue(f.state.knowledge[viewer].any { it.cardId == id.diran.lisbon1944.engine.testing.cid("L2") })
        }
        assertEquals(PendingDecision.AwaitRoll(PlayerId(1)), f.pending)
    }

    @Test
    fun `DeclareBankruptcy ditolak selama masih bisa menggadai`() {
        val f = GameStateFixture().front(2).own(1, 1, level = 4).own(3, 1, level = 4).own(6, 1, level = 5).own(13, 0).money(0, 10)
        f.land(0, 6)
        f.act(Action.PayRentMoney(p0))
        assertEquals(ViolationCode.CAN_STILL_RAISE_FUNDS, f.reject(Action.DeclareBankruptcy(p0)).code)
        f.act(Action.Mortgage(p0, 13))
        // 10 + 70 = 80 < 320: masih berutang.
        assertIs<PendingDecision.RaiseFunds>(f.pending)
    }

    @Test
    fun `utang terbayar otomatis setelah dana cukup`() {
        val f = GameStateFixture().front(2).own(1, 1).own(37, 0).money(0, 2)
        f.land(0, 1)
        f.act(Action.PayRentMoney(p0))
        f.act(Action.Mortgage(p0, 37))
        assertEquals(2 + 175 - 4, f.p(0).money)
        assertEquals(1504, f.p(1).money)
    }

    @Test
    fun `kemenangan ekonomi saat lawan terakhir bangkrut`() {
        val f = GameStateFixture(players = 3).front(2).own(1, 1, level = 4).own(3, 1, level = 4).own(6, 1, level = 5)
            .player(2) { copy(bankrupt = true, money = 0) }.money(0, 5)
        f.land(0, 6)
        f.act(Action.PayRentMoney(p0))
        f.act(Action.DeclareBankruptcy(p0))
        assertEquals(id.diran.lisbon1944.engine.model.WinType.ECONOMIC, f.state.winner!!.type)
        assertEquals(listOf(PlayerId(1)), f.state.winner!!.winners)
    }

    @Test
    fun `kartu Biaya Perbaikan menghitung bangunan`() {
        val f = GameStateFixture().own(1, 0, level = 5).own(3, 0, level = 4).own(6, 0, level = 4)
        assertEquals(8 to 1, id.diran.lisbon1944.engine.internal.Economy.buildingCount(f.state, p0))
        val dice = ScriptedRng()
        assertEquals(0, dice.remaining)
    }
}
