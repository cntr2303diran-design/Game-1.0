package id.diran.lisbon1944.bot

import id.diran.lisbon1944.bot.deduction.DeductionInput
import id.diran.lisbon1944.bot.deduction.DeductionResult
import id.diran.lisbon1944.bot.deduction.DeductionTracker
import id.diran.lisbon1944.engine.GameEvents
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.District
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.MissionCard
import id.diran.lisbon1944.engine.model.MissionCondition
import id.diran.lisbon1944.engine.model.OwnableTile
import id.diran.lisbon1944.engine.model.Ownership
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerView
import id.diran.lisbon1944.engine.model.PropertyTile

/** Turunan dari satu PlayerView yang dipakai bot. Semua dihitung malas dan hanya dari view. */
class Insight(val view: PlayerView, private val content: GameContent, private val tracker: DeductionTracker, private val mode: DeductionMode) {

    val me: PlayerId get() = view.viewerId
    val money: Int get() = view.me.money
    val warStep: Int get() = view.warStep

    val deduction: DeductionResult by lazy {
        tracker.solve(DeductionInput.fromView(view, content), if (mode == DeductionMode.FULL) 0 else 1)
    }

    /** Untuk tiap kartu di tangan saya: siapa saja lawan yang sudah melihatnya. */
    val knownBy: Map<CardId, Set<PlayerId>> by lazy {
        val result = HashMap<CardId, MutableSet<PlayerId>>()
        val mine = me.value.toString()
        view.log.forEach { e ->
            if (e.type == GameEvents.CARD_SEEN && e.payload["holder"] == mine) {
                val card = e.payload["card"]?.let(::CardId) ?: return@forEach
                val viewer = e.payload["viewer"]?.toIntOrNull()?.let(::PlayerId) ?: return@forEach
                if (viewer != me) result.getOrPut(card) { HashSet() } += viewer
            }
        }
        result
    }

    fun knownCount(card: CardId): Int = knownBy[card]?.size ?: 0

    /** Kartu yang paling "murah" ditunjukkan: sudah paling banyak diketahui orang (seri → urutan id). */
    fun mostKnown(cards: List<CardId>): CardId = cards.maxWith(compareBy<CardId>({ knownCount(it) }, { it.value }))

    // --- Ekonomi ---------------------------------------------------------------------------

    fun owner(tile: Int): PlayerId? = view.ownership[tile]?.ownerId

    fun propertyValue(owner: PlayerId): Int = view.ownership.entries.filter { it.value.ownerId == owner }.sumOf { (tile, o) ->
        val price = (content.tile(tile) as OwnableTile).price
        if (o.mortgaged) price / 2 else price
    }

    fun buildingPoints(owner: PlayerId): Int {
        val rules = view.config.balance.score
        return view.ownership.values.filter { it.ownerId == owner }
            .sumOf { if (it.level == Ownership.HQ_LEVEL) rules.hq else it.level * rules.safeHouse }
    }

    fun moneyOf(p: PlayerId): Int = if (p == me) money else view.opponents.first { it.id == p }.money

    /** Skor proyeksi tanpa poin petunjuk (BAGIAN 6.9). */
    fun projectedScore(p: PlayerId): Int {
        val rules = view.config.balance.score
        return moneyOf(p) / rules.cashPer + propertyValue(p) / rules.propertyPer + buildingPoints(p)
    }

    val activeOpponents: List<PlayerId> by lazy { view.opponents.filter { !it.bankrupt }.map { it.id } }

    val leader: PlayerId? by lazy {
        (activeOpponents + me).maxByOrNull { projectedScore(it) }
    }

    /** Kalah jauh: skor proyeksi saya < pemimpin × (1 − deficit). */
    fun losingBadly(deficit: Double): Boolean {
        val best = activeOpponents.maxOfOrNull { projectedScore(it) } ?: return false
        return projectedScore(me) < best * (1.0 - deficit)
    }

    fun districtOf(tile: Int): District? = (content.tile(tile) as? PropertyTile)?.district

    /** Berapa properti distrik [d] yang sudah saya miliki. */
    fun ownedIn(d: District, who: PlayerId = me): Int = content.propertiesIn(d).count { owner(it.index) == who }

    /** Membeli [tile] membuat saya memiliki seluruh distrik? */
    fun completesSet(tile: Int, who: PlayerId = me): Boolean {
        val d = districtOf(tile) ?: return escapeCount(who) >= 1
        return content.propertiesIn(d).all { it.index == tile || owner(it.index) == who }
    }

    fun escapeCount(who: PlayerId): Int = content.escapeRoutes.count { owner(it.index) == who }

    /** Distrik dengan lawan yang sudah memegang properti: membeli memblokir mereka. */
    fun blocksOpponent(tile: Int): Boolean {
        val d = districtOf(tile) ?: return false
        return activeOpponents.any { o -> content.propertiesIn(d).count { owner(it.index) == o } == 2 }
    }

    fun cardsOfType(type: EvidenceType): List<CardId> = content.evidenceOfType(type).map { it.id }

    /** Misi Rahasia saya (hanya saya yang tahu). */
    val mission: MissionCard? get() = view.me.missionId?.let(content::mission)

    /** Sisa ruang Kecurigaan sebelum ditangkap. */
    val suspicionRoom: Int get() = view.config.balance.suspicion.limit - 1 - view.me.suspicion

    /** Apakah petak ini membantu Misi Rahasia saya? */
    fun helpsMission(tile: Int): Boolean = when (val c = mission?.condition) {
        is MissionCondition.OwnDistrict -> districtOf(tile) == c.district
        is MissionCondition.EscapeRoutes -> content.tile(tile) is id.diran.lisbon1944.engine.model.EscapeRouteTile
        is MissionCondition.PropertyCount -> true
        else -> false
    }
}
