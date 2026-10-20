package id.diran.lisbon1944.engine

import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.InfoBlockedReason
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.testing.GameStateFixture
import id.diran.lisbon1944.engine.testing.ScriptedRng
import id.diran.lisbon1944.engine.testing.cid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RentTest {

    /** Front Jul 1944 tidak punya efek berkelanjutan — dipakai sebagai latar netral. */
    private fun neutral() = GameStateFixture().front(2)

    private fun GameStateFixture.rent(tile: Int, payer: Int = 0) = engine.computeRent(state, tile, PlayerId(payer))!!

    @Test
    fun `Gudang 7 tanpa bangunan dan distrik tidak lengkap = 4`() {
        assertEquals(4, neutral().own(1, 1).rent(1).amount)
    }

    @Test
    fun `Gudang 7 dengan Sel Mata-Mata Pelabuhan = 8`() {
        assertEquals(8, neutral().own(1, 1).own(3, 1).own(6, 1).rent(1).amount)
    }

    @Test
    fun `Sel Mata-Mata plus Front Jun 1944 = 16`() {
        assertEquals(16, neutral().own(1, 1).own(3, 1).own(6, 1).front(1).rent(1).amount)
    }

    @Test
    fun `Gudang 7 level 2 = 60`() {
        assertEquals(60, neutral().own(1, 1, level = 2).own(3, 1, level = 2).own(6, 1, level = 1).rent(1).amount)
    }

    @Test
    fun `Kafe Nicola Markas = 750`() {
        assertEquals(750, neutral().own(13, 1, level = 5).own(14, 1, level = 4).own(16, 1, level = 4).rent(13).amount)
    }

    @Test
    fun `Kantor Bea Cukai Markas = 320 dan bayar informasi ditolak`() {
        val f = neutral().cards(listOf("S1", "L1", "I1"), listOf("S2", "L2", "I2")).own(1, 1, level = 4).own(3, 1, level = 4).own(6, 1, level = 5)
        val q = f.rent(6)
        assertEquals(320, q.amount)
        assertEquals(false, q.infoAllowed)
        assertEquals(InfoBlockedReason.RENT_TOO_HIGH, q.infoBlockedReason)
        f.land(0, 6)
        assertEquals(ViolationCode.INFO_RENT_TOO_HIGH, f.reject(Action.PayRentInfo(PlayerId(0), cid("S2"))).code)
    }

    @Test
    fun `Kafe Brasileira level 1 = 80 dan dengan Front Feb 1945 = 120`() {
        val f = neutral().own(18, 1).own(19, 1).own(21, 1, level = 1)
        assertEquals(80, f.rent(21).amount)
        assertEquals(120, f.front(9).rent(21).amount)
    }

    @Test
    fun `3 Jalur Pelarian = 100 dan dengan Front Mar 1945 = 200`() {
        val f = neutral().own(5, 1).own(15, 1).own(25, 1)
        assertEquals(100, f.rent(15).amount)
        assertEquals(200, f.front(10).rent(15).amount)
    }

    @Test
    fun `Pesawat Amfibi saat Front Sep 1944 = 0`() {
        assertEquals(0, neutral().own(25, 1).front(4).rent(25).amount)
    }

    @Test
    fun `properti tergadai = 0 dan tidak memicu keputusan sewa`() {
        val f = neutral().own(1, 1, mortgaged = true)
        assertEquals(0, f.rent(1).amount)
        f.land(0, 1)
        assertIs<PendingDecision.BusinessPhase>(f.pending)
    }

    @Test
    fun `properti tak bertuan tidak punya sewa`() {
        assertNull(neutral().engine.computeRent(neutral().state, 1, PlayerId(0)))
    }

    @Test
    fun `Sel Mata-Mata Alfama mewajibkan kartu Lokasi`() {
        val f = neutral()
            .cards(listOf("S1", "L1", "I1"), listOf("L3", "S2"), listOf("S3"), listOf("S4"), listOf("S5"))
            .own(8, 1).own(9, 1).own(11, 1)
        f.land(0, 8)
        val d = assertIs<PendingDecision.RentChoice>(f.pending)
        assertEquals(12, d.quote.amount)
        assertTrue(d.quote.forcedLocation)
        assertEquals(listOf(cid("L3")), d.allowedInfoCards)
        assertEquals(ViolationCode.CARD_NOT_ALLOWED, f.reject(Action.PayRentInfo(PlayerId(0), cid("S2"))).code)
        f.act(Action.PayRentInfo(PlayerId(0), cid("L3")))
        assertTrue(f.state.knowledge[1].any { it.cardId == cid("L3") && it.holder == Holder.Player(PlayerId(0)) })
        assertEquals(listOf(cid("L3"), cid("S2")), f.p(0).hand, "kartu tetap di tangan pembayar")
        assertEquals(1500, f.p(0).money)
        assertEquals(1500, f.p(1).money)
    }

    @Test
    fun `bayar sewa dengan uang memindahkan escudo`() {
        val f = neutral().own(1, 1)
        f.land(0, 1)
        f.act(Action.PayRentMoney(PlayerId(0)))
        assertEquals(1496, f.p(0).money)
        assertEquals(1504, f.p(1).money)
    }

    @Test
    fun `bayar informasi ke-3 dalam satu langkah ditolak lalu diterima setelah Jam maju`() {
        val f = neutral().cards(listOf("S1", "L1", "I1"), listOf("S2", "S3", "S4"), listOf("S5")).own(1, 1)
        repeat(2) { i ->
            f.land(0, 1)
            f.act(Action.PayRentInfo(PlayerId(0), cid(listOf("S2", "S3")[i])))
        }
        assertEquals(2, f.p(0).infoPaymentsThisStep)
        f.land(0, 1)
        assertEquals(ViolationCode.INFO_QUOTA_EXHAUSTED, f.reject(Action.PayRentInfo(PlayerId(0), cid("S4"))).code)

        // Jam Perang maju saat melewati START (39 → 0 → 1), lalu bayar informasi kembali diizinkan.
        f.at(0, 39).turn(0, clockReady = true)
        f.act(Action.RollDice(PlayerId(0)), ScriptedRng(1, 1))
        assertEquals(3, f.state.warStep)
        assertEquals(0, f.p(0).infoPaymentsThisStep)
        val d = assertIs<PendingDecision.RentChoice>(f.pending)
        assertTrue(d.quote.infoAllowed)
        f.act(Action.PayRentInfo(PlayerId(0), cid("S4")))
        assertEquals(1, f.p(0).infoPaymentsThisStep)
    }

    @Test
    fun `house rule kuota 1`() {
        val f = GameStateFixture(quota = 1).front(2).cards(listOf("S1", "L1", "I1"), listOf("S2", "S3")).own(1, 1)
        f.land(0, 1)
        f.act(Action.PayRentInfo(PlayerId(0), cid("S2")))
        f.land(0, 1)
        assertEquals(ViolationCode.INFO_QUOTA_EXHAUSTED, f.reject(Action.PayRentInfo(PlayerId(0), cid("S3"))).code)
    }

    @Test
    fun `tangan kosong tidak bisa bayar informasi`() {
        val f = neutral().cards(listOf("S1", "L1", "I1"), emptyList()).own(1, 1)
        f.land(0, 1)
        assertEquals(ViolationCode.INFO_EMPTY_HAND, f.reject(Action.PayRentInfo(PlayerId(0), cid("S2"))).code)
    }
}
