package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.GameEvents as E
import id.diran.lisbon1944.engine.model.CardEffect
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Creditor
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.DeckCard
import id.diran.lisbon1944.engine.model.Direction
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.HeldCard
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.ShowReason
import id.diran.lisbon1944.engine.model.SpecialTile
import id.diran.lisbon1944.engine.model.Task
import id.diran.lisbon1944.engine.model.TargetScope

// =====================================================================================
// Tumpukan Sandi & Telegram
// =====================================================================================

internal fun Tx.draw(p: PlayerId, deck: Deck) {
    val pile = deck(deck)
    if (pile.isEmpty()) {
        emit(E.DECK_EMPTY, "player" to p, "deck" to deck)
        return
    }
    if (deck == Deck.TELEGRAM && player(p).faction == Faction.SWISS_JOURNALIST && pile.size >= 2) {
        val options = pile.take(2)
        setDeck(deck, pile.drop(2))
        emit(E.CARD_DRAWN, "player" to p, "deck" to deck, "count" to 2)
        emit(E.CARDS_OFFERED, "player" to p, "deck" to deck, "cards" to options, visibleTo = listOf(p))
        setPending(PendingDecision.TelegramKeepChoice(p, options))
        return
    }
    val id = pile.first()
    setDeck(deck, pile.drop(1))
    emit(E.CARD_DRAWN, "player" to p, "deck" to deck, "card" to id)
    startCard(p, deck, id)
}

internal fun Tx.startCard(p: PlayerId, deck: Deck, id: Int) {
    val card = c.card(deck, id)
    if (card.keep) push(Task.ResolveCard(p, deck, id)) else push(Task.ResolveCard(p, deck, id), Task.ReturnCard(deck, id))
}

internal fun Tx.resolveCard(t: Task.ResolveCard) {
    if (player(t.player).bankrupt) return
    val card = c.card(t.deck, t.cardId)
    if (t.effectIndex >= card.effects.size) return
    push(t.copy(effectIndex = t.effectIndex + 1))
    applyCardEffect(t.player, card, card.effects[t.effectIndex])
}

private fun Tx.forwardSteps(from: Int, to: Int): Int = (to - from + BOARD_SIZE) % BOARD_SIZE

private fun Tx.applyCardEffect(p: PlayerId, card: DeckCard, e: CardEffect) {
    val pos = player(p).position
    when (e) {
        is CardEffect.MoveToTile -> {
            val steps = forwardSteps(pos, e.tile)
            if (steps == 0) push(Task.Land(p, e.rentMultiplier, e.freeAccuse))
            else push(Task.Move(p, steps, e.collectSalary, e.rentMultiplier, e.freeAccuse))
        }
        is CardEffect.MoveToNearest -> {
            val target = (1..BOARD_SIZE).map { (pos + it) % BOARD_SIZE }
                .first { (c.tile(it) as? SpecialTile)?.kind == e.kind }
            push(Task.Move(p, forwardSteps(pos, target), e.collectSalary))
        }
        is CardEffect.MoveBack -> {
            val to = (pos - e.steps + BOARD_SIZE) % BOARD_SIZE
            upd(p) { it.copy(position = to) }
            emit(E.MOVED, "player" to p, "from" to pos, "to" to to, "steps" to -e.steps)
            push(Task.Land(p))
        }
        CardEffect.GoToJail -> sendToJail(p)
        is CardEffect.GainFromBank -> gainFromBank(p, e.amount, "card")
        is CardEffect.PayToVault -> push(Task.Pay(p, Creditor.Vault, e.amount, "card"))
        is CardEffect.PayToBank -> push(Task.Pay(p, Creditor.Bank, e.amount, "card"))
        is CardEffect.PayPerBuilding -> {
            val (houses, hqs) = Economy.buildingCount(s, p)
            val amount = houses * e.perSafeHouse + hqs * e.perHq
            if (amount > 0) push(Task.Pay(p, Creditor.Bank, amount, "repairs"))
        }
        is CardEffect.PayPercentOfCashToVault -> {
            val amount = player(p).money * e.percent / 100
            if (amount > 0) push(Task.Pay(p, Creditor.Vault, amount, "audit"))
        }
        is CardEffect.PayEachOtherPlayer ->
            pushAll(othersFromLeft(p).map { Task.Pay(p, Creditor.Player(it), e.amount, "card") })
        is CardEffect.CollectFromEachOtherPlayer ->
            pushAll(othersFromLeft(p).map { Task.Pay(it, Creditor.Player(p), e.amount, "card") })
        is CardEffect.PeekRandomFromNeighbor ->
            neighbor(p, e.direction)?.let { peek(p, it, KnowledgeSource.INFORMANT, ownerSees = true) }
        CardEffect.PeekRandomFromRichest -> {
            val others = othersFromLeft(p)
            if (others.isNotEmpty()) {
                val max = others.maxOf { player(it).money }
                val tied = others.filter { player(it).money == max }
                if (tied.size == 1) peek(p, tied.first(), KnowledgeSource.WIRETAP, ownerSees = true)
                else setPending(PendingDecision.ChooseWiretapTarget(p, tied))
            }
        }
        is CardEffect.ShowOwnCardToNeighbor ->
            neighbor(p, e.direction)?.let { push(Task.RequestShow(p, it, null, ShowReason.COURIER)) }
        is CardEffect.ForceShowByType -> {
            val targets = when (e.targets) {
                TargetScope.LEFT -> listOfNotNull(leftOf(p))
                TargetScope.RIGHT -> listOfNotNull(rightOf(p))
                TargetScope.ALL_OTHERS -> othersFromLeft(p)
            }
            val reason = when {
                e.targets == TargetScope.ALL_OTHERS -> ShowReason.OPEN_TELEGRAM
                e.cardType == EvidenceType.SUSPECT -> ShowReason.INTELLIGENCE_REPORT
                e.cardType == EvidenceType.LOCATION -> ShowReason.CITY_MAP
                else -> ShowReason.OPEN_TELEGRAM
            }
            pushAll(targets.map { Task.RequestShow(it, p, e.cardType, reason) })
        }
        CardEffect.JailPass -> {
            upd(p) { it.copy(heldCards = it.heldCards + HeldCard(card.deck, card.id)) }
            emit(E.CARD_KEPT, "player" to p, "deck" to card.deck, "card" to card.id)
        }
        CardEffect.SkipNextSuggestion -> upd(p) { it.copy(skipNextSuggestion = true) }
        is CardEffect.ExtraSuggestion -> upd(p) { it.copy(extraSuggestionTurnsLeft = e.turns + 1) }
        is CardEffect.GainIntel -> changeIntel(p, e.amount, "card")
        is CardEffect.LoseIntel -> changeIntel(p, -e.amount, "card")
        is CardEffect.AddSuspicion -> changeSuspicion(p, e.amount, "card")
        is CardEffect.ReduceSuspicion -> changeSuspicion(p, -e.amount, "card")
    }
}

internal fun Tx.neighbor(p: PlayerId, direction: Direction): PlayerId? = when (direction) {
    Direction.LEFT -> leftOf(p)
    Direction.RIGHT -> rightOf(p)
}

// =====================================================================================
// Melihat & menunjukkan kartu Bukti
// =====================================================================================

/** [viewer] melihat 1 kartu acak dari tangan [target]. [ownerSees] = target juga tahu kartu mana. */
internal fun Tx.peek(viewer: PlayerId, target: PlayerId, source: KnowledgeSource, ownerSees: Boolean) {
    val hand = player(target).hand
    if (hand.isEmpty()) {
        emit(E.NO_CARD, "player" to target, "viewer" to viewer, "source" to source)
        return
    }
    val card = rng.pick(hand)
    learn(viewer, card, Holder.Player(target), source)
    emit(E.EVIDENCE_PEEKED, "viewer" to viewer, "target" to target, "source" to source)
    emit(
        E.CARD_SEEN, "viewer" to viewer, "holder" to target, "card" to card, "source" to source,
        visibleTo = if (ownerSees) listOf(viewer, target) else listOf(viewer),
    )
}

/** [shower] memilih sendiri kartu yang ditunjukkan kepada [viewer]. */
internal fun Tx.show(shower: PlayerId, viewer: PlayerId, card: CardId, source: KnowledgeSource) {
    learn(viewer, card, Holder.Player(shower), source)
    emit(E.EVIDENCE_SHOWN, "from" to shower, "to" to viewer, "source" to source)
    emit(E.CARD_SEEN, "viewer" to viewer, "holder" to shower, "card" to card, "source" to source, visibleTo = listOf(shower, viewer))
}

internal fun showSource(reason: ShowReason): KnowledgeSource = when (reason) {
    ShowReason.SECRET_CONFERENCE -> KnowledgeSource.SECRET_CONFERENCE
    ShowReason.COURIER -> KnowledgeSource.COURIER
    ShowReason.OPEN_TELEGRAM -> KnowledgeSource.OPEN_TELEGRAM
    ShowReason.INTELLIGENCE_REPORT -> KnowledgeSource.INTELLIGENCE_REPORT
    ShowReason.CITY_MAP -> KnowledgeSource.CITY_MAP
}

internal fun Tx.requestShow(t: Task.RequestShow) {
    if (t.shower == t.viewer || !isActive(t.shower) || !isActive(t.viewer)) return
    val allowed = player(t.shower).hand.filter { t.cardType == null || c.evidence(it).type == t.cardType }
    if (allowed.isEmpty()) {
        emit(E.NO_CARD, "player" to t.shower, "viewer" to t.viewer, "reason" to t.reason, "cardType" to t.cardType)
        return
    }
    setPending(PendingDecision.ShowCardTo(t.shower, t.viewer, allowed, t.reason))
}
