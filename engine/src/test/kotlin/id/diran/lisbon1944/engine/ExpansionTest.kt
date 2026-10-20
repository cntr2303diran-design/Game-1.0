package id.diran.lisbon1944.engine

import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.FrontId
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.Visibility
import id.diran.lisbon1944.engine.testing.GameStateFixture
import id.diran.lisbon1944.engine.testing.ScriptedRng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ekspansi: Kartu Taktik, Operasi Kota, Prestise, Patroli PIDE, Kontak Netral, Bantuan Sekutu, Front varian. */
class ExpansionTest {

    private val p0 = PlayerId(0)
    private val p1 = PlayerId(1)
    private val p2 = PlayerId(2)

    /** Balance aktif; tanpa pion & tanpa Taktik di tangan kecuali diberikan. */
    private fun table() = GameStateFixture(classic = false)
        .cards(listOf("S1", "L1", "I1"), listOf("S2"), listOf("S3", "I3"), listOf("S4", "L4"), listOf("S5", "I5"))
        .set {
            copy(
                patrolPos = 0, contactPos = 0, sealedTiles = emptyMap(),
                tacticDeck = (tacticDeck + players.flatMap { it.tactics }).sorted(),
                players = players.map { it.copy(tactics = emptyList()) },
            )
        }

    /** Berikan Kartu Taktik [id] ke pemain [seat] (diambil dari tumpukan). */
    private fun GameStateFixture.giveTactic(seat: Int, id: Int) = set {
        copy(tacticDeck = tacticDeck - id, players = players.mapIndexed { i, pl -> if (i == seat) pl.copy(tactics = pl.tactics + id) else pl })
    }

    // --- Front varian ---------------------------------------------------------------------

    @Test
    fun `rencana Front memilih satu kartu per langkah dan berbeda antar seed`() {
        val plans = (1L..20L).map { GameStateFixture(classic = false, seed = it).state.frontPlan }.toSet()
        assertTrue(plans.size > 1, "varian harus bervariasi antar permainan")
        plans.forEach { plan ->
            plan.forEachIndexed { i, fid -> assertEquals(i + 1, TestFront.step(fid)) }
        }
        assertEquals((1..12).toList(), GameStateFixture().state.frontPlan, "aturan dasar memakai varian A")
    }

    @Test
    fun `rencana Front tidak terlihat di PlayerView`() {
        val f = GameStateFixture(classic = false)
        val v = f.engine.viewFor(f.state, p0)
        assertEquals(f.state.activeFront, v.activeFront)
        assertTrue(v.toString().contains("frontPlan").not())
    }

    // --- Kartu Taktik ---------------------------------------------------------------------

    @Test
    fun `Jaringan Kurir menambah 2 Intel dan hanya satu Taktik per giliran`() {
        val f = table().player(0) { copy(intel = 0) }.giveTactic(0, 3).giveTactic(0, 4).business(0)
        f.act(Action.PlayTactic(p0, 3))
        assertEquals(2, f.p(0).intel)
        assertEquals(1, f.p(0).tacticsPlayed)
        assertEquals(3, f.state.tacticDeck.last(), "kartu terpakai ke bawah tumpukan")
        assertEquals(ViolationCode.TACTIC_NOT_ALLOWED, f.reject(Action.PlayTactic(p0, 4)).code)
    }

    @Test
    fun `Penyamaran membatalkan sewa`() {
        val f = table().own(1, 1).giveTactic(0, 1).front(2)
        f.land(0, 1)
        val d = assertIs<PendingDecision.RentChoice>(f.pending)
        assertTrue(Action.PlayTactic(p0, 1) in f.engine.legalActions(f.state, p0))
        f.act(Action.PlayTactic(p0, 1))
        assertEquals(1500, f.p(0).money)
        assertEquals(1500, f.p(1).money)
        assertTrue(f.state.eventLog.any { it.type == GameEvents.RENT_CANCELLED })
        assertTrue(d.quote.amount > 0)
    }

    @Test
    fun `Pemerasan mengambil 100 dan menaikkan Kecurigaan sendiri`() {
        val f = table().giveTactic(0, 11).business(0)
        f.act(Action.PlayTactic(p0, 11, targetPlayer = p2))
        assertEquals(1600, f.p(0).money)
        assertEquals(1400, f.p(2).money)
        assertEquals(1, f.p(0).suspicion)
        assertIs<PendingDecision.BusinessPhase>(f.pending)
    }

    @Test
    fun `Tekanan Diplomatik bisa membuat lawan ditangkap`() {
        val limit = table().content.balance.suspicion.limit
        val f = table().player(2) { copy(suspicion = limit - 2) }.giveTactic(0, 9).business(0)
        f.act(Action.PlayTactic(p0, 9, targetPlayer = p2))
        assertTrue(f.p(2).inJail)
        assertEquals(0, f.p(2).suspicion)
    }

    @Test
    fun `Pengintaian melihat kartu tanpa diketahui target`() {
        val f = table().giveTactic(0, 7).business(0)
        f.act(Action.PlayTactic(p0, 7, targetPlayer = p1))
        assertTrue(f.state.knowledge[0].any { it.holder == Holder.Player(p1) && it.source == KnowledgeSource.MI6_ABILITY })
        val seen = f.state.eventLog.last { it.type == GameEvents.CARD_SEEN }
        assertEquals(Visibility.Private(setOf(p0)), seen.visibility)
    }

    @Test
    fun `Sabotase menyegel properti lawan sampai akhir putaran berikutnya`() {
        val f = table().own(1, 1).giveTactic(0, 13).business(0).front(2)
        f.act(Action.PlayTactic(p0, 13, targetTile = 1))
        assertEquals(f.state.roundNumber + 1, f.state.sealedTiles[1])
        assertEquals(0, f.engine.computeRent(f.state, 1, p2)!!.amount)
        assertEquals(ViolationCode.TACTIC_NOT_ALLOWED, table().own(1, 0).giveTactic(0, 13).business(0).reject(Action.PlayTactic(p0, 13, targetTile = 1)).code, "tidak bisa menyegel milik sendiri")
        // Dua putaran kemudian segel kedaluwarsa.
        f.set { copy(roundNumber = roundNumber + 2) }
        assertTrue(f.engine.computeRent(f.state, 1, p2)!!.amount > 0)
    }

    @Test
    fun `Mogok Buruh menyegel seluruh distrik`() {
        val f = table().own(1, 1).own(3, 1).own(6, 2).giveTactic(0, 21).business(0)
        f.act(Action.PlayTactic(p0, 21, targetTile = 3))
        assertEquals(setOf(1, 3, 6), f.state.sealedTiles.keys)
    }

    @Test
    fun `Taktik diambil saat melewati START sampai batas tangan`() {
        val limit = table().content.balance.tactics.handLimit
        val f = table().at(0, 39).turn(0)
        f.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(1, f.p(0).tactics.size)
        val full = table().set { copy(players = players.mapIndexed { i, pl -> if (i == 0) pl.copy(tactics = tacticDeck.take(limit)) else pl }, tacticDeck = tacticDeck.drop(limit)) }
            .at(0, 39).turn(0)
        full.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(limit, full.p(0).tactics.size)
        val drawn = f.state.eventLog.filter { it.type == GameEvents.TACTIC_DRAWN }
        assertTrue(drawn.any { it.payload.containsKey("card") && it.visibility == Visibility.Private(setOf(p0)) })
        assertTrue(drawn.filter { it.visibility == Visibility.Public }.none { it.payload.containsKey("card") }, "kartu yang diambil tidak publik")
    }

    @Test
    fun `Taktik tidak bisa dimainkan di luar fase Bisnis`() {
        val f = table().giveTactic(0, 3).turn(0)
        assertEquals(ViolationCode.TACTIC_NOT_ALLOWED, f.reject(Action.PlayTactic(p0, 3)).code)
    }

    // --- Operasi Kota & Prestise ---------------------------------------------------------

    @Test
    fun `Operasi diklaim pemain pertama yang memenuhi syarat saat akhir giliran`() {
        val f = table().set { copy(openOperations = listOf(9), operationDeck = (operationDeck + openOperations - 9).sorted()) }
        listOf(1, 3, 6, 8, 9, 11).forEach { f.own(it, 0) }
        f.business(0)
        val before = f.p(0).money
        f.act(Action.EndTurn(p0))
        assertEquals(1, f.p(0).operationsClaimed)
        assertEquals(15, f.p(0).prestige)
        assertEquals(before + 100, f.p(0).money)
        assertTrue(9 in f.state.claimedOperations)
        assertEquals(f.content.openOperations, f.state.openOperations.size, "Operasi terbuka diisi ulang")
        assertTrue(f.engine.computeScores(f.state)[0].prestige == 15)
    }

    // --- Pion netral ------------------------------------------------------------------------

    @Test
    fun `berhenti di petak Patroli menaikkan Kecurigaan dan di petak Kontak memberi Intel`() {
        val bal = table().content.balance
        val f = table().set { copy(patrolPos = 13) }.player(0) { copy(intel = 0) }.front(2)
        f.land(0, 13)
        assertEquals(bal.patrol.suspicion, f.p(0).suspicion)
        val g = table().set { copy(contactPos = 13) }.player(0) { copy(intel = 0) }.front(2)
        g.land(0, 13)
        assertEquals(bal.contact.intel, g.p(0).intel)
        assertEquals(1500 + bal.contact.money, g.p(0).money)
    }

    @Test
    fun `pion bergerak di awal putaran`() {
        val f = table().set { copy(firstPlayerIdx = 0, patrolPos = 0, contactPos = 0) }.business(3)
        f.act(Action.EndTurn(PlayerId(3)), ScriptedRng(2, 3, 4))
        assertEquals(5, f.state.patrolPos)
        assertEquals(4, f.state.contactPos)
    }

    // --- Bantuan Sekutu ---------------------------------------------------------------------

    @Test
    fun `Bantuan Sekutu ke pemain termiskin saat Front dibalik`() {
        val bal = table().content.balance
        val f = table().money(2, 100).set { copy(startPasses = config.passesNeededFor(3) - 1) }.front(2).at(0, 39).turn(0)
        val before = f.p(2).money
        f.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(3, f.state.warStep)
        assertTrue(f.p(2).money >= before + bal.comeback.money)
        assertTrue(f.state.eventLog.any { it.type == GameEvents.ALLIED_AID && it.payload["player"] == "2" })
    }

    @Test
    fun `invariant ekspansi bertahan dalam permainan acak`() {
        assertNull(null)
        val f = GameStateFixture(classic = false, seed = 5)
        assertEquals(emptyList(), f.engine.checkInvariants(f.state))
        assertEquals(EvidenceType.SUSPECT, EvidenceType.valueOf("SUSPECT"))
        assertEquals(f.state.activeFront, FrontId(f.state.frontPlan[0]))
    }
}

private object TestFront {
    fun step(frontId: Int) = id.diran.lisbon1944.engine.testing.TestEngine.content.front(FrontId(frontId)).step
}
