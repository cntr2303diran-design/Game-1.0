package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.GameEvents as E
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.CardEffect
import id.diran.lisbon1944.engine.model.Creditor
import id.diran.lisbon1944.engine.model.District
import id.diran.lisbon1944.engine.model.EscapeRouteTile
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.MissionCondition
import id.diran.lisbon1944.engine.model.Ownership
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerState
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.TacticCard
import id.diran.lisbon1944.engine.model.Task

// =====================================================================================
// Syarat Misi & Operasi (dipakai bersama)
// =====================================================================================

internal object Conditions {
    fun completeDistricts(s: GameState, c: GameContent, p: PlayerId): Int =
        District.entries.count { d -> c.propertiesIn(d).all { s.ownership[it.index]?.ownerId == p } }

    fun met(s: GameState, c: GameContent, pl: PlayerState, cond: MissionCondition): Boolean {
        val owned = s.ownership.filterValues { it.ownerId == pl.id }
        return when (cond) {
            is MissionCondition.OwnDistrict -> c.propertiesIn(cond.district).all { owned.containsKey(it.index) }
            is MissionCondition.EscapeRoutes -> c.escapeRoutes.count { owned.containsKey(it.index) } >= cond.count
            is MissionCondition.CashAtLeast -> pl.money >= cond.amount
            is MissionCondition.BuildingPoints -> owned.values.sumOf { it.level } >= cond.count
            is MissionCondition.KnownCards -> s.knowledge[pl.id.value]
                .filter { it.holder != Holder.Embassy && it.holder != Holder.Player(pl.id) }
                .map { it.cardId }.toSet().size >= cond.count
            is MissionCondition.SuspicionAtMost -> pl.suspicion <= cond.max
            is MissionCondition.PropertyCount -> owned.size >= cond.count
            is MissionCondition.IntelAtLeast -> pl.intel >= cond.count
            is MissionCondition.CorrectGuesses ->
                (pl.finalGuess?.cards?.zip(s.envelope.cards)?.count { (g, e) -> g == e } ?: 0) >= cond.count
            is MissionCondition.HqCount -> owned.values.count { it.level == Ownership.HQ_LEVEL } >= cond.count
            is MissionCondition.CompleteDistricts -> completeDistricts(s, c, pl.id) >= cond.count
            is MissionCondition.PrestigeAtLeast -> pl.prestige >= cond.count
            is MissionCondition.OperationsClaimed -> pl.operationsClaimed >= cond.count
            is MissionCondition.TacticsPlayed -> pl.tacticsPlayed >= cond.count
        }
    }
}

// =====================================================================================
// Prestise
// =====================================================================================

internal fun Tx.gainPrestige(p: PlayerId, amount: Int, reason: String) {
    if (amount == 0 || player(p).bankrupt) return
    upd(p) { it.copy(prestige = it.prestige + amount) }
    emit(E.PRESTIGE, "player" to p, "delta" to amount, "value" to player(p).prestige, "reason" to reason)
}

// =====================================================================================
// Kartu Taktik
// =====================================================================================

internal fun Tx.drawTactics(p: PlayerId, count: Int) {
    if (!b.tactics.enabled) return
    repeat(count) {
        val pl = player(p)
        if (pl.bankrupt || pl.tactics.size >= b.tactics.handLimit || s.tacticDeck.isEmpty()) return
        val card = s.tacticDeck.first()
        s = s.copy(tacticDeck = s.tacticDeck.drop(1))
        upd(p) { it.copy(tactics = it.tactics + card) }
        emit(E.TACTIC_DRAWN, "player" to p, "count" to player(p).tactics.size)
        emit(E.TACTIC_DRAWN, "player" to p, "card" to card, visibleTo = listOf(p))
    }
}

/** Mainkan Kartu Taktik; [inRent] = dimainkan saat keputusan sewa (Penyamaran). */
internal fun Tx.playTactic(p: PlayerId, card: TacticCard, target: PlayerId?, tile: Int?) {
    upd(p) { pl -> pl.copy(tactics = pl.tactics.toMutableList().also { it.remove(card.id) }, tacticsPlayed = pl.tacticsPlayed + 1) }
    s = s.copy(tacticDeck = s.tacticDeck + card.id, tacticsPlayedThisTurn = s.tacticsPlayedThisTurn + 1)
    emit(E.TACTIC_PLAYED, "player" to p, "card" to card.id, "target" to target, "tile" to tile)
    // Efek diterapkan dari belakang agar tugas yang didorong (mis. Pay) berjalan sesuai urutan kartu.
    card.effects.asReversed().forEach { e -> applyTacticEffect(p, e, target, tile) }
}

private fun Tx.applyTacticEffect(p: PlayerId, e: CardEffect, target: PlayerId?, tile: Int?) {
    when (e) {
        CardEffect.CancelRent -> emit(E.RENT_CANCELLED, "player" to p)
        CardEffect.PeekTarget -> target?.let { peek(p, it, KnowledgeSource.MI6_ABILITY, ownerSees = false) }
        is CardEffect.TargetSuspicion -> target?.let { changeSuspicion(it, e.amount, "tactic") }
        is CardEffect.StealFromTarget -> target?.let { push(Task.Pay(it, Creditor.Player(p), e.amount, "blackmail")) }
        is CardEffect.SealTargetTile -> tile?.let { seal(listOf(it), e.rounds) }
        is CardEffect.SealTargetDistrict -> tile?.let { t ->
            val d = (c.tile(t) as PropertyTile).district
            seal(c.propertiesIn(d).map { it.index }, e.rounds)
        }
        is CardEffect.GainPrestige -> gainPrestige(p, e.amount, "tactic")
        is CardEffect.GainIntel -> changeIntel(p, e.amount, "tactic")
        is CardEffect.LoseIntel -> changeIntel(p, -e.amount, "tactic")
        is CardEffect.AddSuspicion -> changeSuspicion(p, e.amount, "tactic")
        is CardEffect.ReduceSuspicion -> changeSuspicion(p, -e.amount, "tactic")
        is CardEffect.GainFromBank -> gainFromBank(p, e.amount, "tactic")
        is CardEffect.ExtraSuggestion -> upd(p) { it.copy(extraSuggestionTurnsLeft = e.turns + 1) }
        else -> error("Efek ${e::class.simpleName} tidak didukung Kartu Taktik (dicegah validator)")
    }
}

private fun Tx.seal(tiles: List<Int>, rounds: Int) {
    val until = s.roundNumber + rounds
    s = s.copy(sealedTiles = s.sealedTiles + tiles.associateWith { maxOf(until, s.sealedTiles[it] ?: 0) })
    emit(E.TILE_SEALED, "tiles" to tiles, "until" to until)
}

internal fun isSealed(s: GameState, tile: Int): Boolean = (s.sealedTiles[tile] ?: -1) >= s.roundNumber

// =====================================================================================
// Operasi Kota
// =====================================================================================

/** Dicek di akhir setiap giliran: pemain aktif (mulai pemain giliran) yang memenuhi syarat mengklaim Operasi terbuka. */
internal fun Tx.checkOperations() {
    if (!b.operationsEnabled || s.winner != null) return
    val order = (listOf(current) + othersFromLeft(current)).filter(::isActive)
    var claimed = true
    while (claimed) {
        claimed = false
        loop@ for (p in order) {
            for (opId in s.openOperations) {
                val op = c.operation(opId)
                if (!Conditions.met(s, c, player(p), op.condition)) continue
                s = s.copy(openOperations = s.openOperations - opId, claimedOperations = s.claimedOperations + opId)
                upd(p) { it.copy(operationsClaimed = it.operationsClaimed + 1) }
                emit(E.OPERATION_CLAIMED, "player" to p, "operation" to opId)
                gainFromBank(p, op.reward.money, "operation")
                changeIntel(p, op.reward.intel, "operation")
                gainPrestige(p, op.reward.prestige, "operation")
                refillOperations()
                claimed = true
                break@loop
            }
        }
    }
}

internal fun Tx.refillOperations() {
    while (s.openOperations.size < c.openOperations && s.operationDeck.isNotEmpty()) {
        val next = s.operationDeck.first()
        s = s.copy(operationDeck = s.operationDeck.drop(1), openOperations = s.openOperations + next)
        emit(E.OPERATION_OPENED, "operation" to next)
    }
}

// =====================================================================================
// Pion netral: Patroli PIDE & Kontak Netral
// =====================================================================================

internal fun Tx.movePawns() {
    if (b.patrol.enabled && s.patrolPos >= 0) {
        val steps = (1..b.patrol.dice).sumOf { rng.rollDie() }
        val to = (s.patrolPos + steps) % BOARD_SIZE
        s = s.copy(patrolPos = to)
        emit(E.PATROL_MOVED, "to" to to, "steps" to steps)
    }
    if (b.contact.enabled && s.contactPos >= 0) {
        val steps = (1..b.contact.dice).sumOf { rng.rollDie() }
        val to = (s.contactPos + steps) % BOARD_SIZE
        s = s.copy(contactPos = to)
        emit(E.CONTACT_MOVED, "to" to to, "steps" to steps)
    }
    // Segel yang kedaluwarsa dibersihkan.
    if (s.sealedTiles.isNotEmpty()) s = s.copy(sealedTiles = s.sealedTiles.filterValues { it >= s.roundNumber })
}

/** Efek berhenti di petak pion. Mengembalikan false bila pemain ditangkap (aksi petak dibatalkan). */
internal fun Tx.pawnEncounter(p: PlayerId): Boolean {
    val pos = player(p).position
    if (b.contact.enabled && pos == s.contactPos) {
        emit(E.CONTACT_MET, "player" to p, "tile" to pos)
        changeIntel(p, b.contact.intel, "contact")
        gainFromBank(p, b.contact.money, "contact")
    }
    if (b.patrol.enabled && pos == s.patrolPos) {
        emit(E.PATROL_CAUGHT, "player" to p, "tile" to pos)
        changeSuspicion(p, b.patrol.suspicion, "patrol")
        if (player(p).inJail) return false
    }
    return true
}

// =====================================================================================
// Bantuan Sekutu
// =====================================================================================

internal fun Tx.alliedAid() {
    if (!b.comeback.enabled || s.warEnded || s.winner != null) return
    val active = activeIds()
    if (active.size < 2) return
    val worth = active.associateWith { Economy.netWorth(s, c, it) }
    val min = worth.values.min()
    worth.filterValues { it == min }.keys.forEach { p ->
        emit(E.ALLIED_AID, "player" to p)
        gainFromBank(p, b.comeback.money, "allied_aid")
        changeIntel(p, b.comeback.intel, "allied_aid")
    }
}

internal fun isEscape(c: GameContent, tile: Int) = c.board.getOrNull(tile) is EscapeRouteTile
