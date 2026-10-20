package id.diran.lisbon1944.bot

import id.diran.lisbon1944.bot.BotTestSupport.c
import id.diran.lisbon1944.bot.BotTestSupport.p
import id.diran.lisbon1944.bot.deduction.Combo
import id.diran.lisbon1944.bot.deduction.DeductionInput
import id.diran.lisbon1944.bot.deduction.DeductionTracker
import id.diran.lisbon1944.engine.GameEvents
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.GameEvent
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.PlayerId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 20 skenario buatan tangan untuk DeductionTracker (BAGIAN 10). */
class DeductionTrackerTest {

    private val content = BotTestSupport.content
    private val tracker = DeductionTracker(content)

    private fun near(expected: Double, actual: Double, msg: String = "") =
        assertTrue(abs(expected - actual) < 1e-9, "$msg diharapkan $expected, ditemukan $actual")

    private fun ids(vararg s: String) = s.map(::c)

    /** Meja 4 pemain: saya P0 [S1,L1,I1], Kedutaan [S2,L2,I2], lawan masing-masing 3 kartu. */
    private fun base(
        known: Map<String, Holder> = emptyMap(),
        lacks: Map<Int, List<String>> = emptyMap(),
        hasOneOf: Map<Int, List<List<String>>> = emptyMap(),
        counts: List<Int> = listOf(3, 3, 3, 3),
    ): DeductionInput {
        val k = LinkedHashMap<CardId, Holder>()
        listOf("S1", "L1", "I1").forEach { k[c(it)] = Holder.Player(p(0)) }
        listOf("S2", "L2", "I2").forEach { k[c(it)] = Holder.Embassy }
        known.forEach { (card, h) -> k[c(card)] = h }
        return DeductionInput(
            me = p(0),
            seats = counts.indices.map(::PlayerId),
            handCounts = counts.indices.associate { PlayerId(it) to counts[it] },
            known = k,
            lacks = lacks.map { (pl, cs) -> PlayerId(pl) to cs.map(::c).toSet() }.toMap(),
            hasOneOf = hasOneOf.map { (pl, sets) -> PlayerId(pl) to sets.map { set -> set.map(::c).toSet() } }.toMap(),
        )
    }

    private fun suspects() = content.evidenceOfType(EvidenceType.SUSPECT).map { it.id }

    @Test
    fun `01 hanya tangan sendiri dan Kedutaan - peluang seragam`() {
        val r = tracker.solve(base())
        assertEquals(64, r.comboProb.size)
        near(1.0, r.comboProb.values.sum(), "jumlah peluang")
        listOf("S3", "S4", "S5", "S6", "L3", "I6").forEach { near(0.25, r.envelopeProbability(c(it)), it) }
        near(0.0, r.envelopeProbability(c("S1")))
        assertEquals(0, r.relaxation)
    }

    @Test
    fun `02 kartu terlihat di tangan lawan keluar dari kandidat`() {
        val r = tracker.solve(base(known = mapOf("S3" to Holder.Player(p(1)))))
        near(0.0, r.envelopeProbability(c("S3")))
        listOf("S4", "S5", "S6").forEach { near(1.0 / 3, r.envelopeProbability(c(it)), it) }
    }

    @Test
    fun `03 semua tersangka lain diketahui - tersangka terakhir pasti`() {
        val r = tracker.solve(base(known = mapOf("S3" to Holder.Player(p(1)), "S4" to Holder.Player(p(2)), "S5" to Holder.Player(p(3)))))
        near(1.0, r.envelopeProbability(c("S6")))
        assertEquals(c("S6"), r.solvedCard(suspects()))
    }

    @Test
    fun `04 isi amplop diketahui setelah Terbakar`() {
        val r = tracker.solve(base(known = mapOf("S6" to Holder.Envelope, "L6" to Holder.Envelope, "I6" to Holder.Envelope)))
        assertEquals(Combo(c("S6"), c("L6"), c("I6")) to 1.0, r.best)
    }

    @Test
    fun `05 constraint mustahil dilonggarkan bertahap`() {
        val all = listOf("S3", "S4", "S5", "S6", "L3", "L4", "L5", "L6", "I3", "I4", "I5", "I6")
        val r = tracker.solve(base(lacks = mapOf(1 to all)))
        assertTrue(r.consistent)
        assertEquals(2, r.relaxation)
    }

    @Test
    fun `06 lewat Dugaan menaikkan peluang kartu di amplop`() {
        val r = tracker.solve(base(lacks = mapOf(1 to listOf("S3"))))
        assertTrue(r.envelopeProbability(c("S3")) > r.envelopeProbability(c("S4")))
        near(r.envelopeProbability(c("S4")), r.envelopeProbability(c("S5")))
        near(1.0, suspects().sumOf { r.envelopeProbability(it) }, "jumlah per kategori")
    }

    @Test
    fun `07 punya salah satu dengan dua lainnya mustahil memaksa letak kartu`() {
        val r = tracker.solve(base(hasOneOf = mapOf(1 to listOf(listOf("S3", "L1", "I2")))))
        near(0.0, r.envelopeProbability(c("S3")))
        listOf("S4", "S5", "S6").forEach { near(1.0 / 3, r.envelopeProbability(c(it)), it) }
    }

    @Test
    fun `08 punya salah satu digabung kartu yang diketahui`() {
        val r = tracker.solve(base(known = mapOf("L3" to Holder.Player(p(2))), hasOneOf = mapOf(1 to listOf(listOf("S3", "L3")))))
        near(0.0, r.envelopeProbability(c("S3")))
    }

    @Test
    fun `09 dua pemain tanpa Barang - Barang terkumpul di pemain ketiga`() {
        val items = listOf("I1", "I2", "I3", "I4", "I5", "I6")
        val r = tracker.solve(base(lacks = mapOf(1 to items, 2 to items)))
        assertEquals(0, r.relaxation)
        listOf("I3", "I4", "I5", "I6").forEach { near(0.25, r.envelopeProbability(c(it)), it) }
    }

    @Test
    fun `10 semua lawan tidak punya I3 - I3 pasti di amplop`() {
        val r = tracker.solve(base(lacks = mapOf(1 to listOf("I3"), 2 to listOf("I3"), 3 to listOf("I3"))))
        near(1.0, r.envelopeProbability(c("I3")))
    }

    @Test
    fun `11 Dugaan tanpa jawaban dari siapa pun memecahkan misteri`() {
        val named = listOf("S4", "L4", "I4")
        val r = tracker.solve(base(lacks = mapOf(1 to named, 2 to named, 3 to named)))
        assertEquals(Combo(c("S4"), c("L4"), c("I4")), r.best!!.first)
        near(1.0, r.bestProbability)
    }

    @Test
    fun `12 punya salah satu dikurangi yang tidak dimiliki`() {
        val r = tracker.solve(base(lacks = mapOf(1 to listOf("S5", "L5")), hasOneOf = mapOf(1 to listOf(listOf("S5", "L5", "I5")))))
        near(0.0, r.envelopeProbability(c("I5")))
    }

    @Test
    fun `13 jumlah kartu tak dikenal per lawan`() {
        val r = tracker.solve(base(known = mapOf("S3" to Holder.Player(p(1)), "L3" to Holder.Player(p(1)))))
        assertEquals(1, r.unknownCount[p(1)])
        assertEquals(3, r.unknownCount[p(2)])
    }

    @Test
    fun `14 catatan melebihi jumlah kartu dibuang pada relaksasi terakhir`() {
        val r = tracker.solve(base(known = mapOf("S3" to Holder.Player(p(1)), "S4" to Holder.Player(p(1)), "S5" to Holder.Player(p(1)), "S6" to Holder.Player(p(1))), counts = listOf(3, 3, 3, 3)))
        assertTrue(r.consistent)
        assertEquals(3, r.relaxation)
    }

    @Test
    fun `15 tiga pemain lima kartu tanpa Kedutaan`() {
        val k = LinkedHashMap<CardId, Holder>()
        ids("S1", "S2", "L1", "L2", "I1").forEach { k[it] = Holder.Player(p(0)) }
        val input = DeductionInput(p(0), listOf(p(0), p(1), p(2)), mapOf(p(0) to 5, p(1) to 5, p(2) to 5), k)
        val r = tracker.solve(input)
        near(0.25, r.envelopeProbability(c("S3")))
        near(0.2, r.envelopeProbability(c("I2")))
        near(1.0, r.comboProb.values.sum())
    }

    @Test
    fun `16 enam pemain tetap cepat dan ternormalisasi`() {
        val k = LinkedHashMap<CardId, Holder>()
        ids("S1", "L1").forEach { k[it] = Holder.Player(p(0)) }
        ids("S2", "L2", "I2").forEach { k[it] = Holder.Embassy }
        val seats = (0 until 6).map(::PlayerId)
        val input = DeductionInput(
            p(0), seats, seats.associateWith { 2 }, k,
            lacks = mapOf(p(1) to setOf(c("S3")), p(4) to setOf(c("I3"), c("L5"))),
            hasOneOf = mapOf(p(2) to listOf(setOf(c("S4"), c("L4"), c("I4")))),
        )
        val t0 = System.nanoTime()
        val r = tracker.solve(input)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(ms < 3_000, "solve 6 pemain $ms ms")
        EvidenceType.entries.forEach { t ->
            near(1.0, content.evidenceOfType(t).sumOf { r.envelopeProbability(it.id) }, "kategori $t")
        }
        assertEquals(0, r.relaxation)
    }

    @Test
    fun `17 deduksi dasar mengabaikan constraint punya salah satu`() {
        val input = base(hasOneOf = mapOf(1 to listOf(listOf("S3", "L1", "I2"))))
        near(0.0, tracker.solve(input).envelopeProbability(c("S3")))
        near(0.25, tracker.solve(input, startRelaxation = 1).envelopeProbability(c("S3")))
    }

    // --- Dari PlayerView + log sungguhan --------------------------------------------------

    private fun view(vararg events: GameEvent) = BotTestSupport.deal(
        4, listOf("S6", "L6", "I6"), listOf("S1", "L1", "I1"), listOf("S3", "L3", "I3"), listOf("S4", "L4", "I4"), listOf("S5", "L5", "I5"),
    ).let { BotTestSupport.engine.viewFor(it, p(0)) }.copy(log = events.mapIndexed { i, e -> e.copy(seq = i) })

    private fun ev(type: String, vararg kv: Pair<String, String>) = GameEvent(0, type, mapOf(*kv))

    @Test
    fun `18 log Dugaan menjadi constraint lewat dan menunjukkan`() {
        val v = view(
            ev(GameEvents.SUGGESTION_MADE, "player" to "3", "suspects" to "S4", "item" to "I4", "location" to "L3"),
            ev(GameEvents.SUGGESTION_PASS, "player" to "0", "asker" to "3"),
            ev(GameEvents.SUGGESTION_PASS, "player" to "1", "asker" to "3"),
            ev(GameEvents.SUGGESTION_SHOWN, "player" to "2", "asker" to "3"),
        )
        val input = DeductionInput.fromView(v, content)
        assertEquals(ids("S4", "I4", "L3").toSet(), input.lacks[p(1)])
        assertEquals(listOf(ids("S4", "I4", "L3").toSet()), input.hasOneOf[p(2)])
        assertEquals(3, input.handCounts[p(1)])
        assertEquals(Holder.Embassy, input.known[c("S2")])
    }

    @Test
    fun `19 Telegram Terbuka dan tanpa kartu bertipe menjadi constraint`() {
        val v = view(
            ev(GameEvents.EVIDENCE_SHOWN, "from" to "1", "to" to "2", "source" to "OPEN_TELEGRAM"),
            ev(GameEvents.NO_CARD, "player" to "3", "viewer" to "2", "reason" to "OPEN_TELEGRAM", "cardType" to "ITEM"),
        )
        val input = DeductionInput.fromView(v, content)
        assertEquals(content.evidenceOfType(EvidenceType.ITEM).map { it.id }.toSet(), input.hasOneOf[p(1)]!!.single())
        assertEquals(content.evidenceOfType(EvidenceType.ITEM).map { it.id }.toSet(), input.lacks[p(3)])
    }

    @Test
    fun `20 perdagangan kartu Bukti menghapus constraint lama pihak terkait`() {
        val v = view(
            ev(GameEvents.SUGGESTION_MADE, "player" to "3", "suspects" to "S4", "item" to "I4", "location" to "L3"),
            ev(GameEvents.SUGGESTION_PASS, "player" to "1", "asker" to "3"),
            ev(GameEvents.SUGGESTION_SHOWN, "player" to "2", "asker" to "3"),
            ev(GameEvents.TRADE_ACCEPTED, "from" to "1", "to" to "2", "giveEvidence" to "1", "takeEvidence" to "0"),
        )
        val input = DeductionInput.fromView(v, content)
        assertEquals(null, input.lacks[p(1)])
        assertEquals(null, input.hasOneOf[p(2)])
        // Constraint yang tidak terkait tetap utuh setelah perdagangan tanpa kartu Bukti.
        val v2 = view(
            ev(GameEvents.SUGGESTION_MADE, "player" to "3", "suspects" to "S4", "item" to "I4", "location" to "L3"),
            ev(GameEvents.SUGGESTION_PASS, "player" to "1", "asker" to "3"),
            ev(GameEvents.TRADE_ACCEPTED, "from" to "1", "to" to "2", "giveEvidence" to "0", "takeEvidence" to "0"),
        )
        assertEquals(ids("S4", "I4", "L3").toSet(), DeductionInput.fromView(v2, content).lacks[p(1)])
    }
}
