package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.OwnableTile
import id.diran.lisbon1944.engine.model.Ownership
import id.diran.lisbon1944.engine.model.PlayerState
import id.diran.lisbon1944.engine.model.ScoreBreakdown

/** Skor Akhir Perang (BAGIAN 6.9 + Misi Rahasia + Prestise). */
internal object Scoring {

    fun missionDone(s: GameState, c: GameContent, pl: PlayerState): Boolean {
        val id = pl.missionId ?: return false
        return Conditions.met(s, c, pl, c.mission(id).condition)
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
            val clues = if (pl.burned || guess == null) 0 else guess.cards.zip(s.envelope.cards).count { (g, e) -> g == e } * rules.correctGuess
            val mission = if (rules.missionsEnabled && missionDone(s, c, pl)) pl.missionId?.let { c.mission(it).points } ?: 0 else 0
            val subtotal = cash + property + buildings + mission + pl.prestige
            val total = if (pl.burned) subtotal * Economy.percent(rules.burnedFactor) / 100 else subtotal + clues
            ScoreBreakdown(pl.id, cash, property, buildings, if (pl.burned) 0 else clues, mission, pl.prestige, pl.burned, total)
        }
    }
}
