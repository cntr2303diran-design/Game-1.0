package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.RuleViolation
import id.diran.lisbon1944.engine.ViolationCode
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.CardId
import id.diran.lisbon1944.engine.model.District
import id.diran.lisbon1944.engine.model.EscapeRouteTile
import id.diran.lisbon1944.engine.model.EvidenceType
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.FrontEffect
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.InfoBlockedReason
import id.diran.lisbon1944.engine.model.OwnableTile
import id.diran.lisbon1944.engine.model.Ownership
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.PropertyTile
import id.diran.lisbon1944.engine.model.RentQuote
import kotlin.math.roundToInt

internal fun violation(code: ViolationCode, message: String) = RuleViolation(code, message)

/** Aturan ekonomi murni (tanpa efek samping). Semua pembulatan ke bawah kecuali bunga tebus gadai. */
internal object Economy {

    /** Pecahan balance (0.10) → persen bulat (10) agar aritmetika tetap bilangan bulat. */
    fun percent(fraction: Double): Int = (fraction * 100).roundToInt()

    fun frontEffects(s: GameState, c: GameContent): List<FrontEffect> = c.front(s.activeFront).effects

    fun ownsDistrict(s: GameState, c: GameContent, owner: PlayerId, district: District): Boolean =
        c.propertiesIn(district).all { s.ownership[it.index]?.ownerId == owner }

    fun isClosed(s: GameState, c: GameContent, tile: Int): Boolean =
        frontEffects(s, c).any { it is FrontEffect.CloseTile && it.tile == tile }

    fun purchasePrice(s: GameState, c: GameContent, buyer: PlayerId, tile: Int): Int {
        val price = (c.tile(tile) as OwnableTile).price
        return if (s.players[buyer.value].faction == Faction.PORTUGAL_TRADER) {
            price * (100 - percent(s.config.balance.portugalDiscount)) / 100
        } else {
            price
        }
    }

    fun buildCost(s: GameState, c: GameContent, district: District): Int {
        var num = s.config.balance.buildCost.getValue(district).toLong()
        var den = 1L
        frontEffects(s, c).filterIsInstance<FrontEffect.BuildCostMultiplier>().forEach {
            num *= it.numerator
            den *= it.denominator
        }
        return (num / den).toInt()
    }

    /** Jual bangunan ke bank: setengah harga bangun DASAR (tanpa inflasi). */
    fun sellRefund(s: GameState, district: District): Int = s.config.balance.buildCost.getValue(district) / 2

    fun mortgageValue(c: GameContent, tile: Int): Int = (c.tile(tile) as OwnableTile).price / 2

    fun unmortgageCost(s: GameState, c: GameContent, tile: Int): Int {
        val half = mortgageValue(c, tile)
        val pct = percent(s.config.balance.mortgageInterest)
        return half + (half * pct + 99) / 100
    }

    /** Sewa final setelah semua pengali (BAGIAN 7): dasar/level → Sel Mata-Mata → kartu → Front, bulat ke bawah. */
    fun rentAmount(s: GameState, c: GameContent, tile: Int, cardMultiplier: Int = 1): Int {
        val o = s.ownership[tile] ?: return 0
        if (o.mortgaged || isClosed(s, c, tile)) return 0
        val b = s.config.balance
        val fx = frontEffects(s, c)
        return when (val t = c.tile(tile)) {
            is PropertyTile -> {
                val raw = when (o.level) {
                    0 -> t.baseRent * (if (ownsDistrict(s, c, o.ownerId, t.district)) b.monopolyBaseMultiplier else 1)
                    Ownership.HQ_LEVEL -> t.hqRent
                    else -> t.baseRent * b.levelMultipliers[o.level - 1]
                }
                var num = raw.toLong() * cardMultiplier
                var den = 1L
                fx.filterIsInstance<FrontEffect.DistrictRentMultiplier>()
                    .filter { t.district in it.districts }
                    .forEach { num *= it.numerator; den *= it.denominator }
                if (o.level > 0) {
                    num *= s.config.balance.buildingRentPercent
                    den *= 100
                }
                (num / den).toInt()
            }
            is EscapeRouteTile -> {
                val count = s.ownership.count { (i, ow) -> ow.ownerId == o.ownerId && c.tile(i) is EscapeRouteTile }
                var num = b.escapeRouteRent[count - 1].toLong() * cardMultiplier
                var den = 1L
                fx.filterIsInstance<FrontEffect.EscapeRouteRentMultiplier>().forEach { num *= it.numerator; den *= it.denominator }
                (num / den).toInt()
            }
            else -> 0
        }
    }

    fun quote(s: GameState, c: GameContent, tile: Int, payer: PlayerId, cardMultiplier: Int = 1): RentQuote? {
        val o = s.ownership[tile] ?: return null
        val amount = rentAmount(s, c, tile, cardMultiplier)
        val t = c.tile(tile)
        val forced = t is PropertyTile && ownsDistrict(s, c, o.ownerId, t.district)
        val pl = s.players[payer.value]
        val reason = when {
            payer == o.ownerId -> InfoBlockedReason.NOT_APPLICABLE
            amount > s.config.balance.infoPaymentMaxRent -> InfoBlockedReason.RENT_TOO_HIGH
            pl.infoPaymentsThisStep >= s.config.infoPaymentQuota -> InfoBlockedReason.QUOTA_EXHAUSTED
            pl.hand.isEmpty() -> InfoBlockedReason.EMPTY_HAND
            else -> null
        }
        return RentQuote(tile, o.ownerId, amount, reason == null, forced, reason)
    }

    /** Kartu yang boleh ditunjukkan sebagai bayaran informasi. Sel Mata-Mata: hanya Lokasi bila punya. */
    fun allowedInfoCards(s: GameState, c: GameContent, q: RentQuote, payer: PlayerId): List<CardId> {
        val hand = s.players[payer.value].hand
        if (q.forcedLocation) {
            val locations = hand.filter { c.evidence(it).type == EvidenceType.LOCATION }
            if (locations.isNotEmpty()) return locations
        }
        return hand
    }

    /** (jumlah Rumah Aman terpasang, jumlah Markas terpasang) milik [owner], atau semua bila null. */
    fun buildingCount(s: GameState, owner: PlayerId? = null): Pair<Int, Int> {
        var houses = 0
        var hqs = 0
        s.ownership.values.filter { owner == null || it.ownerId == owner }.forEach {
            if (it.level == Ownership.HQ_LEVEL) hqs++ else houses += it.level
        }
        return houses to hqs
    }

    /** Kekayaan bersih: uang + harga properti (tergadai setengah) + biaya bangun dasar bangunan terpasang. */
    fun netWorth(s: GameState, c: GameContent, p: PlayerId): Int {
        val pl = s.players[p.value]
        if (pl.bankrupt) return 0
        return pl.money + s.ownership.filterValues { it.ownerId == p }.entries.sumOf { (tile, o) ->
            val t = c.tile(tile) as OwnableTile
            val land = if (o.mortgaged) t.price / 2 else t.price
            val build = (t as? PropertyTile)?.let { s.config.balance.buildCost.getValue(it.district) * o.level } ?: 0
            land + build
        }
    }

    // --- Pemeriksaan aksi bisnis ---------------------------------------------------------

    fun buildCheck(s: GameState, c: GameContent, p: PlayerId, tile: Int): RuleViolation? {
        val t = c.board.getOrNull(tile) as? PropertyTile
            ?: return violation(ViolationCode.NOT_PROPERTY, "Petak $tile bukan bangunan distrik")
        val o = s.ownership[tile]
        if (o?.ownerId != p) return violation(ViolationCode.NOT_OWNER, "${t.name} bukan milikmu")
        val district = c.propertiesIn(t.district).map { s.ownership[it.index] }
        if (district.any { it?.ownerId != p }) return violation(ViolationCode.BUILD_NOT_CELL, "Belum memiliki seluruh distrik ${t.district}")
        if (district.any { it!!.mortgaged }) return violation(ViolationCode.BUILD_MORTGAGED, "Ada properti tergadai di distrik ${t.district}")
        if (o.level >= Ownership.MAX_LEVEL) return violation(ViolationCode.BUILD_MAX_LEVEL, "${t.name} sudah Markas")
        val minLevel = district.minOf { it!!.level }
        if (o.level > minLevel) return violation(ViolationCode.BUILD_UNEVEN, "Bangun merata: selisih level dalam distrik maksimal 1")
        if (o.level < 4 && s.houseStock < 1) return violation(ViolationCode.BUILD_NO_STOCK, "Stok Rumah Aman habis")
        if (o.level == 4 && s.hqStock < 1) return violation(ViolationCode.BUILD_NO_STOCK, "Stok Markas habis")
        val cost = buildCost(s, c, t.district)
        if (s.players[p.value].money < cost) return violation(ViolationCode.INSUFFICIENT_FUNDS, "Biaya bangun $cost")
        return null
    }

    fun sellCheck(s: GameState, c: GameContent, p: PlayerId, tile: Int): RuleViolation? {
        val t = c.board.getOrNull(tile) as? PropertyTile
            ?: return violation(ViolationCode.NOT_PROPERTY, "Petak $tile bukan bangunan distrik")
        val o = s.ownership[tile]
        if (o?.ownerId != p) return violation(ViolationCode.NOT_OWNER, "${t.name} bukan milikmu")
        if (o.level == 0) return violation(ViolationCode.SELL_NOTHING, "Tidak ada bangunan di ${t.name}")
        val maxLevel = c.propertiesIn(t.district).maxOf { s.ownership[it.index]?.level ?: 0 }
        if (o.level < maxLevel) return violation(ViolationCode.SELL_UNEVEN, "Jual merata: jual dari petak dengan level tertinggi")
        if (o.level == Ownership.HQ_LEVEL && s.houseStock < 4) {
            return violation(ViolationCode.SELL_NO_HOUSE_STOCK, "Markas hanya bisa dipecah bila ada 4 Rumah Aman di stok")
        }
        return null
    }

    fun mortgageCheck(s: GameState, c: GameContent, p: PlayerId, tile: Int): RuleViolation? {
        val t = c.board.getOrNull(tile) as? OwnableTile
            ?: return violation(ViolationCode.NOT_PROPERTY, "Petak $tile tidak bisa dimiliki")
        val o = s.ownership[tile]
        if (o?.ownerId != p) return violation(ViolationCode.NOT_OWNER, "${t.name} bukan milikmu")
        if (o.mortgaged) return violation(ViolationCode.ALREADY_MORTGAGED, "${t.name} sudah digadai")
        if (t is PropertyTile && c.propertiesIn(t.district).any { (s.ownership[it.index]?.level ?: 0) > 0 }) {
            return violation(ViolationCode.DISTRICT_HAS_BUILDINGS, "Jual semua bangunan di distrik ${t.district} lebih dulu")
        }
        return null
    }

    fun unmortgageCheck(s: GameState, c: GameContent, p: PlayerId, tile: Int): RuleViolation? {
        val t = c.board.getOrNull(tile) as? OwnableTile
            ?: return violation(ViolationCode.NOT_PROPERTY, "Petak $tile tidak bisa dimiliki")
        val o = s.ownership[tile]
        if (o?.ownerId != p) return violation(ViolationCode.NOT_OWNER, "${t.name} bukan milikmu")
        if (!o.mortgaged) return violation(ViolationCode.NOT_MORTGAGED, "${t.name} tidak sedang digadai")
        val cost = unmortgageCost(s, c, tile)
        if (s.players[p.value].money < cost) return violation(ViolationCode.INSUFFICIENT_FUNDS, "Biaya tebus $cost")
        return null
    }

    /** Masih ada bangunan yang bisa dijual atau properti yang bisa digadai? */
    fun canRaiseFunds(s: GameState, c: GameContent, p: PlayerId): Boolean =
        s.ownership.filterValues { it.ownerId == p }.keys.any { tile ->
            sellCheck(s, c, p, tile) == null || mortgageCheck(s, c, p, tile) == null
        }

    /** Properti boleh diperdagangkan bila tanpa bangunan di seluruh distriknya. */
    fun tradeable(s: GameState, c: GameContent, tile: Int): Boolean {
        val t = c.board.getOrNull(tile) as? OwnableTile ?: return false
        if (s.ownership[tile] == null) return false
        return t !is PropertyTile || c.propertiesIn(t.district).all { (s.ownership[it.index]?.level ?: 0) == 0 }
    }
}
