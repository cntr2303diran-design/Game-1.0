package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.RuleViolation
import id.diran.lisbon1944.engine.ViolationCode
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.Action as A
import id.diran.lisbon1944.engine.model.PendingDecision as PD
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.EscapeRouteTile
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.FrontEffect
import id.diran.lisbon1944.engine.model.GameMode
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.InfoBlockedReason
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PlayerState
import id.diran.lisbon1944.engine.model.TradeOffer
import id.diran.lisbon1944.engine.model.TurnPhase

/**
 * Satu sumber kebenaran untuk legalitas aksi. reduce() memakai [check]; legalActions() = [candidates] yang lolos [check].
 * Karena keduanya memakai predikat yang sama, "setiap aksi legal diterima, aksi di luar daftar ditolak" terjamin
 * selama [candidates] adalah superset semua aksi yang mungkin lolos (kecuali ProposeTrade — lihat DECISIONS.md D21).
 */
internal object Legality {

    private fun v(code: ViolationCode, message: String) = RuleViolation(code, message)

    /** Normalisasi aksi ke bentuk kanonik (urutan Tersangka pada Dugaan). */
    fun normalize(a: A): A = if (a is A.Suggest) a.copy(suspects = a.suspects.sortedBy { it.value }) else a

    fun legal(s: GameState, c: GameContent, p: PlayerId): List<A> = candidates(s, c, p).filter { check(s, c, it) == null }

    fun check(s: GameState, c: GameContent, a: A): RuleViolation? {
        if (s.winner != null || s.phase == TurnPhase.ENDED) return v(ViolationCode.GAME_OVER, "Permainan sudah berakhir")
        val pend = s.pending ?: return v(ViolationCode.NO_DECISION, "Tidak ada keputusan yang ditunggu")
        if (a.actorId.value !in s.players.indices) return v(ViolationCode.INVALID_TARGET, "Pemain ${a.actorId.value} tidak ada")
        if (a.actorId != pend.decider) {
            return v(ViolationCode.NOT_YOUR_DECISION, "Sekarang menunggu keputusan pemain ${pend.decider.value}")
        }
        val me = s.players[a.actorId.value]
        val b = s.config.balance
        return when (pend) {
            is PD.AwaitRoll -> when (a) {
                is A.RollDice -> null
                is A.UseEscapeRoute -> escapeRouteCheck(s, c, me, a.targetTile)
                else -> wrong(s, c, me, a, pend)
            }
            is PD.JailChoice -> when (a) {
                is A.RollDice -> null
                is A.PayJailFine -> funds(me, b.jailFine)
                is A.UseJailCard -> if (me.heldCards.isNotEmpty()) null else v(ViolationCode.NO_JAIL_CARD, "Tidak punya Surat Bebas Diplomatik")
                is A.UseResistanceAbility ->
                    if (me.faction == Faction.RESISTANCE && !me.abilityUsed) null
                    else v(ViolationCode.ABILITY_UNAVAILABLE, "Kemampuan Perlawanan tidak tersedia")
                else -> wrong(s, c, me, a, pend)
            }
            is PD.BuyOrAuction -> when (a) {
                is A.BuyProperty -> funds(me, pend.price)
                is A.DeclineBuy -> null
                else -> wrong(s, c, me, a, pend)
            }
            is PD.AuctionBid -> when (a) {
                is A.PassBid -> null
                is A.Bid -> when {
                    a.amount < pend.minBid -> v(ViolationCode.INVALID_AMOUNT, "Tawaran minimal ${pend.minBid}")
                    (a.amount - pend.minBid) % b.auctionStep != 0 -> v(ViolationCode.INVALID_AMOUNT, "Tawaran harus kelipatan ${b.auctionStep}")
                    else -> funds(me, a.amount)
                }
                else -> wrong(s, c, me, a, pend)
            }
            is PD.RentChoice -> when (a) {
                is A.PayRentMoney -> null
                is A.PayRentInfo -> when {
                    !pend.quote.infoAllowed -> infoBlocked(pend.quote.infoBlockedReason)
                    a.cardId !in pend.allowedInfoCards ->
                        v(ViolationCode.CARD_NOT_ALLOWED, "Kartu ${a.cardId} tidak boleh dipakai (Sel Mata-Mata mewajibkan kartu Lokasi)")
                    else -> null
                }
                else -> wrong(s, c, me, a, pend)
            }
            is PD.SuggestionOffer -> when (a) {
                is A.SkipSuggestion -> null
                is A.Suggest -> suggestCheck(c, me, pend, a)
                else -> wrong(s, c, me, a, pend)
            }
            is PD.RevealForSuggestion -> when (a) {
                is A.RevealCard -> when {
                    a.cardId in pend.allowedCards -> null
                    me.isDoubleAgent && !me.doubleAgentLieUsed && a.cardId in me.hand -> null
                    else -> v(ViolationCode.CARD_NOT_ALLOWED, "Harus menunjukkan salah satu kartu yang disebut")
                }
                is A.StaySilent ->
                    if (me.intel >= b.intel.silenceCost) null else v(ViolationCode.NOT_ENOUGH_INTEL, "Bungkam butuh ${b.intel.silenceCost} Intel")
                else -> wrong(s, c, me, a, pend)
            }
            is PD.DecodeOperatorPick -> when (a) {
                is A.DecodePickNumber -> if (a.n in 1..6) null else v(ViolationCode.INVALID_AMOUNT, "Angka Roda Sandi 1–6")
                else -> wrong(s, c, me, a, pend)
            }
            is PD.DecodeContinue -> when (a) {
                is A.DecodeStop -> null
                is A.DecodeRollExtra -> when {
                    pend.extraRollsUsed >= b.decodeMaxExtraRolls -> v(ViolationCode.INVALID_AMOUNT, "Maksimal ${b.decodeMaxExtraRolls} lemparan tambahan")
                    else -> funds(me, b.decodeExtraRollCost)
                }
                else -> wrong(s, c, me, a, pend)
            }
            is PD.CasinoChoice -> when (a) {
                is A.CasinoSkip -> null
                is A.CasinoBet -> when {
                    a.amount < pend.minBet || a.amount > pend.maxBet -> v(ViolationCode.INVALID_AMOUNT, "Taruhan ${pend.minBet}–${pend.maxBet}")
                    (a.amount - pend.minBet) % pend.step != 0 -> v(ViolationCode.INVALID_AMOUNT, "Taruhan kelipatan ${pend.step}")
                    else -> funds(me, a.amount)
                }
                else -> wrong(s, c, me, a, pend)
            }
            is PD.BlackMarketChoice -> when (a) {
                is A.BlackMarketSkip -> null
                is A.BlackMarketBuy ->
                    if (a.targetPlayer !in pend.candidates) v(ViolationCode.INVALID_TARGET, "Pemain itu tidak punya kartu")
                    else funds(me, if (me.faction == Faction.SPANISH_SMUGGLER) b.smugglerBlackMarketPrice else b.blackMarketPrice)
                else -> wrong(s, c, me, a, pend)
            }
            is PD.BlackMarketPickCard -> when (a) {
                is A.BlackMarketChooseCard -> if (a.cardId in pend.allowed) null else v(ViolationCode.CARD_NOT_ALLOWED, "Bukan kartu di tanganmu")
                else -> wrong(s, c, me, a, pend)
            }
            is PD.AccuseOffer -> when (a) {
                is A.DeclineAccuse -> null
                is A.Accuse -> accuseCheck(s, c, me, a, pend.cost)
                else -> wrong(s, c, me, a, pend)
            }
            is PD.TradeResponse -> when (a) {
                is A.RespondTrade -> null
                else -> wrong(s, c, me, a, pend)
            }
            is PD.TelegramKeepChoice -> when (a) {
                is A.ChooseTelegram -> if (a.keepCardId in pend.options) null else v(ViolationCode.CARD_NOT_ALLOWED, "Pilih salah satu dari dua Telegram")
                else -> wrong(s, c, me, a, pend)
            }
            is PD.ShowCardTo -> when (a) {
                is A.ChooseCardToShow -> if (a.cardId in pend.allowedCards) null else v(ViolationCode.CARD_NOT_ALLOWED, "Kartu itu tidak boleh ditunjukkan")
                else -> wrong(s, c, me, a, pend)
            }
            is PD.ChooseWiretapTarget -> when (a) {
                is A.ChooseWiretapTarget -> if (a.target in pend.candidates) null else v(ViolationCode.INVALID_TARGET, "Pilih salah satu pemain terkaya")
                else -> wrong(s, c, me, a, pend)
            }
            is PD.RaiseFunds -> when (a) {
                is A.SellBuilding -> Economy.sellCheck(s, c, me.id, a.tile)
                is A.Mortgage -> Economy.mortgageCheck(s, c, me.id, a.tile)
                is A.DeclareBankruptcy ->
                    if (Economy.canRaiseFunds(s, c, me.id)) v(ViolationCode.CAN_STILL_RAISE_FUNDS, "Masih bisa menjual bangunan atau menggadai")
                    else null
                else -> wrong(s, c, me, a, pend)
            }
            is PD.FinalGuess -> when (a) {
                is A.SubmitFinalGuess -> guessCheck(c, a.suspect, a.location, a.item)
                else -> wrong(s, c, me, a, pend)
            }
            is PD.BusinessPhase -> when (a) {
                is A.EndTurn -> null
                is A.Build -> Economy.buildCheck(s, c, me.id, a.tile)
                is A.SellBuilding -> Economy.sellCheck(s, c, me.id, a.tile)
                is A.Mortgage -> Economy.mortgageCheck(s, c, me.id, a.tile)
                is A.Unmortgage -> Economy.unmortgageCheck(s, c, me.id, a.tile)
                is A.UseMi6Ability -> mi6Check(s, me, a.targetPlayer)
                is A.ProposeTrade -> tradeCheck(s, c, me, a.offer)
                is A.Accuse ->
                    if (Economy.frontEffects(s, c).none { it is FrontEffect.AccuseFromAnywhere }) {
                        v(ViolationCode.ACCUSE_NOT_ALLOWED, "Tuduhan Akhir hanya di Kedutaan Netral (kecuali Front April 1945)")
                    } else {
                        accuseCheck(s, c, me, a, b.aprilAccuseCost)
                    }
                else -> wrong(s, c, me, a, pend)
            }
        }
    }

    /** Aksi tidak cocok dengan keputusan yang ditunggu — beri alasan sespesifik mungkin. */
    private fun wrong(s: GameState, c: GameContent, me: PlayerState, a: A, pend: PD): RuleViolation = when {
        a is A.Suggest && me.inJail -> v(ViolationCode.IN_JAIL, "Tidak boleh mengajukan Dugaan selama di Penjara")
        a is A.Suggest && me.suggestionBlockedThisTurn -> v(ViolationCode.SUGGESTION_BLOCKED, "Kode Dibakar: tidak boleh Dugaan giliran ini")
        a is A.Accuse && me.burned && s.config.mode != GameMode.FAMILY -> v(ViolationCode.ALREADY_BURNED, "Pemain Terbakar tidak boleh menuduh lagi")
        a is A.Accuse -> v(ViolationCode.ACCUSE_NOT_ALLOWED, "Tuduhan Akhir tidak tersedia sekarang")
        else -> v(ViolationCode.WRONG_DECISION, "Aksi ${a::class.simpleName} tidak sesuai keputusan ${pend::class.simpleName}")
    }

    private fun funds(me: PlayerState, amount: Int): RuleViolation? =
        if (me.money >= amount) null else v(ViolationCode.INSUFFICIENT_FUNDS, "Butuh $amount escudo, punya ${me.money}")

    private fun infoBlocked(reason: InfoBlockedReason?): RuleViolation = when (reason) {
        InfoBlockedReason.RENT_TOO_HIGH -> v(ViolationCode.INFO_RENT_TOO_HIGH, "Bayar informasi hanya untuk sewa ≤ 300")
        InfoBlockedReason.QUOTA_EXHAUSTED -> v(ViolationCode.INFO_QUOTA_EXHAUSTED, "Kuota bayar informasi pada langkah Jam Perang ini habis")
        InfoBlockedReason.EMPTY_HAND -> v(ViolationCode.INFO_EMPTY_HAND, "Tidak punya kartu Bukti")
        InfoBlockedReason.NOT_APPLICABLE, null -> v(ViolationCode.WRONG_DECISION, "Bayar informasi tidak berlaku")
    }

    private fun escapeRouteCheck(s: GameState, c: GameContent, me: PlayerState, target: Int): RuleViolation? {
        val here = me.position
        if (c.tile(here) !is EscapeRouteTile || s.ownership[here]?.ownerId != me.id) {
            return v(ViolationCode.ROUTE_NOT_AVAILABLE, "Harus berada di Jalur Pelarian milik sendiri")
        }
        if (c.board.getOrNull(target) !is EscapeRouteTile || target == here || s.ownership[target]?.ownerId != me.id) {
            return v(ViolationCode.ROUTE_NOT_AVAILABLE, "Tujuan harus Jalur Pelarian lain milik sendiri")
        }
        if (Economy.isClosed(s, c, target)) return v(ViolationCode.ROUTE_NOT_AVAILABLE, "Jalur tujuan sedang ditutup")
        return funds(me, s.config.balance.escapeRouteTravelCost)
    }

    private fun isType(c: GameContent, id: CardId, type: EvidenceType): Boolean =
        c.evidence.any { it.id == id && it.type == type }

    private fun suggestCheck(c: GameContent, me: PlayerState, pend: PD.SuggestionOffer, a: A.Suggest): RuleViolation? {
        if (a.suspects.size !in 1..2) return v(ViolationCode.INVALID_CARD, "Sebut 1 Tersangka (NKVD: 2)")
        if (a.suspects.any { !isType(c, it, EvidenceType.SUSPECT) }) return v(ViolationCode.INVALID_CARD, "Tersangka tidak valid")
        if (!isType(c, a.item, EvidenceType.ITEM)) return v(ViolationCode.INVALID_CARD, "Barang tidak valid")
        if (!isType(c, a.location, EvidenceType.LOCATION)) return v(ViolationCode.INVALID_CARD, "Lokasi tidak valid")
        if (a.suspects.size == 2) {
            if (a.suspects[0].value >= a.suspects[1].value) return v(ViolationCode.INVALID_CARD, "Dua Tersangka harus berbeda")
            if (me.faction != Faction.NKVD || me.abilityUsed) {
                return v(ViolationCode.ABILITY_UNAVAILABLE, "Hanya NKVD, sekali per game, yang boleh menyebut 2 Tersangka")
            }
        }
        if (!pend.freeLocation && a.location != pend.fixedLocation) {
            return v(ViolationCode.LOCATION_NOT_ALLOWED, "Lokasi harus distrik properti tempatmu berdiri")
        }
        return null
    }

    private fun guessCheck(c: GameContent, suspect: CardId, location: CardId, item: CardId): RuleViolation? = when {
        !isType(c, suspect, EvidenceType.SUSPECT) -> v(ViolationCode.INVALID_CARD, "Tersangka tidak valid")
        !isType(c, location, EvidenceType.LOCATION) -> v(ViolationCode.INVALID_CARD, "Lokasi tidak valid")
        !isType(c, item, EvidenceType.ITEM) -> v(ViolationCode.INVALID_CARD, "Barang tidak valid")
        else -> null
    }

    private fun accuseCheck(s: GameState, c: GameContent, me: PlayerState, a: A.Accuse, cost: Int): RuleViolation? {
        if (me.burned && s.config.mode != GameMode.FAMILY) return v(ViolationCode.ALREADY_BURNED, "Pemain Terbakar tidak boleh menuduh lagi")
        if (s.warStep < s.config.balance.accuseUnlockStep) {
            return v(ViolationCode.ACCUSE_LOCKED, "Tuduhan Akhir baru dibuka sejak langkah Jam Perang ${s.config.balance.accuseUnlockStep}")
        }
        if (me.intel < s.config.balance.intel.accuseCost) {
            return v(ViolationCode.NOT_ENOUGH_INTEL, "Tuduhan Akhir butuh ${s.config.balance.intel.accuseCost} Intel")
        }
        guessCheck(c, a.suspect, a.location, a.item)?.let { return it }
        return funds(me, cost)
    }

    private fun mi6Check(s: GameState, me: PlayerState, target: PlayerId): RuleViolation? {
        if (me.faction != Faction.MI6 || me.abilityUsed) return v(ViolationCode.ABILITY_UNAVAILABLE, "Kemampuan MI6 tidak tersedia")
        val t = s.players.getOrNull(target.value) ?: return v(ViolationCode.INVALID_TARGET, "Pemain tidak ada")
        if (t.id == me.id || t.bankrupt || t.hand.isEmpty()) return v(ViolationCode.INVALID_TARGET, "Target harus pemain aktif lain yang punya kartu")
        return null
    }

    fun tradeCheck(s: GameState, c: GameContent, me: PlayerState, o: TradeOffer): RuleViolation? {
        fun bad(msg: String) = v(ViolationCode.INVALID_TRADE, msg)
        if (o.from != me.id) return bad("Tawaran harus atas namamu sendiri")
        val other = s.players.getOrNull(o.to.value) ?: return bad("Pemain tujuan tidak ada")
        if (other.id == me.id || other.bankrupt) return bad("Tujuan harus pemain aktif lain")
        if (o.giveMoney < 0 || o.takeMoney < 0) return bad("Jumlah uang tidak boleh negatif")
        if (o.giveMoney > me.money) return bad("Uangmu tidak cukup")
        if (o.takeMoney > other.money) return bad("Uang ${other.name} tidak cukup")
        val allProps = o.giveProperties + o.takeProperties
        if (allProps.toSet().size != allProps.size) return bad("Properti ganda dalam tawaran")
        if (o.giveProperties.any { s.ownership[it]?.ownerId != me.id || !Economy.tradeable(s, c, it) }) {
            return bad("Properti yang diberikan harus milikmu dan tanpa bangunan di distriknya")
        }
        if (o.takeProperties.any { s.ownership[it]?.ownerId != other.id || !Economy.tradeable(s, c, it) }) {
            return bad("Properti yang diminta harus milik ${other.name} dan tanpa bangunan di distriknya")
        }
        if (!containsAll(me.heldCards, o.giveHeldCards)) return bad("Surat Bebas yang diberikan tidak kamu miliki")
        if (!containsAll(other.heldCards, o.takeHeldCards)) return bad("Surat Bebas yang diminta tidak dimiliki ${other.name}")
        if (o.giveEvidence.toSet().size != o.giveEvidence.size || !me.hand.containsAll(o.giveEvidence)) {
            return bad("Kartu Bukti yang diberikan harus ada di tanganmu")
        }
        if (o.takeEvidence.toSet().size != o.takeEvidence.size || !other.hand.containsAll(o.takeEvidence)) {
            return bad("Kartu Bukti yang diminta harus ada di tangan ${other.name}")
        }
        val empty = o.giveMoney == 0 && o.takeMoney == 0 && allProps.isEmpty() &&
            o.giveHeldCards.isEmpty() && o.takeHeldCards.isEmpty() && o.giveEvidence.isEmpty() && o.takeEvidence.isEmpty()
        if (empty) return bad("Tawaran kosong")
        return null
    }

    private fun <T> containsAll(have: List<T>, want: List<T>): Boolean {
        val pool = have.toMutableList()
        return want.all { pool.remove(it) }
    }

    // ---------------------------------------------------------------------------------
    // Kandidat aksi (superset dari aksi legal)
    // ---------------------------------------------------------------------------------

    fun candidates(s: GameState, c: GameContent, p: PlayerId): List<A> {
        val pend = s.pending ?: return emptyList()
        if (s.winner != null || pend.decider != p) return emptyList()
        val me = s.players[p.value]
        val b = s.config.balance
        return when (pend) {
            is PD.AwaitRoll -> listOf<A>(A.RollDice(p)) + c.escapeRoutes.map { A.UseEscapeRoute(p, it.index) }
            is PD.JailChoice -> listOf(A.RollDice(p), A.PayJailFine(p), A.UseJailCard(p), A.UseResistanceAbility(p))
            is PD.BuyOrAuction -> listOf(A.BuyProperty(p), A.DeclineBuy(p))
            is PD.AuctionBid -> listOf<A>(A.PassBid(p)) + amounts(pend.minBid, me.money, b.auctionStep).map { A.Bid(p, it) }
            is PD.RentChoice -> listOf<A>(A.PayRentMoney(p)) + me.hand.map { A.PayRentInfo(p, it) }
            is PD.SuggestionOffer -> listOf<A>(A.SkipSuggestion(p)) + suggestions(c, p, me)
            is PD.RevealForSuggestion -> me.hand.map { A.RevealCard(p, it) } + A.StaySilent(p)
            is PD.DecodeOperatorPick -> (1..6).map { A.DecodePickNumber(p, it) }
            is PD.DecodeContinue -> listOf(A.DecodeStop(p), A.DecodeRollExtra(p))
            is PD.CasinoChoice -> listOf<A>(A.CasinoSkip(p)) + amounts(pend.minBet, minOf(pend.maxBet, me.money), pend.step).map { A.CasinoBet(p, it) }
            is PD.BlackMarketChoice -> listOf<A>(A.BlackMarketSkip(p)) + pend.candidates.map { A.BlackMarketBuy(p, it) }
            is PD.BlackMarketPickCard -> pend.allowed.map { A.BlackMarketChooseCard(p, it) }
            is PD.AccuseOffer -> listOf<A>(A.DeclineAccuse(p)) + accusations(c, p)
            is PD.TradeResponse -> listOf(A.RespondTrade(p, true), A.RespondTrade(p, false))
            is PD.TelegramKeepChoice -> pend.options.map { A.ChooseTelegram(p, it) }
            is PD.ShowCardTo -> pend.allowedCards.map { A.ChooseCardToShow(p, it) }
            is PD.ChooseWiretapTarget -> pend.candidates.map { A.ChooseWiretapTarget(p, it) }
            is PD.RaiseFunds -> ownedTiles(s, p).flatMap { listOf(A.SellBuilding(p, it), A.Mortgage(p, it)) } + A.DeclareBankruptcy(p)
            is PD.FinalGuess -> combos(c).map { (sus, loc, itm) -> A.SubmitFinalGuess(p, sus, loc, itm) }
            is PD.BusinessPhase -> buildList {
                add(A.EndTurn(p))
                ownedTiles(s, p).forEach { t ->
                    add(A.Build(p, t)); add(A.SellBuilding(p, t)); add(A.Mortgage(p, t)); add(A.Unmortgage(p, t))
                }
                if (me.faction == Faction.MI6 && !me.abilityUsed) s.players.filter { it.id != p }.forEach { add(A.UseMi6Ability(p, it.id)) }
                if (Economy.frontEffects(s, c).any { it is FrontEffect.AccuseFromAnywhere }) addAll(accusations(c, p))
            }
        }
    }

    private fun amounts(min: Int, max: Int, step: Int): List<Int> =
        if (max < min || step <= 0) emptyList() else (min..max step step).toList()

    private fun ownedTiles(s: GameState, p: PlayerId): List<Int> =
        s.ownership.filterValues { it.ownerId == p }.keys.sorted()

    private fun ids(c: GameContent, type: EvidenceType): List<CardId> = c.evidenceOfType(type).map { it.id }

    private fun combos(c: GameContent): List<Triple<CardId, CardId, CardId>> {
        val sus = ids(c, EvidenceType.SUSPECT)
        val loc = ids(c, EvidenceType.LOCATION)
        val itm = ids(c, EvidenceType.ITEM)
        return sus.flatMap { a -> loc.flatMap { b -> itm.map { i -> Triple(a, b, i) } } }
    }

    private fun accusations(c: GameContent, p: PlayerId): List<A> =
        combos(c).map { (sus, loc, itm) -> A.Accuse(p, sus, loc, itm) }

    private fun suggestions(c: GameContent, p: PlayerId, me: PlayerState): List<A> {
        val sus = ids(c, EvidenceType.SUSPECT)
        val suspectSets = sus.map { listOf(it) } +
            if (me.faction == Faction.NKVD && !me.abilityUsed) {
                sus.flatMap { a -> sus.filter { it.value > a.value }.map { listOf(a, it) } }
            } else {
                emptyList()
            }
        val items = ids(c, EvidenceType.ITEM)
        val locations = ids(c, EvidenceType.LOCATION)
        return suspectSets.flatMap { set -> items.flatMap { i -> locations.map { l -> A.Suggest(p, set, i, l) } } }
    }
}
