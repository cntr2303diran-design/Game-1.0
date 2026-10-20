package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.OpponentView
import id.diran.lisbon1944.engine.model.OwnableTile
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerView

/** Satu-satunya jalan UI dan bot membaca permainan. Informasi tersembunyi tidak pernah keluar dari sini. */
internal object Views {

    fun viewFor(s: GameState, c: GameContent, viewer: PlayerId): PlayerView {
        require(viewer.value in s.players.indices) { "Pemain ${viewer.value} tidak ada" }
        val me = s.players[viewer.value]
        val propertyCount = s.ownership.entries
            .filter { c.tile(it.key) is OwnableTile }
            .groupingBy { it.value.ownerId }
            .eachCount()
        val opponents = s.players.filter { it.id != viewer }.map { o ->
            OpponentView(
                id = o.id,
                name = o.name,
                faction = o.faction,
                isBot = o.isBot,
                money = o.money,
                position = o.position,
                handCount = o.hand.size,
                publicHand = if (o.bankrupt) o.hand else null,
                inJail = o.inJail,
                burned = o.burned,
                bankrupt = o.bankrupt,
                abilityUsed = o.abilityUsed,
                heldCardCount = o.heldCards.size,
                propertyCount = propertyCount[o.id] ?: 0,
            )
        }
        val pend = s.pending
        return PlayerView(
            viewerId = viewer,
            me = me,
            opponents = opponents,
            ownership = s.ownership,
            embassyCards = s.embassyCards,
            warStep = s.warStep,
            activeFront = s.activeFront,
            cipherDeckSize = s.cipherDeck.size,
            telegramDeckSize = s.telegramDeck.size,
            casinoVault = s.casinoVault,
            houseStock = s.houseStock,
            hqStock = s.hqStock,
            currentPlayer = PlayerId(s.currentPlayerIdx),
            roundNumber = s.roundNumber,
            phase = s.phase,
            doublesCount = s.doublesCount,
            myDecision = pend?.takeIf { it.decider == viewer },
            waitingOn = pend?.decider,
            waitingKind = pend?.let(::kindOf),
            auction = s.auction,
            trade = s.trade?.takeIf { viewer == it.offer.from || viewer == it.offer.to },
            winner = s.winner,
            revealedEnvelope = if (s.winner != null || me.burned) s.envelope else null,
            knowledge = s.knowledge[viewer.value],
            log = s.eventLog.filter { it.visibility.visibleTo(viewer) },
            config = s.config,
        )
    }

    fun kindOf(d: PendingDecision): String = d::class.simpleName ?: "Decision"
}
