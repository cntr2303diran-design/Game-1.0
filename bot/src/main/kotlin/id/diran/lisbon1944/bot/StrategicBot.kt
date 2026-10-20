package id.diran.lisbon1944.bot

import id.diran.lisbon1944.bot.deduction.Combo
import id.diran.lisbon1944.bot.deduction.DeductionTracker
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.CardEffect
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.Deck
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.FrontEffect
import id.diran.lisbon1944.engine.model.OwnableTile
import id.diran.lisbon1944.engine.model.Ownership
import id.diran.lisbon1944.engine.model.PendingDecision as PD
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerView
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.TradeOffer
import id.diran.lisbon1944.engine.rng.GameRng

/**
 * Bot untuk ketiga tingkat. Perbedaan tingkat berasal dari [BotParams] (bot-config.json):
 * Easy = acak berbobot + aturan sederhana; Normal = ekonomi terarah + deduksi dasar;
 * Hard = deduksi penuh, ambang tuduhan dinamis, perdagangan berbasis nilai.
 *
 * Bot tidak pernah melihat GameState — hanya [PlayerView] dan daftar aksi legal. Setiap keputusan
 * divalidasi terhadap daftar legal; bila pilihan strategis tidak ada di daftar, dipakai aksi aman.
 */
class StrategicBot(
    override val level: BotLevel,
    private val params: BotParams,
    private val content: GameContent,
    private val tracker: DeductionTracker = DeductionTracker(content),
) : Bot {

    override fun decide(view: PlayerView, legal: List<Action>, rng: GameRng): Action {
        require(legal.isNotEmpty()) { "Bot dipanggil tanpa aksi legal" }
        val legalSet = legal.toHashSet()
        val decision = view.myDecision ?: return legal.first()
        if (params.randomness > 0 && rng.nextInt(1000) < params.randomness * 1000 && decision.allowsRandom()) {
            return weightedRandom(legal, rng)
        }
        val insight = Insight(view, content, tracker, params.deduction, legal)
        val wanted = runCatching { choose(decision, insight, legal, rng) }.getOrNull()
        // ProposeTrade tidak dienumerasi engine (parametrik) — disusun sesuai aturan tradeCheck di proposeTrade().
        if (wanted is Action.ProposeTrade && decision is PD.BusinessPhase) return wanted
        return wanted?.takeIf { it in legalSet } ?: safeDefault(legal)
    }

    /** Keputusan yang aman diacak (tidak untuk tebakan/tuduhan — Easy tetap butuh ≥ 0,95 untuk menuduh). */
    private fun PD.allowsRandom(): Boolean = when (this) {
        is PD.AccuseOffer, is PD.FinalGuess, is PD.RaiseFunds, is PD.BusinessPhase -> false
        else -> true
    }

    // =================================================================================
    // Pemilihan per keputusan
    // =================================================================================

    private fun choose(d: PD, i: Insight, legal: List<Action>, rng: GameRng): Action? {
        val me = i.me
        return when (d) {
            is PD.AwaitRoll -> Action.RollDice(me)
            is PD.JailChoice -> jail(i, legal)
            is PD.BuyOrAuction -> if (shouldBuy(i, d.tile, d.price)) Action.BuyProperty(me) else Action.DeclineBuy(me)
            is PD.AuctionBid -> auction(i, d)
            is PD.RentChoice -> rent(i, d)
            is PD.SuggestionOffer -> suggestion(i, d, legal, rng)
            is PD.RevealForSuggestion -> reveal(i, d)
            is PD.DecodeOperatorPick -> Action.DecodePickNumber(me, rng.nextInt(6) + 1)
            is PD.DecodeContinue -> decodeContinue(i)
            is PD.CasinoChoice -> casino(i, d, rng)
            is PD.BlackMarketChoice -> blackMarket(i, d)
            is PD.BlackMarketPickCard -> Action.BlackMarketChooseCard(me, i.mostKnown(d.allowed))
            is PD.AccuseOffer -> accuseOrNull(i, d.cost) ?: Action.DeclineAccuse(me)
            is PD.TradeResponse -> Action.RespondTrade(me, acceptTrade(i, d.offer))
            is PD.TelegramKeepChoice -> Action.ChooseTelegram(me, d.options.maxBy { telegramValue(i, it) })
            is PD.ShowCardTo -> Action.ChooseCardToShow(me, i.mostKnown(d.allowedCards))
            is PD.ChooseWiretapTarget -> Action.ChooseWiretapTarget(me, d.candidates.maxBy { i.deduction.unknownCount[it] ?: 0 })
            is PD.RaiseFunds -> raiseFunds(i, legal)
            is PD.FinalGuess -> finalGuess(i)
            is PD.BusinessPhase -> business(i, legal, rng)
        }
    }

    private fun reserve(i: Insight): Int = if (i.warStep >= params.lateStep) maxOf(params.reserve, params.lateReserve) else params.reserve

    // --- Penjara -------------------------------------------------------------------------
    private fun jail(i: Insight, legal: List<Action>): Action {
        val me = i.me
        if (i.warStep < params.jailPayBeforeStep) {
            legal.firstOrNull { it is Action.UseJailCard }?.let { return it }
            legal.firstOrNull { it is Action.UseResistanceAbility }?.let { return it }
            if (i.money - i.view.config.balance.jailFine >= reserve(i)) return Action.PayJailFine(me)
        }
        return Action.RollDice(me)
    }

    // --- Beli & lelang -------------------------------------------------------------------
    private fun shouldBuy(i: Insight, tile: Int, price: Int): Boolean {
        if (level == BotLevel.EASY) return i.money > price + params.buyMargin
        val after = i.money - price
        val district = i.districtOf(tile)
        val priority = district != null && district in params.priorityDistricts && i.warStep <= params.priorityUntilStep
        val strategic = i.completesSet(tile) || i.blocksOpponent(tile) || (district != null && i.ownedIn(district) > 0) || i.helpsMission(tile)
        return when {
            after < reserve(i) -> false
            priority || strategic -> true
            else -> after >= reserve(i) + params.buyMargin
        }
    }

    private fun maxBid(i: Insight, tile: Int): Int {
        val price = (content.tile(tile) as OwnableTile).price
        var value = price * params.auctionFactor
        if (i.completesSet(tile) || i.blocksOpponent(tile) || i.helpsMission(tile)) value *= 1.0 + params.setBonus
        return minOf(value.toInt(), i.money - reserve(i))
    }

    private fun auction(i: Insight, d: PD.AuctionBid): Action =
        if (d.minBid <= maxBid(i, d.tile)) Action.Bid(i.me, d.minBid) else Action.PassBid(i.me)

    // --- Sewa ----------------------------------------------------------------------------
    private fun rent(i: Insight, d: PD.RentChoice): Action {
        val me = i.me
        // Penyamaran untuk sewa besar.
        val disguise = i.legal.filterIsInstance<Action.PlayTactic>().firstOrNull()
        if (disguise != null && d.quote.amount >= maxOf(120, i.money / 5)) return disguise
        if (!d.quote.infoAllowed || d.allowedInfoCards.isEmpty()) return Action.PayRentMoney(me)
        val card = i.mostKnown(d.allowedInfoCards)
        val cheapLeak = i.knownCount(card) >= params.infoKnownBy
        val expensive = d.quote.amount > i.money * params.infoRentShare
        val mustSave = d.quote.amount > i.money
        return if (cheapLeak || expensive || mustSave) Action.PayRentInfo(me, card) else Action.PayRentMoney(me)
    }

    // --- Dugaan --------------------------------------------------------------------------
    private fun suggestion(i: Insight, d: PD.SuggestionOffer, legal: List<Action>, rng: GameRng): Action {
        val me = i.me
        if (rng.nextInt(1000) >= params.suggestChance * 1000) return Action.SkipSuggestion(me)
        if (level == BotLevel.EASY) {
            val options = legal.filterIsInstance<Action.Suggest>().filter { it.suspects.size == 1 }
            return if (options.isEmpty()) Action.SkipSuggestion(me) else options[rng.nextInt(options.size)]
        }
        val r = i.deduction
        val steer = i.view.me.isDoubleAgent
        // Simpan Intel untuk Tuduhan Akhir bila misteri hampir terpecahkan dan Dugaan ini memakan Intel.
        val bal = i.view.config.balance
        val costsIntel = (d.source == id.diran.lisbon1944.engine.model.SuggestionSource.INVESTIGATE && !d.freeLocation) ||
            (d.source == id.diran.lisbon1944.engine.model.SuggestionSource.INTERROGATION && !bal.intel.interrogationFree)
        if (!steer && costsIntel && r.bestProbability >= params.lateAccuseThreshold.coerceAtMost(params.accuseThreshold) &&
            i.view.me.intel - bal.intel.suggestionCost < bal.intel.accuseCost
        ) {
            return Action.SkipSuggestion(me)
        }
        fun pick(type: EvidenceType, count: Int = 1): List<CardId> {
            val cards = i.cardsOfType(type)
            val solved = r.solvedCard(cards)
            if (steer) {
                // Agen Ganda: arahkan Dugaan menjauh dari jawaban — sebut kartu yang paling kecil peluangnya di amplop.
                return cards.sortedWith(compareBy({ r.envelopeProbability(it) }, { it.value })).take(count)
            }
            if (solved != null && count == 1) {
                // Kategori terpecahkan: sebut kartu sendiri agar Dugaan memeriksa kategori lain tanpa membocorkan.
                val own = cards.filter { it in i.view.me.hand }
                return listOf(own.firstOrNull() ?: solved)
            }
            // Maksimalkan informasi: kartu dengan p(1−p) terbesar di antara kartu yang letaknya belum pasti.
            return cards.sortedWith(
                compareByDescending<CardId> { val p = r.envelopeProbability(it); p * (1 - p) }.thenBy { it.value },
            ).take(count)
        }
        val canDouble = i.view.me.faction == Faction.NKVD && !i.view.me.abilityUsed &&
            r.solvedCard(i.cardsOfType(EvidenceType.SUSPECT)) == null &&
            i.cardsOfType(EvidenceType.SUSPECT).count { r.envelopeProbability(it) > 0.0 } >= 3
        val suspects = pick(EvidenceType.SUSPECT, if (canDouble && !steer) 2 else 1).sortedBy { it.value }
        val item = pick(EvidenceType.ITEM).first()
        val location = d.fixedLocation?.takeIf { !d.freeLocation } ?: pick(EvidenceType.LOCATION).first()
        return Action.Suggest(me, suspects, item, location)
    }

    private fun reveal(i: Insight, d: PD.RevealForSuggestion): Action {
        val me = i.me
        val view = i.view
        if (view.me.isDoubleAgent && !view.me.doubleAgentLieUsed && d.asker == i.leader && d.asker != me) {
            // Bohong sekali: tunjukkan kartu yang TIDAK disebut, saat penanya adalah pemain terdepan.
            val lie = view.me.hand.filter { it !in d.named }
            if (lie.isNotEmpty()) return Action.RevealCard(me, i.mostKnown(lie))
        }
        val card = i.mostKnown(d.allowedCards)
        if (shouldStaySilent(i, d, card)) return Action.StaySilent(me)
        return Action.RevealCard(me, card)
    }

    /** Bungkam bila penanya belum tahu kartu itu dan Intel cukup tanpa mengorbankan Tuduhan. */
    private fun shouldStaySilent(i: Insight, d: PD.RevealForSuggestion, card: CardId): Boolean {
        if (params.silenceChance <= 0.0) return false
        val bal = i.view.config.balance
        val askerKnows = i.knownBy[card]?.contains(d.asker) == true
        if (askerKnows) return false
        val keep = if (i.warStep >= bal.accuseUnlockStep - 1 && !i.view.me.burned) bal.intel.accuseCost else 0
        if (i.view.me.intel - bal.intel.silenceCost < keep) return false
        // Lebih rela bungkam terhadap pemain terdepan.
        val chance = if (d.asker == i.leader) 1.0 else params.silenceChance
        return chance >= 1.0 || (i.view.me.intel + i.warStep) % 10 < chance * 10
    }

    private fun decodeContinue(i: Insight): Action {
        val cost = i.view.config.balance.decodeExtraRollCost
        val unsolved = i.deduction.bestProbability < 0.999
        return if (params.decodeExtraRolls && unsolved && i.money - cost >= reserve(i)) Action.DecodeRollExtra(i.me) else Action.DecodeStop(i.me)
    }

    private fun casino(i: Insight, d: PD.CasinoChoice, rng: GameRng): Action {
        val me = i.me
        val safe = i.suspicionRoom > i.view.config.balance.suspicion.casinoWin
        val ok = safe && i.view.casinoVault >= params.casinoMinVault && i.money - d.minBet >= reserve(i) &&
            rng.nextInt(1000) < params.casinoChance * 1000
        return if (ok) Action.CasinoBet(me, d.minBet) else Action.CasinoSkip(me)
    }

    private fun blackMarket(i: Insight, d: PD.BlackMarketChoice): Action {
        val me = i.me
        val bal = i.view.config.balance
        val smuggler = i.view.me.faction == Faction.SPANISH_SMUGGLER
        val price = if (smuggler) bal.smugglerBlackMarketPrice else bal.blackMarketPrice
        val unsolved = i.deduction.bestProbability < 0.9
        val safe = smuggler || i.suspicionRoom > bal.suspicion.blackMarket
        if (!unsolved || !safe || i.money - price < params.blackMarketReserve) return Action.BlackMarketSkip(me)
        return Action.BlackMarketBuy(me, d.candidates.maxBy { i.deduction.unknownCount[it] ?: 0 })
    }

    // --- Tuduhan & tebakan ---------------------------------------------------------------
    private fun accuseThreshold(i: Insight): Double {
        var t = params.accuseThreshold
        if (i.warStep >= params.lateAccuseStep) t = minOf(t, params.lateAccuseThreshold)
        if (i.warStep >= params.lateAccuseStep && i.losingBadly(params.desperateDeficit)) t = minOf(t, params.desperateAccuseThreshold)
        return t
    }

    private fun accuseOrNull(i: Insight, cost: Int): Action? {
        if (i.view.me.burned || i.money < cost) return null
        val (combo, p) = i.deduction.best ?: return null
        if (p < accuseThreshold(i)) return null
        return Action.Accuse(i.me, combo.suspect, combo.location, combo.item)
    }

    private fun finalGuess(i: Insight): Action {
        val best: Combo = i.deduction.best?.first ?: Combo(
            i.cardsOfType(EvidenceType.SUSPECT).first(),
            i.cardsOfType(EvidenceType.LOCATION).first(),
            i.cardsOfType(EvidenceType.ITEM).first(),
        )
        return Action.SubmitFinalGuess(i.me, best.suspect, best.location, best.item)
    }

    // --- Dagang --------------------------------------------------------------------------
    private fun propertyWorth(i: Insight, tile: Int, receiver: PlayerId): Double {
        val price = (content.tile(tile) as OwnableTile).price.toDouble()
        val half = if (i.view.ownership[tile]?.mortgaged == true) price / 2 else price
        return if (i.completesSet(tile, receiver)) half * (1.0 + params.setBonus) else half
    }

    private fun acceptTrade(i: Insight, o: TradeOffer): Boolean {
        val me = i.me
        val evidenceValue = 60.0
        var delta = (o.giveMoney - o.takeMoney).toDouble()
        delta += o.giveProperties.sumOf { propertyWorth(i, it, me) }
        delta -= o.takeProperties.sumOf { propertyWorth(i, it, o.from) }
        delta += (o.giveHeldCards.size - o.takeHeldCards.size) * 50.0
        delta += (o.giveEvidence.size - o.takeEvidence.size) * evidenceValue
        // Jangan sampai uang jatuh di bawah cadangan.
        if (i.money - o.takeMoney < reserve(i)) return false
        return delta > params.tradeAcceptMargin
    }

    private fun telegramValue(i: Insight, id: Int): Int {
        val card = content.card(Deck.TELEGRAM, id)
        val others = i.activeOpponents.size
        return card.effects.sumOf { e ->
            when (e) {
                is CardEffect.GainFromBank -> e.amount
                is CardEffect.CollectFromEachOtherPlayer -> e.amount * others
                is CardEffect.PayToBank -> -e.amount
                is CardEffect.PayToVault -> -e.amount
                is CardEffect.PayEachOtherPlayer -> -e.amount * others
                is CardEffect.PayPercentOfCashToVault -> -(i.money * e.percent / 100)
                is CardEffect.PayPerBuilding -> {
                    val (h, q) = i.view.ownership.values.filter { it.ownerId == i.me }
                        .fold(0 to 0) { acc, o -> if (o.level == Ownership.HQ_LEVEL) acc.first to acc.second + 1 else acc.first + o.level to acc.second }
                    -(h * e.perSafeHouse + q * e.perHq)
                }
                is CardEffect.MoveToTile -> if (e.tile == 0) 200 else 10
                CardEffect.JailPass -> 60
                is CardEffect.ForceShowByType -> 40
                is CardEffect.ExtraSuggestion -> 40
                CardEffect.SkipNextSuggestion -> -30
                CardEffect.GoToJail -> -80
                else -> 0
            }
        }
    }

    /** Nilai Kartu Taktik; null bila sebaiknya disimpan. */
    private fun playTactic(i: Insight, legal: List<Action>, rng: GameRng): Action? {
        val plays = legal.filterIsInstance<Action.PlayTactic>()
        if (plays.isEmpty()) return null
        if (level == BotLevel.EASY) return if (rng.nextInt(100) < 30) plays[rng.nextInt(plays.size)] else null
        val bal = i.view.config.balance
        val cap = bal.intel.max + if (i.view.me.faction == Faction.ABWEHR_DEFECTOR) bal.intel.defectorBonus else 0
        val unsolved = i.deduction.bestProbability < 0.9
        val leader = i.leader?.takeIf { it != i.me } ?: i.activeOpponents.maxByOrNull { i.projectedScore(it) }
        fun score(a: Action.PlayTactic): Double {
            val card = content.tactic(a.cardId)
            var v = 0.0
            for (e in card.effects) v += when (e) {
                is CardEffect.GainIntel -> if (i.view.me.intel + e.amount <= cap) 30.0 * e.amount else 5.0
                is CardEffect.ReduceSuspicion -> 12.0 * minOf(e.amount, i.view.me.suspicion)
                is CardEffect.AddSuspicion -> if (i.suspicionRoom > e.amount) -10.0 * e.amount else -500.0
                is CardEffect.GainFromBank -> e.amount / 3.0
                is CardEffect.GainPrestige -> e.amount * 6.0
                is CardEffect.ExtraSuggestion -> if (unsolved) 40.0 else 0.0
                CardEffect.PeekTarget -> if (unsolved) 25.0 + (i.deduction.unknownCount[a.targetPlayer] ?: 0) * 8.0 else -1.0
                is CardEffect.TargetSuspicion -> {
                    val t = i.view.opponents.first { it.id == a.targetPlayer }
                    val arrests = t.suspicion + e.amount >= bal.suspicion.limit
                    (if (a.targetPlayer == leader) 25.0 else 8.0) + if (arrests) 40.0 else 0.0
                }
                is CardEffect.StealFromTarget -> {
                    val t = i.view.opponents.first { it.id == a.targetPlayer }
                    minOf(e.amount, t.money) / 2.0 + if (a.targetPlayer == leader) 15.0 else 0.0
                }
                is CardEffect.SealTargetTile -> sealValue(i, listOf(a.targetTile!!), leader)
                is CardEffect.SealTargetDistrict -> {
                    val d = i.districtOf(a.targetTile!!)!!
                    sealValue(i, content.propertiesIn(d).map { it.index }, leader)
                }
                else -> 0.0
            }
            return v
        }
        val best = plays.maxByOrNull(::score) ?: return null
        return best.takeIf { score(it) >= 20.0 }
    }

    /** Nilai menyegel petak lawan: perkiraan sewa yang hilang (lebih tinggi bila milik pemimpin). */
    private fun sealValue(i: Insight, tiles: List<Int>, leader: PlayerId?): Double = tiles.sumOf { t ->
        val o = i.view.ownership[t] ?: return@sumOf 0.0
        if (o.ownerId == i.me || o.mortgaged) return@sumOf 0.0
        val tile = content.tile(t)
        val base = when (tile) {
            is PropertyTile -> when (o.level) {
                0 -> tile.baseRent.toDouble()
                Ownership.HQ_LEVEL -> tile.hqRent * 0.5
                else -> tile.baseRent * i.view.config.balance.levelMultipliers[o.level - 1] * 0.5
            }
            else -> 40.0
        }
        base / 4.0 * (if (o.ownerId == leader) 1.5 else 1.0)
    }

    private fun proposeTrade(i: Insight): Action? {
        if (params.tradeOfferFactor <= 0.0) return null
        val view = i.view
        val mine = i.me.value.toString()
        val lastTurn = view.log.indexOfLast { it.type == id.diran.lisbon1944.engine.GameEvents.TURN_STARTED && it.payload["player"] == mine }
        val alreadyProposed = view.log.drop(lastTurn.coerceAtLeast(0))
            .any { it.type == id.diran.lisbon1944.engine.GameEvents.TRADE_PROPOSED && it.payload["from"] == mine }
        if (alreadyProposed) return null
        val candidates = content.properties.filter { t ->
            val o = view.ownership[t.index] ?: return@filter false
            o.ownerId != i.me && o.ownerId in i.activeOpponents && i.completesSet(t.index) &&
                content.propertiesIn(t.district).all { (view.ownership[it.index]?.level ?: 0) == 0 }
        }
        val target = candidates.maxByOrNull { it.price } ?: return null
        val offer = (target.price * params.tradeOfferFactor).toInt()
        if (i.money - offer < reserve(i)) return null
        val owner = view.ownership.getValue(target.index).ownerId
        return Action.ProposeTrade(i.me, TradeOffer(from = i.me, to = owner, giveMoney = offer, takeProperties = listOf(target.index)))
    }

    // --- Utang ---------------------------------------------------------------------------
    private fun raiseFunds(i: Insight, legal: List<Action>): Action {
        legal.filterIsInstance<Action.SellBuilding>().minByOrNull { i.view.ownership[it.tile]?.level ?: 0 }?.let { return it }
        val mortgages = legal.filterIsInstance<Action.Mortgage>()
        if (mortgages.isNotEmpty()) {
            // Lepas dulu properti yang bukan bagian distrik lengkap, termurah lebih dulu.
            return mortgages.minWith(compareBy({ if (i.completesSet(it.tile)) 1 else 0 }, { (content.tile(it.tile) as OwnableTile).price }))
        }
        return legal.first { it is Action.DeclareBankruptcy }
    }

    // --- Fase Bisnis ---------------------------------------------------------------------
    private fun business(i: Insight, legal: List<Action>, rng: GameRng): Action {
        val me = i.me
        val view = i.view
        // 1. Tuduhan April 1945.
        val aprilFront = content.front(view.activeFront).effects.any { it is FrontEffect.AccuseFromAnywhere }
        if (aprilFront) accuseOrNull(i, view.config.balance.aprilAccuseCost)?.let { return it }

        // 2. Kemampuan MI6: intip pemain dengan kartu tak dikenal terbanyak.
        if (legal.any { it is Action.UseMi6Ability } && rng.nextInt(1000) < params.useAbility * 1000 && i.deduction.bestProbability < 0.9) {
            val target = legal.filterIsInstance<Action.UseMi6Ability>().maxBy { i.deduction.unknownCount[it.targetPlayer] ?: 0 }
            return target
        }

        // 2b. Kartu Taktik (maksimal satu per giliran — dijaga engine).
        playTactic(i, legal, rng)?.let { return it }

        // 3. Tawarkan pembelian properti lawan yang melengkapi distrik (maksimal satu tawaran per giliran).
        proposeTrade(i)?.let { return it }

        // 3b. Tebus gadai bila kaya.
        legal.filterIsInstance<Action.Unmortgage>().firstOrNull()?.let { a ->
            val half = (content.tile(a.tile) as OwnableTile).price / 2
            val cost = half + Math.ceil(half * i.view.config.balance.mortgageInterest).toInt()
            if (i.money - cost >= params.unmortgageReserve) return a
        }

        // 4. Bangun merata, termurah dulu, sebelum menjelang akhir perang (misi bangunan: terus membangun).
        val buildMission = i.mission?.condition is id.diran.lisbon1944.engine.model.MissionCondition.BuildingPoints
        if (i.warStep < params.stopBuildingStep || buildMission) {
            val builds = legal.filterIsInstance<Action.Build>()
            val best = builds.minWithOrNull(compareBy({ i.view.ownership[it.tile]?.level ?: 0 }, { -(content.tile(it.tile) as PropertyTile).baseRent }))
            if (best != null) {
                val district = (content.tile(best.tile) as PropertyTile).district
                val cost = view.config.balance.buildCost.getValue(district)
                if (i.money - cost >= params.buildReserve) return best
            }
        }
        return Action.EndTurn(me)
    }

    // =================================================================================
    // Cadangan
    // =================================================================================

    /** Easy: acak berbobot — aksi "pasif" lebih disukai agar permainan tetap mengalir. */
    private fun weightedRandom(legal: List<Action>, rng: GameRng): Action {
        val weights = legal.map { a ->
            when (a) {
                is Action.Suggest -> if (a.suspects.size == 1) 1 else 0
                is Action.Bid -> 2
                is Action.CasinoBet -> 1
                is Action.RevealCard, is Action.ChooseCardToShow, is Action.DecodePickNumber, is Action.ChooseTelegram -> 10
                else -> 10
            }
        }
        val total = weights.sum()
        if (total <= 0) return safeDefault(legal)
        var r = rng.nextInt(total)
        for ((k, w) in weights.withIndex()) {
            if (r < w) return legal[k]
            r -= w
        }
        return safeDefault(legal)
    }

    private fun safeDefault(legal: List<Action>): Action =
        legal.firstOrNull {
            it is Action.EndTurn || it is Action.DeclineBuy || it is Action.PassBid || it is Action.PayRentMoney ||
                it is Action.SkipSuggestion || it is Action.DecodeStop || it is Action.CasinoSkip ||
                it is Action.BlackMarketSkip || it is Action.DeclineAccuse || it is Action.RollDice
        } ?: legal.firstOrNull { it is Action.RespondTrade && !it.accept } ?: legal.first()
}
