package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.GameEvents as E
import id.diran.lisbon1944.engine.model.AuctionState
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Creditor
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.Ownership
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.Task
import id.diran.lisbon1944.engine.model.TradeOffer
import id.diran.lisbon1944.engine.model.WinType

// =====================================================================================
// Lelang
// =====================================================================================

internal fun Tx.startAuction(tile: Int, order: List<PlayerId>) {
    if (s.ownership[tile] != null) return
    val bidders = order.filter(::isActive)
    if (bidders.isEmpty()) {
        emit(E.AUCTION_UNSOLD, "tile" to tile)
        return
    }
    s = s.copy(auction = AuctionState(tile, bidders, bidders, turnIdx = -1))
    emit(E.AUCTION_STARTED, "tile" to tile, "order" to bidders)
    auctionAdvance()
}

private fun Tx.auctionAdvance() {
    val a = s.auction ?: error("Tidak ada lelang")
    val candidates = a.stillIn.filter { it != a.highBidder }
    if (candidates.isEmpty()) {
        if (a.highBidder != null) auctionWon() else auctionUnsold()
        return
    }
    val size = a.order.size
    for (k in 1..size) {
        val idx = ((a.turnIdx + k) % size + size) % size
        val bidder = a.order[idx]
        if (bidder in candidates) {
            s = s.copy(auction = a.copy(turnIdx = idx))
            val minBid = if (a.highBidder == null) b.auctionStart else a.highBid + b.auctionStep
            setPending(PendingDecision.AuctionBid(bidder, a.tile, minBid))
            return
        }
    }
    error("Penawar lelang tidak ditemukan")
}

internal fun Tx.bid(p: PlayerId, amount: Int) {
    val a = s.auction ?: error("Tidak ada lelang")
    s = s.copy(auction = a.copy(highBid = amount, highBidder = p))
    emit(E.AUCTION_BID, "player" to p, "tile" to a.tile, "amount" to amount)
    auctionAdvance()
}

internal fun Tx.passBid(p: PlayerId) {
    val a = s.auction ?: error("Tidak ada lelang")
    s = s.copy(auction = a.copy(stillIn = a.stillIn - p))
    emit(E.AUCTION_PASS, "player" to p, "tile" to a.tile)
    auctionAdvance()
}

private fun Tx.auctionWon() {
    val a = s.auction ?: error("Tidak ada lelang")
    val winner = a.highBidder ?: error("Lelang tanpa pemenang")
    s = s.copy(auction = null)
    pay(winner, Creditor.Bank, a.highBid, "auction")
    setOwner(a.tile, Ownership(winner))
    emit(E.AUCTION_WON, "player" to winner, "tile" to a.tile, "amount" to a.highBid)
}

private fun Tx.auctionUnsold() {
    val a = s.auction ?: error("Tidak ada lelang")
    s = s.copy(auction = null)
    emit(E.AUCTION_UNSOLD, "tile" to a.tile)
}

// =====================================================================================
// Bangunan & gadai
// =====================================================================================

internal fun Tx.build(p: PlayerId, tile: Int) {
    val t = c.tile(tile) as PropertyTile
    val o = s.ownership.getValue(tile)
    val cost = Economy.buildCost(s, c, t.district)
    pay(p, Creditor.Bank, cost, "build")
    if (o.level == 4) {
        s = s.copy(hqStock = s.hqStock - 1, houseStock = s.houseStock + 4)
    } else {
        s = s.copy(houseStock = s.houseStock - 1)
    }
    setOwner(tile, o.copy(level = o.level + 1))
    emit(E.BUILT, "player" to p, "tile" to tile, "level" to o.level + 1, "cost" to cost)
}

internal fun Tx.sellBuilding(p: PlayerId, tile: Int) {
    val t = c.tile(tile) as PropertyTile
    val o = s.ownership.getValue(tile)
    if (o.level == Ownership.HQ_LEVEL) {
        s = s.copy(hqStock = s.hqStock + 1, houseStock = s.houseStock - 4)
    } else {
        s = s.copy(houseStock = s.houseStock + 1)
    }
    setOwner(tile, o.copy(level = o.level - 1))
    val refund = Economy.sellRefund(s, t.district)
    gainFromBank(p, refund, "sell_building")
    emit(E.BUILDING_SOLD, "player" to p, "tile" to tile, "level" to o.level - 1, "refund" to refund)
}

internal fun Tx.mortgage(p: PlayerId, tile: Int) {
    setOwner(tile, s.ownership.getValue(tile).copy(mortgaged = true))
    val value = Economy.mortgageValue(c, tile)
    gainFromBank(p, value, "mortgage")
    emit(E.MORTGAGED, "player" to p, "tile" to tile, "amount" to value)
}

internal fun Tx.unmortgage(p: PlayerId, tile: Int) {
    val cost = Economy.unmortgageCost(s, c, tile)
    pay(p, Creditor.Bank, cost, "unmortgage")
    setOwner(tile, s.ownership.getValue(tile).copy(mortgaged = false))
    emit(E.UNMORTGAGED, "player" to p, "tile" to tile, "amount" to cost)
}

// =====================================================================================
// Kasino
// =====================================================================================

internal fun Tx.casino(p: PlayerId, bet: Int) {
    upd(p) { it.copy(money = it.money - bet) }
    emit(E.CASINO_BET, "player" to p, "amount" to bet)
    val d1 = rng.rollDie()
    val d2 = rng.rollDie()
    emit(E.DICE_ROLLED, "player" to p, "d1" to d1, "d2" to d2, "purpose" to "casino")
    val payout = b.casinoPayout
    when {
        d1 + d2 == 7 -> {
            val vault = if (b.sevenWinsVault) s.casinoVault else 0
            s = s.copy(casinoVault = s.casinoVault - vault)
            upd(p) { it.copy(money = it.money + bet * payout.seven + vault) }
            emit(E.CASINO_RESULT, "player" to p, "result" to "seven", "won" to bet * payout.seven, "vault" to vault)
            changeSuspicion(p, b.suspicion.casinoWin, "casino")
        }
        d1 + d2 == 11 -> {
            upd(p) { it.copy(money = it.money + bet * payout.eleven) }
            emit(E.CASINO_RESULT, "player" to p, "result" to "eleven", "won" to bet * payout.eleven)
            changeSuspicion(p, b.suspicion.casinoWin, "casino")
        }
        d1 == d2 -> {
            upd(p) { it.copy(money = it.money + bet * payout.doubles) }
            emit(E.CASINO_RESULT, "player" to p, "result" to "doubles", "won" to bet * payout.doubles)
            changeSuspicion(p, b.suspicion.casinoWin, "casino")
        }
        else -> {
            s = s.copy(casinoVault = s.casinoVault + bet)
            emit(E.CASINO_RESULT, "player" to p, "result" to "lose", "lost" to bet)
        }
    }
}

// =====================================================================================
// Perdagangan
// =====================================================================================

internal fun Tx.executeTrade(o: TradeOffer) {
    if (o.giveMoney > 0) pay(o.from, Creditor.Player(o.to), o.giveMoney, "trade")
    if (o.takeMoney > 0) pay(o.to, Creditor.Player(o.from), o.takeMoney, "trade")
    o.giveProperties.forEach { setOwner(it, s.ownership.getValue(it).copy(ownerId = o.to)) }
    o.takeProperties.forEach { setOwner(it, s.ownership.getValue(it).copy(ownerId = o.from)) }
    o.giveHeldCards.forEach { moveHeldCard(o.from, o.to, it) }
    o.takeHeldCards.forEach { moveHeldCard(o.to, o.from, it) }
    o.giveEvidence.forEach { moveEvidence(o.from, o.to, it) }
    o.takeEvidence.forEach { moveEvidence(o.to, o.from, it) }
    emit(
        E.TRADE_ACCEPTED, "from" to o.from, "to" to o.to, "giveMoney" to o.giveMoney, "takeMoney" to o.takeMoney,
        "giveProperties" to o.giveProperties, "takeProperties" to o.takeProperties,
        "giveHeldCards" to o.giveHeldCards.size, "takeHeldCards" to o.takeHeldCards.size,
        "giveEvidence" to o.giveEvidence.size, "takeEvidence" to o.takeEvidence.size,
    )
}

private fun Tx.moveHeldCard(from: PlayerId, to: PlayerId, card: id.diran.lisbon1944.engine.model.HeldCard) {
    upd(from) { pl -> pl.copy(heldCards = pl.heldCards.toMutableList().also { it.remove(card) }) }
    upd(to) { it.copy(heldCards = it.heldCards + card) }
}

private fun Tx.moveEvidence(from: PlayerId, to: PlayerId, card: CardId) {
    upd(from) { it.copy(hand = it.hand - card) }
    upd(to) { it.copy(hand = it.hand + card) }
    forget(from, card)
    forget(to, card)
    learn(to, card, Holder.Player(to), KnowledgeSource.TRADE)
    learn(from, card, Holder.Player(to), KnowledgeSource.TRADE)
    emit(E.CARD_SEEN, "viewer" to to, "holder" to to, "card" to card, "source" to KnowledgeSource.TRADE, visibleTo = listOf(from, to))
}

// =====================================================================================
// Kebangkrutan
// =====================================================================================

internal fun Tx.goBankrupt(p: PlayerId, creditor: Creditor) {
    // 1. Semua bangunan dijual ke bank setengah harga.
    var cash = player(p).money
    var houses = s.houseStock
    var hqs = s.hqStock
    val owned = s.ownership.filterValues { it.ownerId == p }.keys.sorted()
    for (tile in owned) {
        val o = s.ownership.getValue(tile)
        if (o.level > 0) {
            val district = (c.tile(tile) as PropertyTile).district
            cash += Economy.sellRefund(s, district) * o.level
            if (o.level == Ownership.HQ_LEVEL) hqs += 1 else houses += o.level
            setOwner(tile, o.copy(level = 0))
        }
    }
    s = s.copy(houseStock = houses, hqStock = hqs)
    upd(p) { it.copy(money = cash) }
    emit(E.BANKRUPT, "player" to p, "creditor" to creditor)

    // 2. Uang, properti, dan Surat Bebas pindah ke kreditur.
    val pl = player(p)
    val target = if (creditor is Creditor.Player && player(creditor.id).bankrupt) Creditor.Bank else creditor
    if (pl.money > 0) pay(p, target, pl.money, "bankruptcy")
    val auctions = ArrayList<Int>()
    when (target) {
        is Creditor.Player -> {
            owned.forEach { setOwner(it, s.ownership.getValue(it).copy(ownerId = target.id)) }
            upd(target.id) { it.copy(heldCards = it.heldCards + pl.heldCards) }
        }
        Creditor.Bank, Creditor.Vault -> {
            owned.forEach { setOwner(it, null) }
            auctions += owned
            pl.heldCards.forEach { returnCard(it.deck, it.cardId) }
        }
    }
    upd(p) {
        it.copy(
            bankrupt = true, money = 0, jail = null, heldCards = emptyList(), freeSuggestions = 0,
            extraSuggestionTurnsLeft = 0,
        )
    }

    // 3. Kartu Bukti pemain bangkrut menjadi publik.
    s.players.forEach { viewer -> pl.hand.forEach { learn(viewer.id, it, Holder.Player(p), KnowledgeSource.BANKRUPTCY) } }
    emit(E.HAND_REVEALED, "player" to p, "cards" to pl.hand)

    // 4. Buang tugas milik pemain bangkrut; tawaran dagang yang melibatkannya batal.
    s = s.copy(tasks = s.tasks.filterNot { it.involves(p) })
    if (s.trade?.let { it.offer.from == p || it.offer.to == p } == true) s = s.copy(trade = null)

    val alive = activeIds()
    if (alive.size == 1) {
        win(WinType.ECONOMIC, alive)
        return
    }
    pushAll(auctions.map { Task.StartAuction(it, othersFromLeft(p)) })
    if (p == current) appendTask(Task.NextTurn)
}

/** Setelah menjual/menggadai saat RaiseFunds: bayar utang bila uang sudah cukup. */
internal fun Tx.retryDebt(pend: PendingDecision.RaiseFunds) {
    if (player(pend.decider).money >= pend.amountOwed) {
        pay(pend.decider, pend.creditor, pend.amountOwed, "debt")
    } else {
        setPending(pend)
    }
}
