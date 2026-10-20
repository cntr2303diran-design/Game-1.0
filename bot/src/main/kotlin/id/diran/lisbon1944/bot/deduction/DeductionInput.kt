package id.diran.lisbon1944.bot.deduction

import id.diran.lisbon1944.engine.GameEvents
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerView

/**
 * Semua yang diketahui seorang pemain tentang letak 18 kartu Bukti — disusun HANYA dari PlayerView.
 *
 * - [known]: letak pasti (tangan sendiri, Kedutaan, kartu yang pernah ditunjukkan kepadanya, tangan pemain bangkrut,
 *   isi amplop bila sudah Terbakar).
 * - [lacks]: "X tidak punya kartu-kartu ini" (dilewati saat Dugaan, tidak punya kartu bertipe tertentu).
 * - [hasOneOf]: "X punya minimal satu dari {A, B, C}" (menunjukkan kartu kepada orang lain).
 * - [handCounts]: jumlah kartu tiap pemain (publik).
 */
data class DeductionInput(
    val me: PlayerId,
    val seats: List<PlayerId>,
    val handCounts: Map<PlayerId, Int>,
    val known: Map<CardId, Holder>,
    val lacks: Map<PlayerId, Set<CardId>> = emptyMap(),
    val hasOneOf: Map<PlayerId, List<Set<CardId>>> = emptyMap(),
) {
    companion object {

        /** Bangun input dari view. Jejak log dibaca berurutan; perdagangan kartu Bukti menghapus constraint lama pihak terkait. */
        fun fromView(view: PlayerView, content: GameContent): DeductionInput {
            val me = view.viewerId
            val seats = (listOf(view.me.id) + view.opponents.map { it.id }).sortedBy { it.value }
            val counts = HashMap<PlayerId, Int>()
            counts[me] = view.me.hand.size
            view.opponents.forEach { counts[it.id] = it.handCount }

            val known = LinkedHashMap<CardId, Holder>()
            view.knowledge.forEach { known[it.cardId] = it.holder }
            view.embassyCards.forEach { known[it] = Holder.Embassy }
            view.opponents.forEach { o -> o.publicHand?.forEach { known[it] = Holder.Player(o.id) } }
            view.me.hand.forEach { known[it] = Holder.Player(me) }
            // Kartu yang menurut catatan lama ada di tangan saya tetapi sudah tidak ada (diperdagangkan) — buang.
            known.entries.removeAll { (card, h) -> h == Holder.Player(me) && card !in view.me.hand }

            val lacks = HashMap<PlayerId, MutableSet<CardId>>()
            val hasOne = HashMap<PlayerId, MutableList<Set<CardId>>>()
            val typeCards = EvidenceType.entries.associateWith { t -> content.evidenceOfType(t).map { it.id }.toSet() }
            var named: Set<CardId> = emptySet()

            for (e in view.log) {
                val p = e.payload
                when (e.type) {
                    GameEvents.SUGGESTION_MADE -> {
                        named = (p["suspects"].orEmpty().split(",") + listOfNotNull(p["item"], p["location"]))
                            .filter { it.isNotBlank() }.map(::CardId).toSet()
                    }
                    GameEvents.SUGGESTION_PASS -> p.player("player")?.let { lacks.getOrPut(it) { HashSet() } += named }
                    GameEvents.SUGGESTION_SHOWN -> p.player("player")?.let { hasOne.getOrPut(it) { ArrayList() } += named }
                    GameEvents.EVIDENCE_SHOWN -> {
                        val type = typeOfSource(p["source"])
                        val from = p.player("from")
                        if (type != null && from != null) hasOne.getOrPut(from) { ArrayList() } += typeCards.getValue(type)
                    }
                    GameEvents.NO_CARD -> {
                        val type = p["cardType"]?.let { raw -> EvidenceType.entries.firstOrNull { it.name == raw } }
                        val who = p.player("player")
                        if (type != null && who != null) lacks.getOrPut(who) { HashSet() } += typeCards.getValue(type)
                    }
                    GameEvents.EVIDENCE_RESHUFFLED -> {
                        val type = p["category"]?.let { raw -> EvidenceType.entries.firstOrNull { it.name == raw } }
                        if (type != null) {
                            val cards = typeCards.getValue(type)
                            lacks.values.forEach { it.removeAll(cards) }
                            hasOne.values.forEach { sets -> sets.removeAll { set -> set.any { it in cards } } }
                        }
                    }
                    GameEvents.TRADE_ACCEPTED -> {
                        val moved = (p["giveEvidence"]?.toIntOrNull() ?: 0) + (p["takeEvidence"]?.toIntOrNull() ?: 0)
                        if (moved > 0) {
                            listOfNotNull(p.player("from"), p.player("to")).forEach {
                                lacks.remove(it)
                                hasOne.remove(it)
                            }
                        }
                    }
                }
            }
            return DeductionInput(me, seats, counts, known, lacks, hasOne)
        }

        private fun Map<String, String>.player(key: String): PlayerId? = this[key]?.toIntOrNull()?.let(::PlayerId)

        private fun typeOfSource(source: String?): EvidenceType? = when (source) {
            KnowledgeSource.OPEN_TELEGRAM.name -> EvidenceType.ITEM
            KnowledgeSource.INTELLIGENCE_REPORT.name -> EvidenceType.SUSPECT
            KnowledgeSource.CITY_MAP.name -> EvidenceType.LOCATION
            else -> null
        }
    }
}
