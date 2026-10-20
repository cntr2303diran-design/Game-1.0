package id.diran.lisbon1944.engine

import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.GameMode
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerSetup
import id.diran.lisbon1944.engine.testing.GameStateFixture
import id.diran.lisbon1944.engine.testing.TestEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SetupTest {

    private val engine = TestEngine.engine
    private val content = TestEngine.content

    @Test
    fun `pembagian kartu sesuai jumlah pemain`() {
        val expected = mapOf(3 to (5 to 0), 4 to (3 to 3), 5 to (3 to 0), 6 to (2 to 3))
        expected.forEach { (n, pair) ->
            val (perPlayer, embassy) = pair
            val s = GameStateFixture(players = n).state
            s.players.forEach { assertEquals(perPlayer, it.hand.size, "$n pemain") }
            assertEquals(embassy, s.embassyCards.size, "$n pemain: Kedutaan")
            assertEquals(emptyList(), engine.checkInvariants(s))
        }
    }

    @Test
    fun `amplop berisi tepat 1 tersangka 1 lokasi 1 barang`() {
        (1L..60L).forEach { seed ->
            val env = GameStateFixture(seed = seed).state.envelope
            assertEquals(EvidenceType.SUSPECT, content.evidence(env.suspect).type)
            assertEquals(EvidenceType.LOCATION, content.evidence(env.location).type)
            assertEquals(EvidenceType.ITEM, content.evidence(env.item).type)
        }
    }

    @Test
    fun `kartu di tangan tidak pernah ada di amplop`() {
        (1L..30L).forEach { seed ->
            val s = GameStateFixture(seed = seed).state
            s.players.forEach { p -> assertTrue(p.hand.none { it in s.envelope.cards }) }
            assertTrue(s.embassyCards.none { it in s.envelope.cards })
        }
    }

    @Test
    fun `uang awal front awal dan pemain pertama`() {
        val s = GameStateFixture().state
        s.players.forEach { assertEquals(1500, it.money) }
        assertEquals(1, s.warStep)
        assertEquals(1, s.activeFront.value)
        assertEquals(s.firstPlayerIdx, s.currentPlayerIdx)
        assertEquals(PendingDecision.AwaitRoll(PlayerId(s.firstPlayerIdx)), s.pending)
        // Pemain pertama punya jumlah lemparan inisiatif tertinggi (lemparan terakhirnya).
        val rolls = s.eventLog.filter { it.type == GameEvents.INITIATIVE_ROLL }
            .map { it.payload.getValue("player").toInt() to it.payload.getValue("d1").toInt() + it.payload.getValue("d2").toInt() }
        val firstRolls = rolls.filter { it.first == s.firstPlayerIdx }
        assertTrue(rolls.take(4).all { it.second <= firstRolls.first().second })
        assertEquals(s.firstPlayerIdx.toString(), s.eventLog.first { it.type == GameEvents.FIRST_PLAYER }.payload["player"])
    }

    @Test
    fun `pengetahuan awal adalah tangan sendiri dan Kedutaan`() {
        val s = GameStateFixture(players = 4).state
        s.players.forEach { p ->
            val facts = s.knowledge[p.id.value]
            assertEquals(p.hand.size + s.embassyCards.size, facts.size)
            p.hand.forEach { c -> assertTrue(facts.any { it.cardId == c && it.holder == Holder.Player(p.id) && it.source == KnowledgeSource.OWN_HAND }) }
            s.embassyCards.forEach { c -> assertTrue(facts.any { it.cardId == c && it.holder == Holder.Embassy }) }
        }
    }

    @Test
    fun `seed yang sama menghasilkan persiapan identik`() {
        assertEquals(GameStateFixture(seed = 99).state, GameStateFixture(seed = 99).state)
    }

    @Test
    fun `varian agen ganda memilih tepat satu agen secara privat`() {
        val s = GameStateFixture(players = 5, doubleAgent = true).state
        assertEquals(1, s.players.count { it.isDoubleAgent })
        val da = s.players.first { it.isDoubleAgent }.id
        val role = s.eventLog.first { it.type == GameEvents.DOUBLE_AGENT_ROLE }
        assertTrue(role.visibility.visibleTo(da))
        s.players.filter { it.id != da }.forEach { assertTrue(!role.visibility.visibleTo(it.id)) }
    }

    @Test
    fun `mode cepat memberi 2 properti dan mulai di Front 5`() {
        val s = GameStateFixture(mode = GameMode.QUICK).state
        assertEquals(5, s.warStep)
        s.players.forEach { p -> assertEquals(2, s.ownership.values.count { it.ownerId == p.id }) }
        s.ownership.keys.forEach { assertTrue(content.tile(it) is id.diran.lisbon1944.engine.model.PropertyTile) }
    }

    @Test
    fun `mode kampanye memakai kepemilikan awal`() {
        val cfg = GameConfig(
            players = listOf(Faction.MI6, Faction.OSS, Faction.NKVD).mapIndexed { i, f -> PlayerSetup("A$i", f) },
            balance = content.balance,
            mode = GameMode.CAMPAIGN,
            presetOwnership = mapOf(37 to 2),
        )
        val s = engine.newGame(cfg, 5)
        assertEquals(PlayerId(2), s.ownership.getValue(37).ownerId)
    }

    @Test
    fun `konfigurasi tidak valid ditolak`() {
        val cfg = GameConfig(listOf(PlayerSetup("A", Faction.MI6), PlayerSetup("B", Faction.OSS)), content.balance)
        assertFailsWith<IllegalArgumentException> { engine.newGame(cfg, 1) }
    }

    @Test
    fun `persiapan menghasilkan keputusan awal`() {
        assertIs<PendingDecision.AwaitRoll>(GameStateFixture(players = 6).state.pending)
    }
}
