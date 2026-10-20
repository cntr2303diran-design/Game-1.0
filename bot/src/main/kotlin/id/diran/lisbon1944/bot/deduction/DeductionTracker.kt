package id.diran.lisbon1944.bot.deduction

import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.PlayerId

data class Combo(val suspect: CardId, val location: CardId, val item: CardId) {
    val cards: List<CardId> get() = listOf(suspect, location, item)
}

/**
 * Hasil deduksi.
 * - [comboProb]: peluang tiap kombinasi amplop (jumlah = 1 bila konsisten).
 * - [envelopeProb]: peluang tiap kartu berada di amplop.
 * - [unknownCount]: jumlah kartu tiap lawan yang letaknya belum pasti bagi pelacak.
 * - [relaxation]: 0 = semua constraint dipakai; >0 = sebagian dibuang karena saling bertentangan
 *   (mis. kebohongan Agen Ganda atau catatan basi setelah perdagangan).
 */
class DeductionResult(
    val comboProb: Map<Combo, Double>,
    val envelopeProb: Map<CardId, Double>,
    val unknownCount: Map<PlayerId, Int>,
    val relaxation: Int,
) {
    val consistent: Boolean get() = comboProb.isNotEmpty()

    val best: Pair<Combo, Double>? by lazy { comboProb.maxByOrNull { it.value }?.toPair() }

    val bestProbability: Double get() = best?.second ?: 0.0

    fun envelopeProbability(card: CardId): Double = envelopeProb[card] ?: 0.0

    /** Kategori dianggap terpecahkan bila satu kartu ≥ [threshold]. */
    fun solvedCard(cards: Collection<CardId>, threshold: Double = 0.999): CardId? =
        cards.firstOrNull { envelopeProbability(it) >= threshold }
}

/**
 * Pelacak deduksi: menghitung secara EKSAK jumlah pembagian kartu yang konsisten untuk tiap kombinasi amplop
 * (216 kombinasi × semua distribusi tangan lawan), memakai memo atas sisa kartu sehingga cepat bahkan untuk 6 pemain.
 * Ini setara dengan "enumerasi 216 kombinasi + sampling distribusi tangan" tetapi tanpa galat sampling.
 */
class DeductionTracker(private val content: GameContent) {

    private val cards: List<CardId> = content.evidence.map { it.id }
    private val index: Map<CardId, Int> = cards.withIndex().associate { it.value to it.index }
    private val typeOf: Map<CardId, EvidenceType> = content.evidence.associate { it.id to it.type }

    /** [startRelaxation] 1 = deduksi dasar (tanpa constraint "punya salah satu"). */
    fun solve(input: DeductionInput, startRelaxation: Int = 0): DeductionResult {
        for (level in startRelaxation..MAX_RELAXATION) {
            val r = solveAt(relax(input, level), level)
            if (r != null) return r
        }
        return DeductionResult(emptyMap(), emptyMap(), emptyMap(), MAX_RELAXATION + 1)
    }

    private fun relax(input: DeductionInput, level: Int): DeductionInput = when (level) {
        0 -> input
        1 -> input.copy(hasOneOf = emptyMap())
        2 -> input.copy(hasOneOf = emptyMap(), lacks = emptyMap())
        else -> input.copy(
            hasOneOf = emptyMap(),
            lacks = emptyMap(),
            known = input.known.filter { (_, h) -> h == Holder.Player(input.me) || h == Holder.Embassy },
        )
    }

    private fun mask(set: Collection<CardId>): Long = set.fold(0L) { m, c -> index[c]?.let { m or (1L shl it) } ?: m }

    private fun solveAt(input: DeductionInput, level: Int): DeductionResult? {
        // --- Letak pasti -------------------------------------------------------------------
        val fixedOwner = HashMap<PlayerId, Long>()
        var envelopeFixed = 0L
        var placed = 0L
        for ((card, holder) in input.known) {
            val i = index[card] ?: continue
            val bit = 1L shl i
            if (placed and bit != 0L) continue
            placed = placed or bit
            when (holder) {
                is Holder.Player -> fixedOwner[holder.id] = (fixedOwner[holder.id] ?: 0L) or bit
                Holder.Embassy -> Unit
                Holder.Envelope -> envelopeFixed = envelopeFixed or bit
            }
        }
        val all = (1L shl cards.size) - 1
        val unknown = all and placed.inv()

        // --- Pemain yang tangannya belum diketahui penuh ------------------------------------
        val need = HashMap<PlayerId, Int>()
        for (p in input.seats) {
            val k = (input.handCounts[p] ?: 0) - java.lang.Long.bitCount(fixedOwner[p] ?: 0L)
            if (k < 0) return null
            need[p] = k
        }
        val lacksMask = input.seats.associateWith { mask(input.lacks[it].orEmpty()) }
        val oneOfMasks = input.seats.associateWith { p -> input.hasOneOf[p].orEmpty().map(::mask) }

        // Pemain tanpa kartu tersembunyi: constraint-nya harus sudah terpenuhi oleh kartu pasti.
        for (p in input.seats) {
            val fixed = fixedOwner[p] ?: 0L
            if (fixed and lacksMask.getValue(p) != 0L) return null
            if (need.getValue(p) == 0 && oneOfMasks.getValue(p).any { it and fixed == 0L }) return null
        }
        val order = input.seats.filter { need.getValue(it) > 0 }

        // --- Kandidat amplop per kategori ---------------------------------------------------
        fun candidates(type: EvidenceType): List<Int> {
            val idx = cards.indices.filter { typeOf[cards[it]] == type }
            val fixed = idx.filter { envelopeFixed and (1L shl it) != 0L }
            if (fixed.size > 1) return emptyList()
            return fixed.ifEmpty { idx.filter { unknown and (1L shl it) != 0L } }
        }
        val sus = candidates(EvidenceType.SUSPECT)
        val loc = candidates(EvidenceType.LOCATION)
        val itm = candidates(EvidenceType.ITEM)
        val pool0 = unknown and envelopeFixed.inv()
        val needTotal = order.sumOf { need.getValue(it) }

        val counter = Counter(order, need, lacksMask, oneOfMasks, fixedOwner)
        val comboWeight = LinkedHashMap<Combo, Double>()
        var total = 0.0
        for (s in sus) for (l in loc) for (i in itm) {
            val env = (1L shl s) or (1L shl l) or (1L shl i)
            val pool = pool0 and env.inv()
            if (java.lang.Long.bitCount(pool) != needTotal) continue
            val w = counter.count(0, pool)
            if (w <= 0.0) continue
            comboWeight[Combo(cards[s], cards[l], cards[i])] = w
            total += w
        }
        if (total <= 0.0) return null

        val comboProb = comboWeight.mapValues { it.value / total }
        val envelopeProb = HashMap<CardId, Double>()
        cards.forEachIndexed { i, c -> if (envelopeFixed and (1L shl i) != 0L) envelopeProb[c] = 1.0 }
        comboProb.forEach { (combo, p) -> combo.cards.forEach { c -> if (envelopeProb[c] != 1.0) envelopeProb[c] = (envelopeProb[c] ?: 0.0) + p } }
        return DeductionResult(comboProb, envelopeProb, need.filterKeys { it != input.me }, level)
    }

    /** Menghitung banyaknya pembagian sisa kartu ke pemain [order] yang memenuhi semua constraint. Memo atas (pemain, sisa kartu). */
    private class Counter(
        val order: List<PlayerId>,
        val need: Map<PlayerId, Int>,
        val lacks: Map<PlayerId, Long>,
        val oneOf: Map<PlayerId, List<Long>>,
        val fixed: Map<PlayerId, Long>,
    ) {
        private val memo = HashMap<Long, Double>()

        fun count(i: Int, pool: Long): Double {
            if (i == order.size) return if (pool == 0L) 1.0 else 0.0
            val key = (i.toLong() shl 40) or pool
            memo[key]?.let { return it }
            val p = order[i]
            val k = need.getValue(p)
            val cand = pool and lacks.getValue(p).inv()
            val bits = IntArray(java.lang.Long.bitCount(cand))
            var n = 0
            var m = cand
            while (m != 0L) {
                val b = java.lang.Long.numberOfTrailingZeros(m)
                bits[n++] = b
                m = m and (m - 1)
            }
            val fx = fixed[p] ?: 0L
            val req = oneOf.getValue(p)
            var sum = 0.0
            fun choose(start: Int, left: Int, chosen: Long) {
                if (left == 0) {
                    val hand = chosen or fx
                    for (r in req) if (r and hand == 0L) return
                    sum += count(i + 1, pool and chosen.inv())
                    return
                }
                for (j in start..(n - left)) choose(j + 1, left - 1, chosen or (1L shl bits[j]))
            }
            if (n >= k) choose(0, k, 0L)
            memo[key] = sum
            return sum
        }
    }

    companion object {
        const val MAX_RELAXATION = 3
    }
}
