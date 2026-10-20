package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.GameEvents as E
import id.diran.lisbon1944.engine.model.Action as A
import id.diran.lisbon1944.engine.model.PendingDecision as PD
import id.diran.lisbon1944.engine.model.Creditor
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.Guess
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.JailState
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.Ownership
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.SuggestionSource
import id.diran.lisbon1944.engine.model.Task
import id.diran.lisbon1944.engine.model.TradeState
import id.diran.lisbon1944.engine.model.TurnPhase

/** Terapkan aksi yang SUDAH lolos Legality.check terhadap keputusan [pend]. */
internal fun Tx.handle(pend: PD, a: A) {
    s = s.copy(pending = null)
    val p = a.actorId
    when (a) {
        is A.RollDice -> if (pend is PD.JailChoice) jailRoll(p) else roll(p)
        is A.UseEscapeRoute -> {
            pay(p, Creditor.Bank, b.escapeRouteTravelCost, "escape_route")
            val from = player(p).position
            upd(p) { it.copy(position = a.targetTile) }
            s = s.copy(doublesCount = 0, phase = TurnPhase.TILE_ACTION)
            emit(E.ESCAPE_TRAVEL, "player" to p, "from" to from, "to" to a.targetTile)
            push(Task.Land(p), Task.Investigate(p))
        }
        is A.PayJailFine -> {
            pay(p, Creditor.Bank, b.jailFine, "jail_fine")
            leaveJail(p, "fine")
            setPending(PD.AwaitRoll(p))
        }
        is A.UseJailCard -> {
            val card = player(p).heldCards.first()
            upd(p) { it.copy(heldCards = it.heldCards.drop(1)) }
            returnCard(card.deck, card.cardId)
            leaveJail(p, "card")
            setPending(PD.AwaitRoll(p))
        }
        is A.UseResistanceAbility -> {
            upd(p) { it.copy(abilityUsed = true) }
            emit(E.ABILITY_USED, "player" to p, "faction" to player(p).faction)
            leaveJail(p, "resistance")
            setPending(PD.AwaitRoll(p))
        }
        is A.BuyProperty -> {
            val d = pend as PD.BuyOrAuction
            pay(p, Creditor.Bank, d.price, "purchase")
            setOwner(d.tile, Ownership(p))
            emit(E.PROPERTY_BOUGHT, "player" to p, "tile" to d.tile, "price" to d.price)
        }
        is A.DeclineBuy -> {
            val d = pend as PD.BuyOrAuction
            emit(E.PURCHASE_DECLINED, "player" to p, "tile" to d.tile)
            push(Task.StartAuction(d.tile, othersFromLeft(p) + p))
        }
        is A.Bid -> bid(p, a.amount)
        is A.PassBid -> passBid(p)
        is A.PayRentMoney -> {
            val q = (pend as PD.RentChoice).quote
            push(Task.Pay(p, Creditor.Player(q.ownerId), q.amount, "rent"))
        }
        is A.PayRentInfo -> {
            val q = (pend as PD.RentChoice).quote
            upd(p) { it.copy(infoPaymentsThisStep = it.infoPaymentsThisStep + 1) }
            learn(q.ownerId, a.cardId, Holder.Player(p), KnowledgeSource.RENT_INFO)
            emit(E.RENT_PAID_INFO, "player" to p, "owner" to q.ownerId, "tile" to q.tile, "rent" to q.amount)
            emit(
                E.CARD_SEEN, "viewer" to q.ownerId, "holder" to p, "card" to a.cardId, "source" to KnowledgeSource.RENT_INFO,
                visibleTo = listOf(p, q.ownerId),
            )
        }
        is A.Suggest -> suggest(p, pend as PD.SuggestionOffer, a)
        is A.SkipSuggestion -> {
            emit(E.SUGGESTION_SKIPPED, "player" to p)
            if ((pend as PD.SuggestionOffer).source == SuggestionSource.INVESTIGATE) push(Task.Business(p))
        }
        is A.RevealCard -> revealForSuggestion(p, pend as PD.RevealForSuggestion, a.cardId)
        is A.StaySilent -> staySilent(p, pend as PD.RevealForSuggestion)
        is A.DecodePickNumber -> {
            s = s.copy(decode = s.decode!!.copy(number = a.n))
            emit(E.DECODE_NUMBER_PICKED, "operator" to p)
            decodeRoll()
        }
        is A.DecodeRollExtra -> decodeExtraRoll()
        is A.DecodeStop -> decodeFail()
        is A.CasinoBet -> casino(p, a.amount)
        is A.CasinoSkip -> emit(E.CASINO_SKIP, "player" to p)
        is A.BlackMarketBuy -> {
            pay(p, Creditor.Player(a.targetPlayer), blackMarketPrice(p), "black_market")
            emit(E.BLACK_MARKET_DEAL, "player" to p, "seller" to a.targetPlayer)
            // Penangkapan karena Kecurigaan tidak membatalkan transaksi: penjual tetap memilih kartu.
            if (player(p).faction != Faction.SPANISH_SMUGGLER) changeSuspicion(p, b.suspicion.blackMarket, "black_market")
            setPending(PD.BlackMarketPickCard(a.targetPlayer, p, player(a.targetPlayer).hand))
        }
        is A.BlackMarketChooseCard -> show(p, (pend as PD.BlackMarketPickCard).buyer, a.cardId, KnowledgeSource.BLACK_MARKET)
        is A.BlackMarketSkip -> emit(E.BLACK_MARKET_SKIP, "player" to p)
        is A.Accuse -> {
            val cost = if (pend is PD.AccuseOffer) pend.cost else b.aprilAccuseCost
            if (cost > 0) pay(p, Creditor.Bank, cost, "accusation")
            changeIntel(p, -b.intel.accuseCost, "accusation")
            resolveAccusation(p, Guess(a.suspect, a.location, a.item))
            if (pend is PD.BusinessPhase && s.winner == null) push(Task.Business(p))
        }
        is A.DeclineAccuse -> emit(E.ACCUSATION_DECLINED, "player" to p)
        is A.Build -> {
            build(p, a.tile)
            push(Task.Business(p))
        }
        is A.SellBuilding -> {
            sellBuilding(p, a.tile)
            afterFundsAction(p, pend)
        }
        is A.Mortgage -> {
            mortgage(p, a.tile)
            afterFundsAction(p, pend)
        }
        is A.Unmortgage -> {
            unmortgage(p, a.tile)
            push(Task.Business(p))
        }
        is A.ProposeTrade -> {
            s = s.copy(trade = TradeState(a.offer))
            emit(E.TRADE_PROPOSED, "from" to a.offer.from, "to" to a.offer.to)
            setPending(PD.TradeResponse(a.offer.to, a.offer))
        }
        is A.RespondTrade -> {
            val offer = (pend as PD.TradeResponse).offer
            if (a.accept) executeTrade(offer) else emit(E.TRADE_REJECTED, "from" to offer.from, "to" to offer.to)
            s = s.copy(trade = null)
            push(Task.Business(offer.from))
        }
        is A.UseMi6Ability -> {
            upd(p) { it.copy(abilityUsed = true) }
            emit(E.ABILITY_USED, "player" to p, "faction" to player(p).faction, "target" to a.targetPlayer)
            peek(p, a.targetPlayer, KnowledgeSource.MI6_ABILITY, ownerSees = false)
            push(Task.Business(p))
        }
        is A.ChooseTelegram -> {
            val d = pend as PD.TelegramKeepChoice
            d.options.filter { it != a.keepCardId }.forEach { returnCard(Deck.TELEGRAM, it) }
            emit(E.CARD_DRAWN, "player" to p, "deck" to Deck.TELEGRAM, "card" to a.keepCardId, "chosen" to true)
            startCard(p, Deck.TELEGRAM, a.keepCardId)
        }
        is A.ChooseCardToShow -> {
            val d = pend as PD.ShowCardTo
            show(p, d.target, a.cardId, showSource(d.reason))
        }
        is A.ChooseWiretapTarget -> peek(p, a.target, KnowledgeSource.WIRETAP, ownerSees = true)
        is A.SubmitFinalGuess -> {
            upd(p) { it.copy(finalGuess = Guess(a.suspect, a.location, a.item)) }
            emit(E.FINAL_GUESS_SUBMITTED, "player" to p)
        }
        is A.DeclareBankruptcy -> goBankrupt(p, (pend as PD.RaiseFunds).creditor)
        is A.EndTurn -> {
            emit(E.TURN_ENDED, "player" to p)
            checkOperations()
            if (s.winner == null) push(Task.NextTurn)
        }
        is A.PlayTactic -> {
            val card = c.tactic(a.cardId)
            if (pend is PD.RentChoice) {
                // Penyamaran: sewa batal, lanjutkan alur.
                playTactic(p, card, a.targetPlayer, a.targetTile)
            } else {
                push(Task.Business(p))
                playTactic(p, card, a.targetPlayer, a.targetTile)
            }
        }
    }
}

private fun Tx.afterFundsAction(p: PlayerId, pend: PD) {
    if (pend is PD.RaiseFunds) retryDebt(pend) else push(Task.Business(p))
}

private fun Tx.rollTwo(p: PlayerId): Pair<Int, Int> {
    val d1 = rng.rollDie()
    val d2 = rng.rollDie()
    emit(E.DICE_ROLLED, "player" to p, "d1" to d1, "d2" to d2)
    return d1 to d2
}

private fun Tx.roll(p: PlayerId) {
    val (d1, d2) = rollTwo(p)
    if (d1 == d2) {
        val count = s.doublesCount + 1
        s = s.copy(doublesCount = count)
        if (count >= 3) {
            emit(E.DOUBLES_TO_JAIL, "player" to p)
            sendToJail(p)
            push(Task.Investigate(p))
            return
        }
    } else {
        s = s.copy(doublesCount = 0)
    }
    s = s.copy(phase = TurnPhase.TILE_ACTION)
    push(Task.Move(p, d1 + d2, collectSalary = true), Task.Investigate(p))
}

private fun Tx.jailRoll(p: PlayerId) {
    val (d1, d2) = rollTwo(p)
    val served = (player(p).jail?.turnsServed ?: 0) + 1
    when {
        d1 == d2 -> {
            leaveJail(p, "doubles")
            s = s.copy(doublesCount = 0)
            push(Task.Move(p, d1 + d2, collectSalary = true), Task.Investigate(p))
        }
        served >= b.maxJailTurns -> {
            leaveJail(p, "forced")
            push(Task.Pay(p, Creditor.Bank, b.jailFine, "jail_fine"), Task.Move(p, d1 + d2, collectSalary = true), Task.Investigate(p))
        }
        else -> {
            upd(p) { it.copy(jail = JailState(served)) }
            emit(E.JAIL_STAY, "player" to p, "turnsServed" to served)
            push(Task.Investigate(p))
        }
    }
}
