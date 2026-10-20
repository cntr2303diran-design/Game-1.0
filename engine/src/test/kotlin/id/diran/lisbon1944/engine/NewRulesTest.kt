package id.diran.lisbon1944.engine

import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.Guess
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.SuggestionSource
import id.diran.lisbon1944.engine.model.Visibility
import id.diran.lisbon1944.engine.model.WinType
import id.diran.lisbon1944.engine.testing.GameStateFixture
import id.diran.lisbon1944.engine.testing.ScriptedRng
import id.diran.lisbon1944.engine.testing.cid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Aturan & elemen tambahan: Intel, Bungkam, Kecurigaan, Misi Rahasia, faksi baru, Map Merah berpindah, Jam berbasis START. */
class NewRulesTest {

    private val p0 = PlayerId(0)
    private val p1 = PlayerId(1)
    private val p2 = PlayerId(2)

    /** Balance saat ini (bukan aturan dasar). Amplop S1/L1/I1; P0 [S2], P1 [S3,I3], P2 [S4,L4], P3 [S5,I5]. */
    private fun table(factions: List<Faction> = GameStateFixture.DEFAULT_FACTIONS.take(4)) =
        GameStateFixture(factions = factions, classic = false).front(2)
            .cards(listOf("S1", "L1", "I1"), listOf("S2"), listOf("S3", "I3"), listOf("S4", "L4"), listOf("S5", "I5"))

    // --- Intel ---------------------------------------------------------------------------

    @Test
    fun `Intel awal dari balance dan Pembelot Abwehr mendapat bonus`() {
        val f = GameStateFixture(factions = listOf(Faction.ABWEHR_DEFECTOR, Faction.MI6, Faction.OSS), classic = false)
        val b = f.content.balance.intel
        // Front langkah 1 bisa saja memberi Intel ke semua pemain — bandingkan selisihnya.
        assertEquals(b.defectorBonus, f.p(0).intel - f.p(1).intel)
        assertTrue(f.p(1).intel >= b.start)
    }

    @Test
    fun `Dugaan dari properti sendiri memakai Intel dan tidak ditawarkan tanpa Intel`() {
        val f = table().own(13, 0).player(0) { copy(intel = 1) }
        f.land(0, 13)
        f.act(Action.Suggest(p0, listOf(cid("S6")), cid("I6"), cid("L3")))
        assertEquals(0, f.p(0).intel)
        val g = table().own(13, 0).player(0) { copy(intel = 0) }
        g.land(0, 13)
        assertIs<PendingDecision.BusinessPhase>(g.pending)
    }

    @Test
    fun `Ruang Interogasi memakai Intel pada balance saat ini`() {
        val f = table().player(0) { copy(intel = 0) }
        f.land(0, 17)
        assertIs<PendingDecision.BusinessPhase>(f.pending, "tanpa Intel tidak ada Dugaan")
        val g = table().player(0) { copy(intel = 2) }
        g.land(0, 17)
        assertEquals(SuggestionSource.INTERROGATION, assertIs<PendingDecision.SuggestionOffer>(g.pending).source)
        g.act(Action.Suggest(p0, listOf(cid("S6")), cid("I6"), cid("L6")))
        assertEquals(1, g.p(0).intel)
    }

    @Test
    fun `melewati START memberi Intel sampai batas`() {
        val f = table().player(0) { copy(intel = 3) }.at(0, 39).turn(0)
        f.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(4, f.p(0).intel)
        val g = table().player(0) { copy(intel = 4) }.at(0, 39).turn(0)
        g.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(4, g.p(0).intel, "batas Intel")
    }

    @Test
    fun `Bungkam membayar Intel dan Dugaan berlanjut ke pemain berikutnya`() {
        val f = table().player(1) { copy(intel = 2) }.player(0) { copy(intel = 2) }
        f.land(0, 17)
        f.act(Action.Suggest(p0, listOf(cid("S3")), cid("I5"), cid("L6")))
        assertEquals(p1, f.pending!!.decider)
        f.act(Action.StaySilent(p1))
        assertEquals(1, f.p(1).intel)
        // P2 tidak punya → lewat; P3 punya I5.
        val d = assertIs<PendingDecision.RevealForSuggestion>(f.pending)
        assertEquals(PlayerId(3), d.decider)
        assertTrue(f.state.eventLog.any { it.type == GameEvents.SUGGESTION_SILENT && it.payload["player"] == "1" })
        assertTrue(f.state.knowledge[0].none { it.holder == Holder.Player(p1) && it.cardId != cid("S2") })
    }

    @Test
    fun `Bungkam tanpa Intel ditolak`() {
        val f = table().player(1) { copy(intel = 0) }.player(0) { copy(intel = 2) }
        f.land(0, 17)
        f.act(Action.Suggest(p0, listOf(cid("S3")), cid("I6"), cid("L6")))
        assertEquals(ViolationCode.NOT_ENOUGH_INTEL, f.reject(Action.StaySilent(p1)).code)
    }

    @Test
    fun `lewat Dugaan hanya terlihat oleh penanya dan pemain yang lewat`() {
        val f = table().player(0) { copy(intel = 2) }
        f.land(0, 17)
        f.act(Action.Suggest(p0, listOf(cid("S4")), cid("I6"), cid("L6")))
        val pass = f.state.eventLog.last { it.type == GameEvents.SUGGESTION_PASS }
        assertEquals(Visibility.Private(setOf(p1, p0)), pass.visibility)
    }

    // --- Tuduhan ---------------------------------------------------------------------------

    @Test
    fun `Tuduhan terkunci sebelum langkah pembukaan`() {
        val unlock = table().content.balance.accuseUnlockStep
        val early = table().front(unlock - 1).player(0) { copy(intel = 4) }
        early.land(0, 39)
        assertIs<PendingDecision.BusinessPhase>(early.pending, "Kedutaan belum menawarkan Tuduhan")
        val open = table().front(unlock).player(0) { copy(intel = 4) }
        open.land(0, 39)
        assertEquals(PendingDecision.AccuseOffer(p0, 0), open.pending)
        open.act(Action.Accuse(p0, cid("S1"), cid("L1"), cid("I1")))
        assertEquals(WinType.DETECTIVE, open.state.winner!!.type)
        assertEquals(4 - open.content.balance.intel.accuseCost, open.p(0).intel)
    }

    @Test
    fun `Tuduhan butuh Intel`() {
        val f = table().front(table().content.balance.accuseUnlockStep).player(0) { copy(intel = 0) }
        f.land(0, 39)
        assertIs<PendingDecision.BusinessPhase>(f.pending)
    }

    // --- Kecurigaan ------------------------------------------------------------------------

    @Test
    fun `Pasar Gelap menambah Kecurigaan dan batas berarti ditangkap`() {
        val limit = table().content.balance.suspicion.limit
        val add = table().content.balance.suspicion.blackMarket
        val f = table().player(0) { copy(suspicion = limit - add) }
        f.land(0, 29)
        f.act(Action.BlackMarketBuy(p0, p2))
        assertTrue(f.p(0).inJail)
        assertEquals(0, f.p(0).suspicion, "penjara mengosongkan Kecurigaan")
        assertTrue(f.state.eventLog.any { it.type == GameEvents.SUSPICION_ARREST })
        // Transaksi tetap berjalan.
        f.act(Action.BlackMarketChooseCard(p2, cid("L4")))
        assertTrue(f.state.knowledge[0].any { it.cardId == cid("L4") })
    }

    @Test
    fun `Penyelundup Spanyol membayar 100 tanpa Kecurigaan`() {
        val f = table(listOf(Faction.SPANISH_SMUGGLER, Faction.MI6, Faction.NKVD, Faction.OSS))
        f.land(0, 29)
        f.act(Action.BlackMarketBuy(p0, p2))
        assertEquals(1400, f.p(0).money)
        assertEquals(0, f.p(0).suspicion)
    }

    @Test
    fun `Uang Suap mengurangi Kecurigaan dan START meluruhkannya`() {
        val f = table().player(0) { copy(suspicion = 3) }
        f.land(0, 38)
        assertEquals(3 - f.content.balance.suspicion.bribeRelief, f.p(0).suspicion)
        val g = table().player(0) { copy(suspicion = 3) }.at(0, 39).turn(0)
        g.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(3 - g.content.balance.suspicion.decayPerStartPass, g.p(0).suspicion)
    }

    @Test
    fun `kartu baru mengubah Intel dan Kecurigaan`() {
        val f = table().player(0) { copy(intel = 1) }.set { copy(cipherDeck = listOf(17) + (cipherDeck - 17)) }
        f.land(0, 7)
        assertEquals(3, f.p(0).intel)
        val g = table().set { copy(telegramDeck = listOf(18) + (telegramDeck - 18)) }
        g.land(0, 2)
        assertEquals(1, g.p(0).suspicion)
    }

    // --- Jam Perang berbasis lintasan START -----------------------------------------------

    @Test
    fun `Jam maju setelah jumlah lintasan START tercapai`() {
        val f = GameStateFixture(classic = false)
        val cfg = f.state.config
        val need1 = cfg.passesNeededFor(2)
        f.set { copy(startPasses = need1 - 1) }.at(0, 39).turn(0)
        assertEquals(1, f.engine.viewFor(f.state, p0).passesToNextStep)
        f.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(2, f.state.warStep)
        assertEquals(need1, f.state.startPasses)
        // Rata-rata lintasan per langkah sesuai balance, tidak bergantung jumlah pemain.
        val per = f.content.balance.warClock.passesPerStep
        assertEquals(Math.round(11 * per).toInt(), cfg.passesNeededFor(12))
        assertEquals(cfg.passesNeededFor(12), GameStateFixture(players = 6, classic = false).state.config.passesNeededFor(12))
    }

    // --- Map Merah berpindah ---------------------------------------------------------------

    @Test
    fun `Map Merah berpindah membagi ulang satu kategori dan menghapus pengetahuannya`() {
        val f = table().front(4)
            .set { copy(knowledge = knowledge.toMutableList().also { it[0] = it[0] + id.diran.lisbon1944.engine.model.KnownFact(cid("L4"), Holder.Player(p2), id.diran.lisbon1944.engine.model.KnowledgeSource.SUGGESTION) }) }
            .set { copy(firstPlayerIdx = 0) }
        val before = f.state.players.map { pl -> pl.hand.count { f.content.evidence(it).type == EvidenceType.LOCATION } }
        // Front 5 dibalik lewat Jam klasik? Tidak: balance saat ini memakai lintasan START.
        f.set { copy(startPasses = config.passesNeededFor(5) - 1) }.at(0, 39).turn(0)
        f.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(5, f.state.warStep)
        val after = f.state.players.map { pl -> pl.hand.count { f.content.evidence(it).type == EvidenceType.LOCATION } }
        assertEquals(before, after, "jumlah kartu Lokasi tiap pemain tetap")
        assertTrue(f.state.knowledge[0].none { it.cardId == cid("L4") && it.holder == Holder.Player(p2) }, "pengetahuan Lokasi lama dihapus")
        assertTrue(f.state.eventLog.any { it.type == GameEvents.EVIDENCE_RESHUFFLED && it.payload["category"] == "LOCATION" })
        assertEquals(emptyList(), f.engine.checkInvariants(f.state))
        // Pengetahuan tangan sendiri tetap utuh setelah pembagian ulang.
        f.state.players.forEach { pl -> pl.hand.forEach { c -> assertTrue(f.state.knowledge[pl.id.value].any { it.cardId == c && it.holder == Holder.Player(pl.id) }) } }
    }

    // --- Dominasi Ekonomi ------------------------------------------------------------------

    @Test
    fun `Dominasi Ekonomi di meja 5 pemain saat Front dibalik`() {
        val f = GameStateFixture(players = 5, classic = false)
        val bal = f.content.balance
        f.front(bal.dominanceFromStep).set { copy(startPasses = config.passesNeededFor(bal.dominanceFromStep + 1) - 1) }
            .money(0, 20_000).at(0, 39).turn(0)
        f.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertEquals(WinType.ECONOMIC, f.state.winner!!.type)
        assertEquals(listOf(p0), f.state.winner!!.winners)
        // Meja 4 pemain tidak memakai Dominasi.
        val g = GameStateFixture(players = 4, classic = false)
        g.front(bal.dominanceFromStep).set { copy(startPasses = config.passesNeededFor(bal.dominanceFromStep + 1) - 1) }
            .money(0, 20_000).at(0, 39).turn(0)
        g.act(Action.RollDice(p0), ScriptedRng(1, 1))
        assertNull(g.state.winner)
    }

    // --- Misi Rahasia ----------------------------------------------------------------------

    @Test
    fun `setiap pemain menerima satu Misi Rahasia yang privat`() {
        val s = GameStateFixture(classic = false).state
        val ids = s.players.map { it.missionId }
        assertTrue(ids.all { it != null })
        assertEquals(ids.size, ids.toSet().size)
        s.eventLog.filter { it.type == GameEvents.MISSION_ASSIGNED }.forEach { e ->
            val owner = PlayerId(e.payload.getValue("player").toInt())
            assertEquals(Visibility.Private(setOf(owner)), e.visibility)
        }
    }

    @Test
    fun `misi selesai menambah skor`() {
        val f = table().own(1, 0).own(3, 0).own(6, 0)
            .player(0) { copy(missionId = 1) }.player(1) { copy(missionId = 2) }
            .player(2) { copy(missionId = 8, money = 2600) }.player(3) { copy(missionId = 14, finalGuess = Guess(cid("S1"), cid("L1"), cid("I6"))) }
        val s = f.engine.computeScores(f.state)
        assertEquals(f.content.mission(1).points, s[0].mission, "Raja Pelabuhan")
        assertEquals(0, s[1].mission)
        assertEquals(f.content.mission(8).points, s[2].mission, "Kas Rahasia")
        assertEquals(f.content.mission(14).points, s[3].mission, "Analis Ulung")
        assertNull(GameStateFixture().state.players.first().missionId, "aturan dasar tanpa misi")
    }
}
