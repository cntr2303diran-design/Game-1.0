package id.diran.lisbon1944.session

import id.diran.lisbon1944.engine.GameEvents as E
import id.diran.lisbon1944.engine.model.Balance
import id.diran.lisbon1944.engine.model.EscapeRouteTile
import id.diran.lisbon1944.engine.model.GameEvent
import id.diran.lisbon1944.engine.model.PropertyTile

/** Statistik permainan untuk layar akhir — hanya dari event publik. */
data class GameStats(
    val biggestRent: Int,
    val biggestRentTile: Int?,
    val biggestRentPayer: Int?,
    val infoPayments: Int,
    val cardsShown: Int,
    val suggestions: Int,
    val auctions: Int,
    val trades: Int,
    val arrests: Int,
    val frontsRevealed: Int,
    val doubleAgent: Int?,
    /** Kebohongan Agen Ganda: (penanya, kartu yang ditunjukkan). */
    val doubleAgentLies: List<Pair<Int, String>>,
) {
    companion object {
        fun from(log: List<GameEvent>): GameStats {
            var biggest = 0
            var tile: Int? = null
            var payer: Int? = null
            var lastLanded: Int? = null
            for (e in log) {
                if (e.type == E.LANDED) lastLanded = e.payload["tile"]?.toIntOrNull()
                if (e.type == E.MONEY && e.payload["reason"] == "rent") {
                    val a = e.payload["amount"]?.toIntOrNull() ?: 0
                    if (a > biggest) {
                        biggest = a
                        tile = lastLanded
                        payer = e.payload["from"]?.toIntOrNull()
                    }
                }
            }
            fun count(t: String) = log.count { it.type == t }
            return GameStats(
                biggestRent = biggest,
                biggestRentTile = tile,
                biggestRentPayer = payer,
                infoPayments = count(E.RENT_PAID_INFO),
                cardsShown = count(E.SUGGESTION_SHOWN) + count(E.RENT_PAID_INFO) + count(E.BLACK_MARKET_DEAL),
                suggestions = count(E.SUGGESTION_MADE),
                auctions = count(E.AUCTION_STARTED),
                trades = count(E.TRADE_ACCEPTED),
                arrests = count(E.SUSPICION_ARREST),
                frontsRevealed = count(E.FRONT_REVEALED),
                doubleAgent = log.lastOrNull { it.type == E.DOUBLE_AGENT_REVEALED }?.payload?.get("player")?.toIntOrNull(),
                doubleAgentLies = log.filter { it.type == E.DOUBLE_AGENT_LIE_REVEALED }.mapNotNull {
                    val asker = it.payload["asker"]?.toIntOrNull() ?: return@mapNotNull null
                    asker to it.payload["card"].orEmpty()
                },
            )
        }
    }
}

/** Pratinjau sewa per level (tanpa efek Front/kartu): sama dengan rumus Economy.rentAmount. */
object RentTable {
    /** [dasar, distrik lengkap, 1..4 Rumah Aman, Markas]. */
    fun property(t: PropertyTile, b: Balance): List<Int> = buildList {
        add(t.baseRent)
        add(t.baseRent * b.monopolyBaseMultiplier)
        for (l in 1..4) add(t.baseRent * b.levelMultipliers[l - 1] * b.buildingRentPercent / 100)
        add(t.hqRent * b.buildingRentPercent / 100)
    }

    /** Sewa Jalur Pelarian menurut jumlah jalur yang dimiliki (1..4). */
    fun escape(@Suppress("UNUSED_PARAMETER") t: EscapeRouteTile, b: Balance): List<Int> = b.escapeRouteRent
}
