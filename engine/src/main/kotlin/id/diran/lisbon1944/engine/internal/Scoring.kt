package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.MissionCondition
import id.diran.lisbon1944.engine.model.PlayerState
import id.diran.lisbon1944.engine.model.OwnableTile
import id.diran.lisbon1944.engine.model.Ownership
import id.diran.lisbon1944.engine.model.ScoreBreakdown

/** Skor Akhir Perang (BAGIAN 6.9). */
internal object Scoring {

    fun missionDone(s: GameState, c: GameContent, pl: PlayerState): Boolean {
        val id = pl.missionId ?: return false
        val owned = s.ownership.filterValues { it.ownerId == pl.id }
        return when (val cond = c.mission(id).condition) {
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
            is MissionCondition.CorrectGuesses -> (pl.finalGuess?.cards?.zip(s.envelope.cards)?.count { (g, e) -> g == e } ?: 0) >= cond.count
        }
    }

    fun compute(s: GameState, c: GameContent): List<ScoreBreakdown> {
        val rules = s.config.balance.score
        return s.players.map { pl ->
            val owned = s.ownership.filterValues { it.ownerId == pl.id }
            val cash = pl.money / rules.cashPer
            val propertyValue = owned.entries.sumOf { (tile, o) ->
                val price = (c.tile(tile) as OwnableTile).price
                if (o.mortgaged) price / 2 else price
            }
            val property = propertyValue / rules.propertyPer
            val buildings = owned.values.sumOf { if (it.level == Ownership.HQ_LEVEL) rules.hq else it.level * rules.safeHouse }
            val guess = pl.finalGuess
            val clues = if (pl.burned || guess == null) {
                0
            } else {
                guess.cards.zip(s.envelope.cards).count { (g, e) -> g == e } * rules.correctGuess
            }
            val mission = if (rules.missionsEnabled && missionDone(s, c, pl)) pl.missionId?.let { c.mission(it).points } ?: 0 else 0
            val subtotal = cash + property + buildings + mission
            val total = if (pl.burned) subtotal * Economy.percent(rules.burnedFactor) / 100 else subtotal + clues
            ScoreBreakdown(pl.id, cash, property, buildings, if (pl.burned) 0 else clues, mission, pl.burned, total)
        }
    }
}
