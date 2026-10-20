package id.diran.lisbon1944.engine

import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.Guess
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.ShowReason
import id.diran.lisbon1944.engine.model.WinType
import id.diran.lisbon1944.engine.testing.GameStateFixture
import id.diran.lisbon1944.engine.testing.ScriptedRng
import id.diran.lisbon1944.engine.testing.cid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CardsAndFrontTest {

    private val p0 = PlayerId(0)
    private val p1 = PlayerId(1)

    private fun table(factions: List<Faction> = GameStateFixture.DEFAULT_FACTIONS.take(4)) =
        GameStateFixture(factions = factions).front(2)
            .cards(listOf("S1", "L1", "I1"), listOf("S2"), listOf("S3", "I3"), listOf("S4", "L4"), listOf("S5", "I5"))

    /** Letakkan kartu [id] di atas tumpukan. */
    private fun GameStateFixture.top(deck: Deck, id: Int) = set {
        when (deck) {
            Deck.CIPHER -> copy(cipherDeck = listOf(id) + (cipherDeck - id))
            Deck.TELEGRAM -> copy(telegramDeck = listOf(id) + (telegramDeck - id))
        }
    }

    @Test
    fun `kartu terpakai kembali ke bawah tumpukan`() {
        val f = table().top(Deck.TELEGRAM, 1)
        f.land(0, 2)
        assertEquals(1600, f.p(0).money)
        assertEquals(1, f.state.telegramDeck.last())
        assertEquals(16, f.state.telegramDeck.size)
    }

    @Test
    fun `Kereta Malam ke jalur bertuan membayar 2 kali sewa jalur`() {
        val f = table().own(15, 1).own(5, 1).top(Deck.CIPHER, 7)
        f.land(0, 7)
        val d = assertIs<PendingDecision.RentChoice>(f.pending)
        assertEquals(15, f.p(0).position)
        assertEquals(100, d.quote.amount, "2 jalur = 50, dikali 2")
        f.act(Action.PayRentMoney(p0))
        assertEquals(1400, f.p(0).money)
    }

    @Test
    fun `Kereta Malam dari Sandi 36 melewati START dan menerima gaji`() {
        val f = table().top(Deck.CIPHER, 7)
        f.land(0, 36)
        assertEquals(PendingDecision.AccuseOffer(p0, 100), f.pending, "melintas Kedutaan")
        f.act(Action.DeclineAccuse(p0))
        assertEquals(15, f.p(0).position)
        assertEquals(1700, f.p(0).money)
        assertEquals(PendingDecision.BuyOrAuction(p0, 15, 200), f.pending)
    }

    @Test
    fun `Penyamaran Terbongkar langsung ke Penjara tanpa gaji`() {
        val f = table().top(Deck.CIPHER, 2)
        f.land(0, 36)
        assertTrue(f.p(0).inJail)
        assertEquals(1500, f.p(0).money)
    }

    @Test
    fun `Kejar-kejaran mundur 3 dan menjalankan petak tujuan`() {
        val f = table().top(Deck.CIPHER, 5)
        f.land(0, 7)
        assertEquals(4, f.p(0).position)
        assertEquals(1300, f.p(0).money, "Pajak Perang 200")
    }

    @Test
    fun `Rapat Agen menagih 20 dari setiap pemain lain`() {
        val f = table().top(Deck.TELEGRAM, 4)
        f.land(0, 2)
        assertEquals(1560, f.p(0).money)
        (1..3).forEach { assertEquals(1480, f.p(it).money) }
    }

    @Test
    fun `Audit Keuangan 10 persen dibulatkan ke bawah`() {
        val f = table().money(0, 1239).top(Deck.TELEGRAM, 15)
        f.land(0, 2)
        assertEquals(1239 - 123, f.p(0).money)
        assertEquals(123, f.state.casinoVault)
    }

    @Test
    fun `Surat Bebas Diplomatik disimpan lalu dipakai`() {
        val f = table().top(Deck.CIPHER, 6)
        f.land(0, 7)
        assertEquals(1, f.p(0).heldCards.size)
        assertEquals(15, f.state.cipherDeck.size)
    }

    @Test
    fun `Kurir Tertangkap menunjukkan kartu pilihan ke kiri`() {
        val f = table().top(Deck.CIPHER, 12)
        f.land(0, 7)
        assertEquals(PendingDecision.ShowCardTo(p0, p1, listOf(cid("S2")), ShowReason.COURIER), f.pending)
        f.act(Action.ChooseCardToShow(p0, cid("S2")))
        assertTrue(f.state.knowledge[1].any { it.cardId == cid("S2") && it.source == KnowledgeSource.COURIER })
    }

    @Test
    fun `Laporan Intelijen pemain kanan menunjukkan Tersangka`() {
        val f = table().top(Deck.TELEGRAM, 8)
        f.land(0, 2)
        assertEquals(PendingDecision.ShowCardTo(PlayerId(3), p0, listOf(cid("S5")), ShowReason.INTELLIGENCE_REPORT), f.pending)
    }

    @Test
    fun `Telegram Terbuka hanya pemain yang punya Barang`() {
        val f = table().top(Deck.TELEGRAM, 3)
        f.land(0, 2)
        assertEquals(PendingDecision.ShowCardTo(p1, p0, listOf(cid("I3")), ShowReason.OPEN_TELEGRAM), f.pending)
        f.act(Action.ChooseCardToShow(p1, cid("I3")))
        assertEquals(PendingDecision.ShowCardTo(PlayerId(3), p0, listOf(cid("I5")), ShowReason.OPEN_TELEGRAM), f.pending)
        f.act(Action.ChooseCardToShow(PlayerId(3), cid("I5")))
        assertTrue(f.state.eventLog.any { it.type == GameEvents.NO_CARD && it.payload["player"] == "2" })
    }

    @Test
    fun `Penyadapan memilih pemain terkaya dan bila seri penarik memilih`() {
        val f = table().money(2, 3000).top(Deck.CIPHER, 14)
        f.land(0, 7)
        assertTrue(f.state.knowledge[0].any { it.source == KnowledgeSource.WIRETAP && it.cardId == cid("S4") })
        val g = table().money(2, 3000).money(3, 3000).top(Deck.CIPHER, 14)
        g.land(0, 7)
        assertEquals(PendingDecision.ChooseWiretapTarget(p0, listOf(PlayerId(2), PlayerId(3))), g.pending)
        g.act(Action.ChooseWiretapTarget(p0, PlayerId(3)))
        assertTrue(g.state.knowledge[0].any { it.source == KnowledgeSource.WIRETAP && it.cardId == cid("S5") })
    }

    @Test
    fun `Jurnalis Swiss mengambil 2 Telegram, memilih 1, sisanya ke bawah`() {
        val f = table(listOf(Faction.SWISS_JOURNALIST, Faction.MI6, Faction.NKVD, Faction.OSS)).top(Deck.TELEGRAM, 9).top(Deck.TELEGRAM, 1)
        f.land(0, 2)
        assertEquals(PendingDecision.TelegramKeepChoice(p0, listOf(1, 9)), f.pending)
        assertEquals(emptyList(), f.engine.checkInvariants(f.state))
        f.act(Action.ChooseTelegram(p0, 1))
        assertEquals(1600, f.p(0).money)
        assertEquals(listOf(9, 1), f.state.telegramDeck.takeLast(2))
    }

    @Test
    fun `Rekrut Informan memberi Dugaan bebas pada giliran berikutnya`() {
        val f = table().top(Deck.TELEGRAM, 16)
        f.land(0, 2)
        assertEquals(PendingDecision.SuggestionOffer(p0, freeLocation = true), f.pending, "berlaku mulai giliran ini")
        f.act(Action.SkipSuggestion(p0))
        assertEquals(4, f.p(0).extraSuggestionTurnsLeft)
    }

    // ---------------------------------------------------------------------------------
    // Front
    // ---------------------------------------------------------------------------------

    private fun passStartWithClock(f: GameStateFixture, player: Int = 0) {
        f.at(player, 39).turn(player, clockReady = true)
        f.act(Action.RollDice(PlayerId(player)), ScriptedRng(1, 1))
    }

    @Test
    fun `Paris Dibebaskan memberi Perlawanan 150 dan Dugaan gratis`() {
        val f = table(listOf(Faction.MI6, Faction.RESISTANCE, Faction.NKVD, Faction.OSS))
        passStartWithClock(f)
        assertEquals(3, f.state.warStep)
        assertEquals(1650, f.p(1).money)
        f.state.players.forEach { assertEquals(1, it.freeSuggestions) }
    }

    @Test
    fun `Harga Wolfram Anjlok menagih pemilik Estoril`() {
        val f = table().front(1).own(31, 2)
        passStartWithClock(f)
        assertEquals(2, f.state.warStep)
        assertEquals(1450, f.p(2).money)
    }

    @Test
    fun `Razia Polisi Rahasia memenjarakan pemain ber-uang di atas 1500`() {
        val f = table().front(4).money(2, 1501).money(3, 1500)
        passStartWithClock(f)
        assertEquals(5, f.state.warStep)
        assertTrue(f.p(2).inJail)
        assertTrue(!f.p(3).inJail)
        assertTrue(!f.p(0).inJail, "gaji diterima setelah Razia — penggerak tidak terjebak oleh gajinya sendiri")
        assertEquals(1700, f.p(0).money)
    }

    @Test
    fun `Konferensi Rahasia setiap pemain menunjukkan kartu ke kiri`() {
        val f = table().front(7)
        passStartWithClock(f)
        assertEquals(8, f.state.warStep)
        val order = mutableListOf<Pair<Int, Int>>()
        while (f.pending is PendingDecision.ShowCardTo) {
            val d = f.pending as PendingDecision.ShowCardTo
            order += d.decider.value to d.target.value
            f.act(Action.ChooseCardToShow(d.decider, d.allowedCards.first()))
        }
        assertEquals(listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0), order)
        assertEquals(PendingDecision.BuyOrAuction(p0, 1, 60), f.pending)
    }

    @Test
    fun `Perang Usai mengakhiri permainan dengan skor dan tebakan akhir`() {
        val f = table().front(11)
        passStartWithClock(f)
        assertEquals(12, f.state.warStep)
        repeat(4) { i ->
            assertEquals(PendingDecision.FinalGuess(PlayerId(i)), f.pending)
            val guess = if (i == 1) Guess(cid("S1"), cid("L1"), cid("I1")) else Guess(cid("S2"), cid("L2"), cid("I2"))
            f.act(Action.SubmitFinalGuess(PlayerId(i), guess.suspect, guess.location, guess.item))
        }
        val w = f.state.winner!!
        assertEquals(WinType.WAR_END, w.type)
        assertEquals(listOf(p1), w.winners, "3 tebakan benar = +150")
        val scores = f.engine.computeScores(f.state)
        assertEquals(150 + 150, scores[1].total)
        assertEquals(150, scores[0].total, "uang 1500 / 10 — tidak ada gaji setelah perang usai")
        assertEquals(12, f.state.warStep)
    }

    @Test
    fun `skor akhir menghitung properti bangunan dan Terbakar`() {
        val f = table().money(0, 1500).own(1, 0, level = 5).own(3, 0, level = 4).own(6, 0, level = 4).own(13, 0, mortgaged = true)
            .player(1) { copy(burned = true, money = 1000) }
            .player(2) { copy(finalGuess = Guess(cid("S1"), cid("L2"), cid("I1"))) }
        val s = f.engine.computeScores(f.state)
        // Uang 150; properti (60+60+80+70)/10 = 27; bangunan 25 + 4×5 + 4×5 = 65.
        assertEquals(150 + 27 + 65, s[0].total)
        assertEquals(50, s[1].total, "Terbakar: (100) × 0,5")
        assertEquals(150 + 100, s[2].total, "2 tebakan benar")
    }

    @Test
    fun `seri skor dipecah oleh petunjuk lalu uang`() {
        val f = table().front(11)
            .player(0) { copy(money = 1300, finalGuess = Guess(cid("S1"), cid("L6"), cid("I6"))) }
            .player(1) { copy(money = 1800) }
            .player(2) { copy(money = 100) }.player(3) { copy(money = 100) }
        passStartWithClock(f, 0)
        while (f.pending is PendingDecision.FinalGuess) {
            val d = f.pending as PendingDecision.FinalGuess
            f.act(Action.SubmitFinalGuess(d.decider, cid("S6"), cid("L6"), cid("I6")))
        }
        // P0: 1300/10 + 50 = 180; P1: 1800/10 + 0 = 180 → seri, dipecah petunjuk: P0 menang.
        assertEquals(listOf(180, 180), f.engine.computeScores(f.state).take(2).map { it.total })
        assertEquals(listOf(p0), f.state.winner!!.winners)
    }
}
