package id.diran.lisbon1944.engine

import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.GameMode
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.SuggestionSource
import id.diran.lisbon1944.engine.model.WinType
import id.diran.lisbon1944.engine.testing.GameStateFixture
import id.diran.lisbon1944.engine.testing.ScriptedRng
import id.diran.lisbon1944.engine.testing.cid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MysteryTest {

    private val p0 = PlayerId(0)
    private val p1 = PlayerId(1)
    private val p2 = PlayerId(2)
    private val p3 = PlayerId(3)

    /** Amplop S1/L1/I1. Tangan: P0 [S2], P1 [S3,I3], P2 [S4,L4], P3 [S5,I5]. */
    private fun table(factions: List<Faction> = GameStateFixture.DEFAULT_FACTIONS.take(4)) =
        GameStateFixture(factions = factions).front(2)
            .cards(listOf("S1", "L1", "I1"), listOf("S2"), listOf("S3", "I3"), listOf("S4", "L4"), listOf("S5", "I5"))

    @Test
    fun `Dugaan berhenti di pemain pertama yang punya kartu`() {
        val f = table()
        f.land(0, 17)
        assertEquals(PendingDecision.SuggestionOffer(p0, freeLocation = true, source = SuggestionSource.INTERROGATION), f.pending)
        f.act(Action.Suggest(p0, listOf(cid("S4")), cid("I5"), cid("L6")))
        // P1 tidak punya S4/I5/L6 → lewat. P2 punya S4 → wajib menunjukkan.
        val reveal = assertIs<PendingDecision.RevealForSuggestion>(f.pending)
        assertEquals(p2, reveal.decider)
        assertEquals(listOf(cid("S4")), reveal.allowedCards)
        f.act(Action.RevealCard(p2, cid("S4")))
        assertTrue(f.state.knowledge[0].any { it.cardId == cid("S4") && it.holder == Holder.Player(p2) && it.source == KnowledgeSource.SUGGESTION })
        assertTrue(f.state.knowledge[3].none { it.cardId == cid("S4") }, "P3 tidak ditanya dan tidak tahu kartunya")
        val seen = f.state.eventLog.last { it.type == GameEvents.CARD_SEEN }
        assertTrue(seen.visibility.visibleTo(p0) && seen.visibility.visibleTo(p2))
        assertTrue(!seen.visibility.visibleTo(p1) && !seen.visibility.visibleTo(p3))
        assertTrue(f.state.eventLog.any { it.type == GameEvents.SUGGESTION_PASS && it.payload["player"] == "1" })
        assertIs<PendingDecision.BusinessPhase>(f.pending)
    }

    @Test
    fun `Dugaan tanpa kartu di siapa pun memberi 100 dari bank`() {
        val f = table()
        f.land(0, 17)
        f.act(Action.Suggest(p0, listOf(cid("S1")), cid("I1"), cid("L1")))
        assertEquals(1600, f.p(0).money)
        assertTrue(f.state.eventLog.any { it.type == GameEvents.SUGGESTION_STRONG })
    }

    @Test
    fun `menunjukkan kartu yang tidak disebut ditolak`() {
        val f = table()
        f.land(0, 17)
        f.act(Action.Suggest(p0, listOf(cid("S3")), cid("I3"), cid("L6")))
        assertEquals(ViolationCode.CARD_NOT_ALLOWED, f.reject(Action.RevealCard(p1, cid("S2"))).code)
        f.act(Action.RevealCard(p1, cid("I3")))
    }

    @Test
    fun `Dugaan dari properti sendiri memakai Lokasi distrik itu`() {
        val f = table().own(13, 0)
        f.land(0, 13)
        assertEquals(PendingDecision.SuggestionOffer(p0, freeLocation = false, fixedLocation = cid("L3")), f.pending)
        assertEquals(ViolationCode.LOCATION_NOT_ALLOWED, f.reject(Action.Suggest(p0, listOf(cid("S2")), cid("I2"), cid("L4"))).code)
        f.act(Action.Suggest(p0, listOf(cid("S2")), cid("I2"), cid("L3")))
        assertTrue(f.state.propertySuggestionUsed)
    }

    @Test
    fun `Kode Dibakar melarang Dugaan pada giliran berikutnya`() {
        val f = table().own(13, 0).player(0) { copy(suggestionBlockedThisTurn = true) }
        f.land(0, 13)
        assertIs<PendingDecision.BusinessPhase>(f.pending, "tidak ada tawaran Dugaan")
        assertEquals(ViolationCode.SUGGESTION_BLOCKED, f.reject(Action.Suggest(p0, listOf(cid("S2")), cid("I2"), cid("L3"))).code)
    }

    @Test
    fun `NKVD menyebut 2 tersangka sekali per game`() {
        val f = table(listOf(Faction.NKVD, Faction.MI6, Faction.RESISTANCE, Faction.OSS))
        f.land(0, 17)
        f.act(Action.Suggest(p0, listOf(cid("S5"), cid("S4")), cid("I6"), cid("L6")))
        assertTrue(f.p(0).abilityUsed)
        assertEquals(listOf(cid("S4")), assertIs<PendingDecision.RevealForSuggestion>(f.pending).allowedCards)
        f.act(Action.RevealCard(p2, cid("S4")))
        f.land(0, 34)
        assertEquals(ViolationCode.ABILITY_UNAVAILABLE, f.reject(Action.Suggest(p0, listOf(cid("S4"), cid("S5")), cid("I6"), cid("L6"))).code)
        val g = table()
        g.land(0, 17)
        assertEquals(ViolationCode.ABILITY_UNAVAILABLE, g.reject(Action.Suggest(p0, listOf(cid("S4"), cid("S5")), cid("I6"), cid("L6"))).code)
    }

    @Test
    fun `MI6 melihat kartu acak secara rahasia sekali per game`() {
        val f = table().business(0)
        f.act(Action.UseMi6Ability(p0, p1))
        assertTrue(f.p(0).abilityUsed)
        val fact = f.state.knowledge[0].single { it.source == KnowledgeSource.MI6_ABILITY }
        assertEquals(cid("S3"), fact.cardId, "ScriptedRng memilih kartu pertama")
        val seen = f.state.eventLog.last { it.type == GameEvents.CARD_SEEN }
        assertEquals(setOf(p0), (seen.visibility as id.diran.lisbon1944.engine.model.Visibility.Private).playerIds, "target tidak tahu kartu mana")
        assertTrue(f.state.eventLog.any { it.type == GameEvents.ABILITY_USED && it.payload["target"] == "1" })
        assertEquals(ViolationCode.ABILITY_UNAVAILABLE, f.reject(Action.UseMi6Ability(p0, p2)).code)
    }

    @Test
    fun `Dekode berhasil pada lemparan pertama`() {
        val f = table()
        f.land(0, 12)
        val pick = assertIs<PendingDecision.DecodeOperatorPick>(f.pending)
        assertEquals(p3, pick.decider, "operator = pemain di kanan")
        f.act(Action.DecodePickNumber(p3, 4), ScriptedRng(4))
        assertTrue(f.state.knowledge[0].any { it.cardId == cid("S5") && it.source == KnowledgeSource.DECODE })
        assertNull(f.state.decode)
    }

    @Test
    fun `Dekode gagal setelah 2 lemparan tambahan uangnya ke operator`() {
        val f = table()
        f.land(0, 28)
        f.act(Action.DecodePickNumber(p3, 6), ScriptedRng(1))
        assertEquals(PendingDecision.DecodeContinue(p0, 0, 0), f.pending)
        f.act(Action.DecodeRollExtra(p0), ScriptedRng(2))
        assertEquals(1475, f.p(0).money)
        f.act(Action.DecodeRollExtra(p0), ScriptedRng(3))
        assertEquals(1450, f.p(0).money)
        assertEquals(1550, f.p(3).money)
        assertTrue(f.state.knowledge[0].none { it.source == KnowledgeSource.DECODE })
    }

    @Test
    fun `Dekode berhasil di lemparan tambahan uangnya ke bank`() {
        val f = table()
        f.land(0, 12)
        f.act(Action.DecodePickNumber(p3, 6), ScriptedRng(1))
        f.act(Action.DecodeRollExtra(p0), ScriptedRng(6))
        assertEquals(1475, f.p(0).money)
        assertEquals(1500, f.p(3).money)
    }

    @Test
    fun `Dekode berhasil ke operator bertangan kosong memberi 50`() {
        val f = table().player(3) { copy(hand = emptyList()) }
            .set { copy(embassyCards = embassyCards + listOf(cid("S5"), cid("I5"))) }
        f.land(0, 12)
        f.act(Action.DecodePickNumber(p3, 2), ScriptedRng(2))
        assertEquals(1550, f.p(0).money)
    }

    @Test
    fun `Tuduhan benar di Kedutaan = kemenangan Detektif`() {
        val f = table()
        f.land(0, 39)
        assertEquals(PendingDecision.AccuseOffer(p0, 0), f.pending)
        f.act(Action.Accuse(p0, cid("S1"), cid("L1"), cid("I1")))
        assertEquals(WinType.DETECTIVE, f.state.winner!!.type)
        assertEquals(listOf(p0), f.state.winner!!.winners)
        assertTrue(f.engine.legalActions(f.state, p1).isEmpty())
        assertEquals(ViolationCode.GAME_OVER, f.reject(Action.EndTurn(p1)).code)
    }

    @Test
    fun `Tuduhan salah = Terbakar, denda 50 persen ke Brankas, kartu tetap`() {
        val f = table().money(0, 1201)
        f.land(0, 39)
        f.act(Action.Accuse(p0, cid("S2"), cid("L1"), cid("I1")))
        assertTrue(f.p(0).burned)
        assertEquals(601, f.p(0).money)
        assertEquals(600, f.state.casinoVault)
        assertEquals(listOf(cid("S2")), f.p(0).hand)
        assertNull(f.state.winner)
        assertTrue(f.state.knowledge[0].count { it.holder == Holder.Envelope } == 3, "Terbakar melihat isi amplop")
        val wrong = f.state.eventLog.last { it.type == GameEvents.ACCUSATION_WRONG }
        assertTrue(wrong.visibility.visibleTo(p0) && !wrong.visibility.visibleTo(p1))
        assertEquals(f.state.envelope, f.engine.viewFor(f.state, p0).revealedEnvelope)
        assertNull(f.engine.viewFor(f.state, p1).revealedEnvelope)
        // Terbakar tetap menjawab Dugaan.
        f.land(1, 17)
        f.act(Action.Suggest(p1, listOf(cid("S2")), cid("I6"), cid("L6")))
        assertEquals(p0, f.pending!!.decider)
        f.act(Action.RevealCard(p0, cid("S2")))
        // Terbakar tidak bisa menuduh lagi.
        f.land(0, 39)
        assertIs<PendingDecision.BusinessPhase>(f.pending)
    }

    @Test
    fun `Tuduhan April 1945 dari mana pun seharga 300`() {
        val f = table().front(11).business(0)
        f.act(Action.Accuse(p0, cid("S1"), cid("L1"), cid("I1")))
        assertEquals(1200, f.p(0).money)
        assertEquals(WinType.DETECTIVE, f.state.winner!!.type)
        assertEquals(ViolationCode.ACCUSE_NOT_ALLOWED, table().business(0).reject(Action.Accuse(p0, cid("S1"), cid("L1"), cid("I1"))).code)
    }

    @Test
    fun `mode keluarga tuduhan salah denda 200 tanpa Terbakar`() {
        val f = GameStateFixture(mode = GameMode.FAMILY).front(2)
            .cards(listOf("S1", "L1", "I1"), listOf("S2"), listOf("S3"), listOf("S4"), listOf("S5"))
        f.land(0, 39)
        f.act(Action.Accuse(p0, cid("S2"), cid("L1"), cid("I1")))
        assertEquals(false, f.p(0).burned)
        assertEquals(1300, f.p(0).money)
        assertEquals(200, f.state.casinoVault)
    }

    @Test
    fun `Kasino 7 menang 3 kali plus seluruh Brankas`() {
        val f = table().set { copy(casinoVault = 400) }
        f.land(0, 20)
        f.act(Action.CasinoBet(p0, 100), ScriptedRng(3, 4))
        assertEquals(1500 - 100 + 300 + 400, f.p(0).money)
        assertEquals(0, f.state.casinoVault)
    }

    @Test
    fun `Kasino kalah masuk Brankas dan kembar dibayar 2 kali`() {
        val f = table()
        f.land(0, 20)
        f.act(Action.CasinoBet(p0, 50), ScriptedRng(1, 2))
        assertEquals(1450, f.p(0).money)
        assertEquals(50, f.state.casinoVault)
        f.land(0, 20)
        f.act(Action.CasinoBet(p0, 50), ScriptedRng(2, 2))
        assertEquals(1500, f.p(0).money)
        assertEquals(ViolationCode.INVALID_AMOUNT, run { f.land(0, 20); f.reject(Action.CasinoBet(p0, 55)) }.code)
    }

    @Test
    fun `Pasar Gelap 150 ke penjual yang memilih kartunya`() {
        val f = table()
        f.land(0, 29)
        f.act(Action.BlackMarketBuy(p0, p2))
        assertEquals(1350, f.p(0).money)
        assertEquals(1650, f.p(2).money)
        assertEquals(PendingDecision.BlackMarketPickCard(p2, p0, listOf(cid("S4"), cid("L4"))), f.pending)
        f.act(Action.BlackMarketChooseCard(p2, cid("L4")))
        assertTrue(f.state.knowledge[0].any { it.cardId == cid("L4") && it.source == KnowledgeSource.BLACK_MARKET })
    }

    @Test
    fun `mode keluarga menonaktifkan Kasino dan Pasar Gelap`() {
        val f = GameStateFixture(mode = GameMode.FAMILY).front(2)
        f.land(0, 20)
        assertIs<PendingDecision.BusinessPhase>(f.pending)
        f.land(0, 29)
        assertIs<PendingDecision.BusinessPhase>(f.pending)
    }

    @Test
    fun `Agen Ganda boleh berbohong sekali dan tercatat`() {
        val f = table().player(1) { copy(isDoubleAgent = true) }.set { copy(config = config.copy(doubleAgentVariant = true)) }
        f.land(0, 17)
        f.act(Action.Suggest(p0, listOf(cid("S3")), cid("I6"), cid("L6")))
        f.act(Action.RevealCard(p1, cid("I3")))
        assertTrue(f.p(1).doubleAgentLieUsed)
        assertEquals(1, f.state.doubleAgentLies.size)
        val lie = f.state.eventLog.last { it.type == GameEvents.DOUBLE_AGENT_LIE }
        assertTrue(lie.visibility.visibleTo(p1) && !lie.visibility.visibleTo(p0))
        f.land(0, 34)
        f.act(Action.Suggest(p0, listOf(cid("S3")), cid("I6"), cid("L6")))
        assertEquals(ViolationCode.CARD_NOT_ALLOWED, f.reject(Action.RevealCard(p1, cid("I3"))).code)
    }
}
