package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.GameEvents as E
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Creditor
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Holder
import id.diran.lisbon1944.engine.model.KnowledgeSource
import id.diran.lisbon1944.engine.model.KnownFact
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.FrontEffect
import id.diran.lisbon1944.engine.model.FrontId
import id.diran.lisbon1944.engine.model.JailState
import id.diran.lisbon1944.engine.model.OwnableTile
import id.diran.lisbon1944.engine.model.PendingDecision
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.ShowReason
import id.diran.lisbon1944.engine.model.SpecialKind
import id.diran.lisbon1944.engine.model.SpecialTile
import id.diran.lisbon1944.engine.model.SuggestionSource
import id.diran.lisbon1944.engine.model.Task
import id.diran.lisbon1944.engine.model.TurnPhase
import id.diran.lisbon1944.engine.model.WarClockMode

/** Jalankan tugas sampai ada keputusan yang ditunggu atau permainan selesai. */
internal fun Tx.advance() {
    var guard = 0
    while (s.pending == null && s.winner == null) {
        val task = s.tasks.firstOrNull()
            ?: error("Engine macet: tidak ada keputusan maupun tugas tersisa (aksi #${s.actionCount})")
        if (s.warEnded && task !is Task.CollectFinalGuesses && task !is Task.FinishWar) {
            divertIfWarEnded()
            continue
        }
        s = s.copy(tasks = s.tasks.drop(1))
        exec(task)
        check(++guard < 100_000) { "Engine berputar tanpa henti" }
    }
    if (s.winner != null) {
        s = s.copy(pending = null, tasks = emptyList(), auction = null, trade = null, decode = null, phase = TurnPhase.ENDED)
    }
}

internal fun Tx.exec(t: Task) {
    when (t) {
        is Task.StartTurn -> startTurn(t.player)
        is Task.Move -> moveStep(t)
        is Task.PassStart -> passStart(t.player, t.salary)
        is Task.EmbassyPass -> embassyPass(t.player)
        is Task.Land -> land(t.player, t.rentMultiplier)
        is Task.Investigate -> investigate(t.player)
        is Task.Business -> business(t.player)
        Task.NextTurn -> nextTurn()
        is Task.ResolveCard -> resolveCard(t)
        is Task.ReturnCard -> returnCard(t.deck, t.cardId)
        is Task.Pay -> payOrRaise(t.from, t.to, t.amount, t.reason)
        is Task.RequestShow -> requestShow(t)
        is Task.SuggestionAsk -> suggestionAsk(t)
        is Task.StartAuction -> startAuction(t.tile, t.order)
        is Task.CollectFinalGuesses -> collectFinalGuesses(t.remaining)
        Task.FinishWar -> finishWar()
    }
}

/** Tugas yang "milik" seorang pemain — dibuang saat pemain itu bangkrut. */
internal fun Task.involves(p: PlayerId): Boolean = when (this) {
    is Task.StartTurn -> player == p
    is Task.Move -> player == p
    is Task.PassStart -> player == p
    is Task.EmbassyPass -> player == p
    is Task.Land -> player == p
    is Task.Investigate -> player == p
    is Task.Business -> player == p
    is Task.ResolveCard -> player == p
    is Task.Pay -> from == p
    is Task.RequestShow -> shower == p || viewer == p
    is Task.SuggestionAsk -> asker == p
    is Task.ReturnCard, is Task.StartAuction, is Task.CollectFinalGuesses, Task.NextTurn, Task.FinishWar -> false
}

/** Tugas gerak/aksi petak yang batal ketika pemain masuk Penjara. */
private fun Task.isMovementOf(p: PlayerId): Boolean = when (this) {
    is Task.Move -> player == p
    is Task.PassStart -> player == p
    is Task.EmbassyPass -> player == p
    is Task.Land -> player == p
    is Task.ResolveCard -> player == p
    else -> false
}

// =====================================================================================
// Giliran
// =====================================================================================

internal fun Tx.startTurn(p: PlayerId) {
    if (divertIfWarEnded()) return
    if (player(p).bankrupt) {
        s = s.copy(currentPlayerIdx = p.value, doublesCount = 0)
        push(Task.NextTurn)
        return
    }
    s = s.copy(currentPlayerIdx = p.value, phase = TurnPhase.MOVE, propertySuggestionUsed = false)
    upd(p) {
        it.copy(
            suggestionBlockedThisTurn = it.skipNextSuggestion,
            skipNextSuggestion = false,
            extraSuggestionTurnsLeft = (it.extraSuggestionTurnsLeft - 1).coerceAtLeast(0),
        )
    }
    emit(E.TURN_STARTED, "player" to p, "round" to s.roundNumber)
    val jail = player(p).jail
    setPending(if (jail != null) PendingDecision.JailChoice(p, jail.turnsServed) else PendingDecision.AwaitRoll(p))
}

internal fun Tx.nextTurn() {
    if (divertIfWarEnded()) return
    val cur = current
    val cp = player(cur)
    if (!cp.bankrupt && !cp.inJail && s.doublesCount > 0) {
        push(Task.StartTurn(cur))
        return
    }
    s = s.copy(doublesCount = 0)
    var newRound = false
    var next: PlayerId? = null
    for (seat in clockwiseOthers(cur)) {
        if (seat.value == s.firstPlayerIdx) newRound = true
        if (isActive(seat)) {
            next = seat
            break
        }
    }
    push(Task.StartTurn(next ?: cur))
    if (newRound) {
        s = s.copy(roundNumber = s.roundNumber + 1, warClockAdvancedThisRound = false)
        emit(E.ROUND_STARTED, "round" to s.roundNumber)
        if (s.config.clockMode == WarClockMode.EVERY_ROUND && s.warStep < LAST_FRONT) revealFront(s.warStep + 1)
    }
}

internal fun Tx.investigate(p: PlayerId) {
    if (divertIfWarEnded()) return
    if (player(p).bankrupt) return
    s = s.copy(phase = TurnPhase.INVESTIGATE)
    val offer = investigateOffer(p)
    if (offer == null) push(Task.Business(p)) else setPending(offer)
}

/** Hak Dugaan pada fase Penyelidikan: properti sendiri, Dugaan gratis Front, atau Rekrut Informan. */
internal fun Tx.investigateOffer(p: PlayerId): PendingDecision.SuggestionOffer? {
    if (!canSuggest(p)) return null
    val pl = player(p)
    val tile = c.tile(pl.position)
    val o = s.ownership[pl.position]
    val fixed = if (tile is PropertyTile && o != null && o.ownerId == p && !o.mortgaged && !s.propertySuggestionUsed) {
        c.locationCardFor(tile.district).id
    } else {
        null
    }
    val free = pl.freeSuggestions > 0 || pl.extraSuggestionTurnsLeft > 0
    val affordable = fixed?.takeIf { pl.intel >= b.intel.suggestionCost }
    if (affordable == null && !free) return null
    if (affordable == null) return PendingDecision.SuggestionOffer(p, freeLocation = true, fixedLocation = null, source = SuggestionSource.INVESTIGATE)
    return PendingDecision.SuggestionOffer(p, freeLocation = free, fixedLocation = fixed, source = SuggestionSource.INVESTIGATE)
}

internal fun Tx.canSuggest(p: PlayerId): Boolean {
    val pl = player(p)
    return !pl.bankrupt && !pl.inJail && !pl.suggestionBlockedThisTurn
}

internal fun Tx.business(p: PlayerId) {
    if (divertIfWarEnded()) return
    if (player(p).bankrupt) return
    s = s.copy(phase = TurnPhase.BUSINESS)
    setPending(PendingDecision.BusinessPhase(p))
}

// =====================================================================================
// Gerak
// =====================================================================================

internal fun Tx.moveStep(t: Task.Move) {
    val pl = player(t.player)
    if (pl.bankrupt) return
    if (!t.started) {
        emit(E.MOVED, "player" to t.player, "from" to pl.position, "to" to (pl.position + t.steps) % BOARD_SIZE, "steps" to t.steps)
        s = s.copy(phase = TurnPhase.TILE_ACTION)
    }
    val to = (pl.position + 1) % BOARD_SIZE
    upd(t.player) { it.copy(position = to) }
    val remaining = t.steps - 1
    val next = ArrayList<Task>(3)
    if (to == 0) next += Task.PassStart(t.player, t.collectSalary)
    if (to == EMBASSY_TILE && remaining > 0) next += Task.EmbassyPass(t.player)
    next += if (remaining > 0) t.copy(steps = remaining, started = true) else Task.Land(t.player, t.rentMultiplier, t.freeAccuse)
    pushAll(next)
}

/** Jam Perang maju lebih dulu, baru gaji — Front yang baru dibalik langsung berlaku (DECISIONS D17). */
internal fun Tx.passStart(p: PlayerId, salary: Boolean) {
    if (player(p).bankrupt) return
    when (s.config.clockMode) {
        WarClockMode.FIRST_PASS_PER_ROUND -> if (!s.warClockAdvancedThisRound && s.warStep < LAST_FRONT) {
            s = s.copy(warClockAdvancedThisRound = true)
            revealFront(s.warStep + 1)
        }
        WarClockMode.START_PASSES -> if (s.warStep < LAST_FRONT) {
            val passes = s.startPasses + 1
            s = s.copy(startPasses = passes)
            val needed = s.config.passesNeededFor(s.warStep + 1)
            if (passes >= needed) revealFront(s.warStep + 1) else emit(E.WAR_CLOCK, "passes" to passes, "needed" to needed)
        }
        WarClockMode.EVERY_ROUND -> Unit
    }
    if (s.warEnded || player(p).inJail || player(p).bankrupt) return
    changeIntel(p, b.intel.perStartPass, "start")
    changeSuspicion(p, -b.suspicion.decayPerStartPass, "start")
    if (!salary) return
    if (frontEffects().any { it is FrontEffect.NoStartSalary }) {
        emit(E.SALARY_BLOCKED, "player" to p, "front" to s.activeFront.value)
    } else {
        gainFromBank(p, if (player(p).faction == Faction.OSS) b.ossSalary else b.startSalary, "salary")
    }
}

internal fun Tx.embassyPass(p: PlayerId) {
    if (canAccuse(p, b.embassyPassCost)) setPending(PendingDecision.AccuseOffer(p, b.embassyPassCost))
}

internal fun Tx.canAccuse(p: PlayerId, cost: Int): Boolean {
    val pl = player(p)
    return !pl.bankrupt && (!pl.burned || familyMode) && pl.money >= cost && s.warStep >= b.accuseUnlockStep &&
        pl.intel >= b.intel.accuseCost
}

internal fun Tx.sendToJail(p: PlayerId) {
    upd(p) { it.copy(jail = JailState(0), position = JAIL_TILE, suspicion = 0) }
    if (p == current) s = s.copy(doublesCount = 0)
    s = s.copy(tasks = s.tasks.filterNot { it.isMovementOf(p) })
    emit(E.JAIL_ENTERED, "player" to p)
}

internal fun Tx.leaveJail(p: PlayerId, how: String) {
    upd(p) { it.copy(jail = null) }
    emit(E.JAIL_LEFT, "player" to p, "how" to how)
}

// =====================================================================================
// Aksi petak
// =====================================================================================

internal fun Tx.land(p: PlayerId, rentMultiplier: Int) {
    val pl = player(p)
    if (pl.bankrupt) return
    val tile = c.tile(pl.position)
    s = s.copy(phase = TurnPhase.TILE_ACTION)
    emit(E.LANDED, "player" to p, "tile" to tile.index)
    when (tile) {
        is OwnableTile -> landOwnable(p, tile, rentMultiplier)
        is SpecialTile -> landSpecial(p, tile)
    }
}

private fun Tx.landOwnable(p: PlayerId, tile: OwnableTile, rentMultiplier: Int) {
    val o = s.ownership[tile.index]
    if (o == null) {
        setPending(PendingDecision.BuyOrAuction(p, tile.index, Economy.purchasePrice(s, c, p, tile.index)))
        return
    }
    if (o.ownerId == p) return
    val q = Economy.quote(s, c, tile.index, p, rentMultiplier) ?: return
    if (q.amount <= 0) {
        emit(E.NO_RENT, "player" to p, "tile" to tile.index)
        return
    }
    val allowed = if (q.infoAllowed) Economy.allowedInfoCards(s, c, q, p) else emptyList()
    setPending(PendingDecision.RentChoice(p, q, allowed))
}

private fun Tx.landSpecial(p: PlayerId, tile: SpecialTile) {
    val pl = player(p)
    when (tile.kind) {
        SpecialKind.START, SpecialKind.JAIL -> Unit
        SpecialKind.TELEGRAM -> draw(p, Deck.TELEGRAM)
        SpecialKind.CIPHER -> draw(p, Deck.CIPHER)
        SpecialKind.WAR_TAX -> push(Task.Pay(p, Creditor.Vault, b.warTax, "war_tax"))
        SpecialKind.BRIBE -> {
            changeSuspicion(p, -b.suspicion.bribeRelief, "bribe")
            push(Task.Pay(p, Creditor.Vault, b.bribe, "bribe"))
        }
        SpecialKind.RADIO, SpecialKind.TELEGRAPH -> startDecode(p)
        SpecialKind.INTERROGATION ->
            if (canSuggest(p) && (b.intel.interrogationFree || pl.intel >= b.intel.suggestionCost)) {
                setPending(PendingDecision.SuggestionOffer(p, freeLocation = true, source = SuggestionSource.INTERROGATION))
            }
        SpecialKind.CASINO ->
            if (!familyMode && pl.money >= b.casinoMinBet) {
                setPending(PendingDecision.CasinoChoice(p, b.casinoMinBet, b.casinoMaxBet, b.casinoBetStep))
            }
        SpecialKind.BLACK_MARKET -> if (!familyMode && pl.money >= blackMarketPrice(p)) {
            val candidates = othersFromLeft(p).filter { player(it).hand.isNotEmpty() }
            if (candidates.isNotEmpty()) setPending(PendingDecision.BlackMarketChoice(p, candidates))
        }
        SpecialKind.ARREST -> sendToJail(p)
        SpecialKind.EMBASSY -> if (canAccuse(p, 0)) setPending(PendingDecision.AccuseOffer(p, 0))
    }
}

// =====================================================================================
// Pembayaran & utang
// =====================================================================================

internal fun Tx.payOrRaise(from: PlayerId, to: Creditor, amount: Int, reason: String) {
    if (amount <= 0) return
    val pl = player(from)
    if (pl.bankrupt) return
    if (pl.money >= amount) {
        pay(from, to, amount, reason)
    } else {
        emit(E.DEBT, "player" to from, "to" to to, "amount" to amount, "reason" to reason)
        setPending(PendingDecision.RaiseFunds(from, amount, to))
    }
}

// =====================================================================================
// Front & Jam Perang
// =====================================================================================

internal fun Tx.revealFront(step: Int) {
    val front = c.front(FrontId(step))
    s = s.copy(warStep = step, activeFront = front.id)
    val free = front.effects.filterIsInstance<FrontEffect.FreeSuggestionEachPlayer>().sumOf { it.count }
    s = s.copy(players = s.players.map { it.copy(infoPaymentsThisStep = 0, freeSuggestions = if (it.bankrupt) 0 else free) })
    emit(E.FRONT_REVEALED, "front" to step, "month" to front.month)
    val tasks = ArrayList<Task>()
    val startSeat = if (isActive(current)) current else leftOf(current) ?: current
    val order = (listOf(startSeat) + othersFromLeft(startSeat)).filter(::isActive)
    // Pembagian ulang dilakukan lebih dulu agar efek lain (mis. Konferensi Rahasia) memakai tangan baru.
    front.effects.filterIsInstance<FrontEffect.ReshuffleEvidence>().forEach { reshuffleEvidence(it.category) }
    for (e in front.effects) {
        when (e) {
            is FrontEffect.OnRevealPayIfOwnsInDistrict -> order
                .filter { id -> c.propertiesIn(e.district).any { s.ownership[it.index]?.ownerId == id } }
                .forEach { tasks += Task.Pay(it, Creditor.Bank, e.amount, "front") }
            is FrontEffect.OnRevealFactionBonus -> order
                .filter { player(it).faction == e.faction }
                .forEach { gainFromBank(it, e.amount, "front") }
            is FrontEffect.OnRevealJailIfMoneyAbove -> order
                .filter { player(it).money > e.threshold }
                .forEach { sendToJail(it) }
            FrontEffect.OnRevealSecretConference -> order.forEach { id ->
                leftOf(id)?.let { tasks += Task.RequestShow(id, it, null, ShowReason.SECRET_CONFERENCE) }
            }
            FrontEffect.EndGame -> s = s.copy(warEnded = true)
            is FrontEffect.DistrictRentMultiplier, is FrontEffect.EscapeRouteRentMultiplier,
            is FrontEffect.FreeSuggestionEachPlayer, is FrontEffect.CloseTile, is FrontEffect.BuildCostMultiplier,
            FrontEffect.NoStartSalary, FrontEffect.AccuseFromAnywhere, is FrontEffect.ReshuffleEvidence -> Unit
        }
    }
    pushAll(tasks)
}

/**
 * Map Merah berpindah: kartu kategori [type] di amplop dan di tangan pemain aktif dikumpulkan, dikocok, lalu dibagi
 * ulang (jumlah per pemain tetap). Pengetahuan semua pemain tentang kategori itu dihapus, kecuali Kedutaan dan
 * tangan pemain bangkrut yang terbuka.
 */
internal fun Tx.reshuffleEvidence(type: EvidenceType) {
    fun isType(card: CardId) = c.evidence(card).type == type
    val active = s.players.filter { !it.bankrupt }
    val counts = active.associate { it.id to it.hand.count(::isType) }
    val envCard = s.envelope.cards.first(::isType)
    val pool = rng.shuffled(listOf(envCard) + active.flatMap { pl -> pl.hand.filter(::isType) })
    val newEnv = pool.first()
    var k = 1
    val newHands = HashMap<PlayerId, List<CardId>>()
    active.forEach { pl ->
        val n = counts.getValue(pl.id)
        newHands[pl.id] = pl.hand.filterNot(::isType) + pool.subList(k, k + n)
        k += n
    }
    s = s.copy(
        envelope = when (type) {
            EvidenceType.SUSPECT -> s.envelope.copy(suspect = newEnv)
            EvidenceType.LOCATION -> s.envelope.copy(location = newEnv)
            EvidenceType.ITEM -> s.envelope.copy(item = newEnv)
        },
        players = s.players.map { pl -> newHands[pl.id]?.let { pl.copy(hand = it) } ?: pl },
        doubleAgentSteered = s.doubleAgentSteered.filterNot(::isType).toSet(),
    )
    val bankruptIds = s.players.filter { it.bankrupt }.map { Holder.Player(it.id) }.toSet()
    s = s.copy(
        knowledge = s.knowledge.mapIndexed { viewer, facts ->
            val kept = facts.filter { f -> !isType(f.cardId) || f.holder == Holder.Embassy || f.holder in bankruptIds }
            val own = newHands[PlayerId(viewer)].orEmpty().filter(::isType)
                .map { KnownFact(it, Holder.Player(PlayerId(viewer)), KnowledgeSource.OWN_HAND) }
            (kept + own).toSet()
        },
    )
    emit(E.EVIDENCE_RESHUFFLED, "category" to type, "front" to s.warStep)
}

/** Front #12 sudah dibalik: ganti sisa giliran dengan pengumpulan tebakan akhir lalu skor. */
internal fun Tx.divertIfWarEnded(): Boolean {
    if (!s.warEnded) return false
    s.tasks.filterIsInstance<Task.ReturnCard>().forEach { returnCard(it.deck, it.cardId) }
    s = s.copy(tasks = emptyList(), auction = null, trade = null, decode = null)
    emit(E.WAR_ENDED, "front" to s.warStep)
    val start = if (isActive(current)) current else leftOf(current) ?: current
    val order = (listOf(start) + othersFromLeft(start)).filter(::isActive)
    pushAll(listOf(Task.CollectFinalGuesses(order), Task.FinishWar))
    return true
}
