package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.GameEvents as E
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Creditor
import id.diran.lisbon1944.engine.model.Guess
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.LieRecord
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.SuggestionSource
import id.diran.lisbon1944.engine.model.Task
import id.diran.lisbon1944.engine.model.WinResult
import id.diran.lisbon1944.engine.model.WinType

// =====================================================================================
// Dugaan
// =====================================================================================

internal fun Tx.suggest(p: PlayerId, offer: PendingDecision.SuggestionOffer, a: Action.Suggest) {
    val named = a.suspects + a.item + a.location
    if (offer.source == SuggestionSource.INTERROGATION && !b.intel.interrogationFree) {
        changeIntel(p, -b.intel.suggestionCost, "suggestion")
    }
    if (offer.source == SuggestionSource.INVESTIGATE) {
        if (offer.fixedLocation != null && a.location == offer.fixedLocation) {
            s = s.copy(propertySuggestionUsed = true)
            changeIntel(p, -b.intel.suggestionCost, "suggestion")
        } else if (player(p).freeSuggestions > 0) {
            upd(p) { it.copy(freeSuggestions = it.freeSuggestions - 1) }
        } else {
            upd(p) { it.copy(extraSuggestionTurnsLeft = 0) }
        }
    }
    if (a.suspects.size == 2) {
        upd(p) { it.copy(abilityUsed = true) }
        emit(E.ABILITY_USED, "player" to p, "faction" to player(p).faction)
    }
    val me = player(p)
    if (me.isDoubleAgent) s = s.copy(doubleAgentSteered = s.doubleAgentSteered + named.filter { it !in me.hand })
    emit(
        E.SUGGESTION_MADE, "player" to p, "suspects" to a.suspects, "item" to a.item, "location" to a.location,
        "source" to offer.source,
    )
    val ask = Task.SuggestionAsk(p, named, othersFromLeft(p))
    if (offer.source == SuggestionSource.INVESTIGATE) push(ask, Task.Investigate(p)) else push(ask)
}

internal fun Tx.suggestionAsk(t: Task.SuggestionAsk) {
    if (!isActive(t.asker)) return
    val remaining = t.remaining.filter(::isActive)
    if (remaining.isEmpty()) {
        gainFromBank(t.asker, b.suggestionNoCardReward, "strong_suggestion")
        emit(E.SUGGESTION_STRONG, "player" to t.asker)
        return
    }
    val q = remaining.first()
    val match = player(q).hand.filter { it in t.named }
    if (match.isEmpty()) {
        emit(E.SUGGESTION_PASS, "player" to q, "asker" to t.asker, visibleTo = if (b.publicPasses) null else listOf(q, t.asker))
        push(t.copy(remaining = remaining.drop(1)))
        return
    }
    setPending(PendingDecision.RevealForSuggestion(q, t.asker, match, t.named, remaining.drop(1)))
}

internal fun Tx.staySilent(p: PlayerId, pend: PendingDecision.RevealForSuggestion) {
    changeIntel(p, -b.intel.silenceCost, "silence")
    emit(E.SUGGESTION_SILENT, "player" to p, "asker" to pend.asker)
    push(Task.SuggestionAsk(pend.asker, pend.named, pend.remaining))
}

internal fun Tx.revealForSuggestion(p: PlayerId, pend: PendingDecision.RevealForSuggestion, card: CardId) {
    if (card !in pend.allowedCards) {
        upd(p) { it.copy(doubleAgentLieUsed = true) }
        s = s.copy(doubleAgentLies = s.doubleAgentLies + LieRecord(p, pend.asker, card, pend.named, s.actionCount))
        emit(E.DOUBLE_AGENT_LIE, "player" to p, "asker" to pend.asker, "card" to card, visibleTo = listOf(p))
    }
    learn(pend.asker, card, Holder.Player(p), KnowledgeSource.SUGGESTION)
    emit(E.SUGGESTION_SHOWN, "player" to p, "asker" to pend.asker)
    emit(
        E.CARD_SEEN, "viewer" to pend.asker, "holder" to p, "card" to card, "source" to KnowledgeSource.SUGGESTION,
        visibleTo = listOf(p, pend.asker),
    )
}

// =====================================================================================
// Dekode
// =====================================================================================

internal fun Tx.startDecode(asker: PlayerId) {
    val operator = rightOf(asker) ?: return
    s = s.copy(decode = id.diran.lisbon1944.engine.model.DecodeState(asker, operator))
    emit(E.DECODE_STARTED, "player" to asker, "operator" to operator)
    setPending(PendingDecision.DecodeOperatorPick(operator, asker))
}

internal fun Tx.decodeRoll() {
    val d = s.decode ?: error("Dekode tidak aktif")
    val die = rng.rollDie()
    emit(E.DECODE_ROLL, "player" to d.asker, "die" to die, "attempt" to d.extraRolls + 1)
    when {
        die == d.number -> decodeSuccess()
        d.extraRolls < b.decodeMaxExtraRolls && player(d.asker).money >= b.decodeExtraRollCost ->
            setPending(PendingDecision.DecodeContinue(d.asker, d.extraRolls, d.paid))
        else -> decodeFail()
    }
}

internal fun Tx.decodeExtraRoll() {
    val d = s.decode ?: error("Dekode tidak aktif")
    upd(d.asker) { it.copy(money = it.money - b.decodeExtraRollCost) }
    s = s.copy(decode = d.copy(extraRolls = d.extraRolls + 1, paid = d.paid + b.decodeExtraRollCost))
    emit(E.MONEY, "from" to d.asker, "to" to "escrow", "amount" to b.decodeExtraRollCost, "reason" to "decode")
    decodeRoll()
}

private fun Tx.decodeSuccess() {
    val d = s.decode ?: error("Dekode tidak aktif")
    s = s.copy(decode = null)
    if (d.paid > 0) emit(E.MONEY, "from" to "escrow", "to" to "bank", "amount" to d.paid, "reason" to "decode")
    emit(E.DECODE_SUCCESS, "player" to d.asker, "operator" to d.operator, "number" to d.number)
    changeIntel(d.asker, b.intel.decodeSuccess, "decode")
    if (player(d.operator).hand.isEmpty()) gainFromBank(d.asker, b.decodeEmptyHandReward, "decode")
    else peek(d.asker, d.operator, KnowledgeSource.DECODE, ownerSees = true)
}

internal fun Tx.decodeFail() {
    val d = s.decode ?: error("Dekode tidak aktif")
    s = s.copy(decode = null)
    if (d.paid > 0) {
        upd(d.operator) { it.copy(money = it.money + d.paid) }
        emit(E.MONEY, "from" to "escrow", "to" to d.operator, "amount" to d.paid, "reason" to "decode")
    }
    emit(E.DECODE_FAILED, "player" to d.asker, "operator" to d.operator, "number" to d.number)
}

// =====================================================================================
// Tuduhan
// =====================================================================================

internal fun Tx.resolveAccusation(p: PlayerId, guess: Guess) {
    val env = s.envelope
    if (guess.cards == env.cards) {
        emit(E.ACCUSATION_CORRECT, "player" to p, "suspect" to env.suspect, "location" to env.location, "item" to env.item)
        win(WinType.DETECTIVE, listOf(p))
        return
    }
    if (familyMode) {
        val penalty = minOf(player(p).money, b.familyMode.wrongAccusePenalty)
        if (penalty > 0) pay(p, Creditor.Vault, penalty, "wrong_accusation")
        emit(E.ACCUSATION_WRONG_FAMILY, "player" to p, "penalty" to penalty)
        emit(E.ACCUSATION_WRONG, "player" to p, "guess" to guess.cards, visibleTo = listOf(p))
        return
    }
    val penalty = player(p).money * Economy.percent(b.burnPenaltyFraction) / 100
    if (penalty > 0) pay(p, Creditor.Vault, penalty, "burn_penalty")
    upd(p) { it.copy(burned = true) }
    env.cards.forEach { learn(p, it, Holder.Envelope, KnowledgeSource.ACCUSATION) }
    emit(E.BURNED, "player" to p, "penalty" to penalty)
    emit(E.ACCUSATION_WRONG, "player" to p, "guess" to guess.cards, "envelope" to env.cards, visibleTo = listOf(p))
    val da = s.players.firstOrNull { it.isDoubleAgent && !it.bankrupt }
    if (s.config.doubleAgentVariant && da != null && da.id != p && guess.cards.any { it in s.doubleAgentSteered }) {
        win(WinType.DOUBLE_AGENT, listOf(da.id))
    }
}

// =====================================================================================
// Akhir permainan
// =====================================================================================

internal fun Tx.collectFinalGuesses(remaining: List<PlayerId>) {
    val todo = remaining.filter { val pl = player(it); !pl.bankrupt && !pl.burned && pl.finalGuess == null }
    if (todo.isEmpty()) return
    push(Task.CollectFinalGuesses(todo.drop(1)))
    setPending(PendingDecision.FinalGuess(todo.first()))
}

internal fun Tx.finishWar() {
    val scores = Scoring.compute(s, c)
    scores.filter { it.mission > 0 }.forEach { emit(E.MISSION_COMPLETED, "player" to it.player, "mission" to player(it.player).missionId, "points" to it.mission) }
    emit(E.SCORES, "totals" to scores.joinToString(",") { "${it.player.value}:${it.total}" })
    val da = s.players.firstOrNull { it.isDoubleAgent && !it.bankrupt }
    if (s.config.doubleAgentVariant && da != null) {
        win(WinType.DOUBLE_AGENT, listOf(da.id))
        return
    }
    val contenders = scores.filter { !player(it.player).bankrupt }
    val best = contenders.maxOf { it.total }
    var top = contenders.filter { it.total == best }
    if (top.size > 1) {
        val bestClues = top.maxOf { it.clues }
        top = top.filter { it.clues == bestClues }
    }
    if (top.size > 1) {
        val bestCash = top.maxOf { player(it.player).money }
        top = top.filter { player(it.player).money == bestCash }
    }
    win(WinType.WAR_END, top.map { it.player })
}

internal fun Tx.win(type: WinType, winners: List<PlayerId>) {
    s = s.copy(winner = WinResult(type, winners))
    emit(E.GAME_OVER, "type" to type, "winners" to winners)
    val env = s.envelope
    emit(E.ENVELOPE_REVEALED, "suspect" to env.suspect, "location" to env.location, "item" to env.item)
    s.players.forEach { pl ->
        pl.finalGuess?.let { emit(E.FINAL_GUESS_REVEALED, "player" to pl.id, "guess" to it.cards) }
        if (pl.isDoubleAgent) emit(E.DOUBLE_AGENT_REVEALED, "player" to pl.id)
    }
    s.doubleAgentLies.forEach {
        emit(E.DOUBLE_AGENT_LIE_REVEALED, "player" to it.liar, "asker" to it.asker, "card" to it.shown, "named" to it.named)
    }
}
