package id.diran.lisbon1944.engine

import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.HeldCard
import id.diran.lisbon1944.engine.model.JailState
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.WarClockMode
import id.diran.lisbon1944.engine.testing.GameStateFixture
import id.diran.lisbon1944.engine.testing.ScriptedRng
import id.diran.lisbon1944.engine.testing.cid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovementTest {

    private val p0 = PlayerId(0)

    @Test
    fun `lempar dadu dan maju ke petak tak bertuan`() {
        val f = GameStateFixture().front(2).turn(0)
        f.act(Action.RollDice(p0), ScriptedRng(2, 4))
        assertEquals(6, f.p(0).position)
        assertEquals(PendingDecision.BuyOrAuction(p0, 6, 80), f.pending)
    }

    @Test
    fun `melewati START memberi 200 dan OSS 250`() {
        val f = GameStateFixture().front(2).at(0, 39).turn(0)
        f.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(1, f.p(0).position)
        assertEquals(1700, f.p(0).money)
        val g = GameStateFixture(factions = listOf(Faction.OSS, Faction.MI6, Faction.NKVD, Faction.RESISTANCE)).front(2).at(0, 39).turn(0)
        g.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(1750, g.p(0).money)
    }

    @Test
    fun `OSS saat Front Des 1944 tidak menerima gaji`() {
        val f = GameStateFixture(factions = listOf(Faction.OSS, Faction.MI6, Faction.NKVD, Faction.RESISTANCE)).front(7).at(0, 39).turn(0)
        f.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(1500, f.p(0).money)
        assertTrue(f.state.eventLog.any { it.type == GameEvents.SALARY_BLOCKED })
    }

    @Test
    fun `Jam Perang maju sekali per putaran saat pemain pertama melewati START`() {
        val f = GameStateFixture().front(2).at(0, 39).turn(0, clockReady = true)
        f.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(3, f.state.warStep)
        assertTrue(f.state.warClockAdvancedThisRound)
        // Pemain lain melewati START pada putaran yang sama: Jam tidak maju lagi.
        f.at(1, 39).turn(1)
        f.act(Action.RollDice(PlayerId(1)), ScriptedRng(1, 1))
        assertEquals(3, f.state.warStep)
    }

    @Test
    fun `putaran baru membuka kembali Jam Perang`() {
        val f = GameStateFixture(players = 3).front(2).set { copy(firstPlayerIdx = 0, warClockAdvancedThisRound = true) }
            .at(2, 20).business(2)
        f.act(Action.EndTurn(PlayerId(2)))
        assertEquals(false, f.state.warClockAdvancedThisRound)
        assertEquals(2, f.state.roundNumber)
        assertEquals(PendingDecision.AwaitRoll(p0), f.pending)
    }

    @Test
    fun `mode Jam setiap putaran memajukan Front di awal putaran`() {
        val f = GameStateFixture(players = 3).front(2)
            .set { copy(config = config.copy(warClockMode = WarClockMode.EVERY_ROUND), firstPlayerIdx = 0) }
            .business(2)
        f.act(Action.EndTurn(PlayerId(2)))
        assertEquals(3, f.state.warStep)
    }

    @Test
    fun `dadu kembar memberi giliran lagi dan kembar ketiga masuk Penjara`() {
        val f = GameStateFixture().front(2).at(0, 4).turn(0)
        f.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(6, f.p(0).position)
        f.act(Action.DeclineBuy(p0))
        repeat(4) { f.act(Action.PassBid(f.pending!!.decider)) }
        f.act(Action.EndTurn(p0))
        assertEquals(PendingDecision.AwaitRoll(p0), f.pending)
        assertEquals(1, f.state.doublesCount)
        f.act(Action.RollDice(p0), ScriptedRng(2, 2))
        assertEquals(10, f.p(0).position)
        assertEquals(false, f.p(0).inJail, "berhenti di petak Penjara hanya berkunjung")
        f.act(Action.EndTurn(p0))
        f.act(Action.RollDice(p0), ScriptedRng(3, 3))
        assertTrue(f.p(0).inJail)
        assertEquals(10, f.p(0).position)
        assertIs<PendingDecision.BusinessPhase>(f.pending)
        f.act(Action.EndTurn(p0))
        assertEquals(PendingDecision.AwaitRoll(PlayerId(1)), f.pending)
    }

    @Test
    fun `Ditangkap Polisi Rahasia mengirim ke Penjara tanpa gaji`() {
        val f = GameStateFixture().front(2).at(0, 27).turn(0)
        f.act(Action.RollDice(p0), ScriptedRng(1, 2))
        assertTrue(f.p(0).inJail)
        assertEquals(1500, f.p(0).money)
    }

    @Test
    fun `keluar Penjara dengan denda, kartu, kemampuan, atau kembar`() {
        val fine = GameStateFixture().front(2).player(0) { copy(jail = JailState(), position = 10) }.turn(0)
            .set { copy(pending = PendingDecision.JailChoice(p0, 0)) }
        fine.act(Action.PayJailFine(p0))
        assertEquals(1450, fine.p(0).money)
        assertEquals(PendingDecision.AwaitRoll(p0), fine.pending)

        val card = GameStateFixture().front(2)
            .player(0) { copy(jail = JailState(), position = 10, heldCards = listOf(HeldCard(Deck.CIPHER, 6))) }
            .set { copy(cipherDeck = cipherDeck - 6, pending = PendingDecision.JailChoice(p0, 0), currentPlayerIdx = 0) }
        card.act(Action.UseJailCard(p0))
        assertEquals(6, card.state.cipherDeck.last(), "Surat Bebas kembali ke bawah tumpukan")

        val res = GameStateFixture(factions = listOf(Faction.RESISTANCE, Faction.MI6, Faction.NKVD, Faction.OSS)).front(2)
            .player(0) { copy(jail = JailState(), position = 10) }
            .set { copy(pending = PendingDecision.JailChoice(p0, 0), currentPlayerIdx = 0) }
        res.act(Action.UseResistanceAbility(p0))
        assertTrue(res.p(0).abilityUsed)
        assertEquals(1500, res.p(0).money)

        val dbl = GameStateFixture().front(2).player(0) { copy(jail = JailState(), position = 10) }
            .set { copy(pending = PendingDecision.JailChoice(p0, 0), currentPlayerIdx = 0) }
        dbl.act(Action.RollDice(p0), ScriptedRng(3, 3))
        assertNull(dbl.p(0).jail)
        assertEquals(16, dbl.p(0).position)
        assertEquals(0, dbl.state.doublesCount, "kembar keluar Penjara tidak memberi giliran tambahan")
    }

    @Test
    fun `gagal kembar tiga kali wajib bayar lalu jalan`() {
        val f = GameStateFixture().front(2).player(0) { copy(jail = JailState(2), position = 10) }
            .set { copy(pending = PendingDecision.JailChoice(p0, 2), currentPlayerIdx = 0) }
        f.act(Action.RollDice(p0), ScriptedRng(1, 3))
        assertNull(f.p(0).jail)
        assertEquals(1450, f.p(0).money)
        assertEquals(14, f.p(0).position)
    }

    @Test
    fun `gagal kembar di bawah batas tetap di Penjara`() {
        val f = GameStateFixture().front(2).player(0) { copy(jail = JailState(0), position = 10) }
            .set { copy(pending = PendingDecision.JailChoice(p0, 0), currentPlayerIdx = 0) }
        f.act(Action.RollDice(p0), ScriptedRng(1, 3))
        assertEquals(1, f.p(0).jail!!.turnsServed)
        assertIs<PendingDecision.BusinessPhase>(f.pending)
    }

    @Test
    fun `pemain di Penjara tetap menerima sewa`() {
        val f = GameStateFixture().front(2).own(1, 1).player(1) { copy(jail = JailState(), position = 10) }
        f.land(0, 1)
        f.act(Action.PayRentMoney(p0))
        assertEquals(1504, f.p(1).money)
    }

    @Test
    fun `Dugaan saat di Penjara ditolak`() {
        val f = GameStateFixture().front(2).cards(listOf("S1", "L1", "I1"), listOf("S2")).own(1, 0)
            .player(0) { copy(jail = JailState(), position = 1) }.business(0)
        val r = f.reject(Action.Suggest(p0, listOf(cid("S2")), cid("I2"), cid("L1")))
        assertEquals(ViolationCode.IN_JAIL, r.code)
    }

    @Test
    fun `Pajak Perang dan Uang Suap masuk Brankas Kasino`() {
        val f = GameStateFixture().front(2)
        f.land(0, 4)
        assertEquals(1300, f.p(0).money)
        assertEquals(200, f.state.casinoVault)
        f.land(0, 38)
        assertEquals(1150, f.p(0).money)
        assertEquals(350, f.state.casinoVault)
    }

    @Test
    fun `melintasi Kedutaan menawarkan Tuduhan seharga 100`() {
        val f = GameStateFixture().front(2).at(0, 37).turn(0)
        f.act(Action.RollDice(p0), ScriptedRng(1, 3))
        assertEquals(PendingDecision.AccuseOffer(p0, 100), f.pending)
        assertEquals(39, f.p(0).position)
        f.act(Action.DeclineAccuse(p0))
        assertEquals(1, f.p(0).position)
    }
}
