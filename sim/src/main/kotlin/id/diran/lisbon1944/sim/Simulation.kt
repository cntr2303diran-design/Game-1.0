package id.diran.lisbon1944.sim

import id.diran.lisbon1944.bot.BotMatch
import id.diran.lisbon1944.engine.GameEngine
import id.diran.lisbon1944.engine.GameEvents
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.GameConfig
import id.diran.lisbon1944.engine.model.GameState
import id.diran.lisbon1944.engine.model.PlayerSetup
import id.diran.lisbon1944.engine.model.WinType
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.sqrt

/** Statistik satu kursi dalam satu permainan. */
data class SeatRecord(
    val seat: Int,
    val faction: Faction,
    val won: Double,
    val infoPays: Int,
    val moneyPays: Int,
    val bankrupt: Boolean,
    val burned: Boolean,
)

/** Ringkasan satu permainan — semua diturunkan dari GameState akhir & log event. */
data class GameRecord(
    val index: Int,
    val seed: Long,
    val aborted: Boolean,
    val actions: Int,
    val turns: Int,
    val rounds: Int,
    val minutes: Double,
    val winType: WinType?,
    val winners: List<Int>,
    val warStep: Int,
    val correctAccusations: Int,
    val wrongAccusations: Int,
    val arrests: Int,
    val missionsCompleted: Int,
    val seats: List<SeatRecord>,
) {
    companion object {
        fun from(index: Int, seed: Long, state: GameState, actions: Int, aborted: Boolean, secondsPerTurn: Int): GameRecord {
            val log = state.eventLog
            fun count(type: String) = log.count { it.type == type }
            fun countBy(type: String, key: String): Map<Int, Int> =
                log.filter { it.type == type }.mapNotNull { it.payload[key]?.toIntOrNull() }.groupingBy { it }.eachCount()
            val infoBy = countBy(GameEvents.RENT_PAID_INFO, "player")
            val moneyBy = log.filter { it.type == GameEvents.MONEY && it.payload["reason"] == "rent" }
                .mapNotNull { it.payload["from"]?.toIntOrNull() }.groupingBy { it }.eachCount()
            val winners = if (aborted) emptyList() else state.winner?.winners?.map { it.value }.orEmpty()
            val share = if (winners.isEmpty()) 0.0 else 1.0 / winners.size
            val turns = count(GameEvents.TURN_STARTED)
            return GameRecord(
                index = index,
                seed = seed,
                aborted = aborted,
                actions = actions,
                turns = turns,
                rounds = state.roundNumber,
                minutes = turns * secondsPerTurn / 60.0,
                winType = if (aborted) null else state.winner?.type,
                winners = winners,
                warStep = state.warStep,
                correctAccusations = count(GameEvents.ACCUSATION_CORRECT),
                wrongAccusations = log.count { it.type == GameEvents.BURNED || it.type == GameEvents.ACCUSATION_WRONG_FAMILY },
                arrests = count(GameEvents.SUSPICION_ARREST),
                missionsCompleted = count(GameEvents.MISSION_COMPLETED),
                seats = state.players.map { p ->
                    SeatRecord(
                        seat = p.id.value,
                        faction = p.faction,
                        won = if (p.id.value in winners) share else 0.0,
                        infoPays = infoBy[p.id.value] ?: 0,
                        moneyPays = moneyBy[p.id.value] ?: 0,
                        bankrupt = p.bankrupt,
                        burned = p.burned,
                    )
                },
            )
        }
    }
}

/** Menjalankan banyak permainan antar-bot secara paralel; hasil berurutan sesuai indeks (deterministik). */
class Simulation(private val content: GameContent) {

    private val engine = GameEngine(content)
    private val match = BotMatch(engine)

    /** Faksi per permainan: dirotasi agar setiap faksi tampil merata di semua kursi. */
    fun factionsFor(game: Int, players: Int): List<Faction> {
        val all = Faction.entries
        return List(players) { all[(game + it * 3) % all.size] }.let { list ->
            if (list.toSet().size == players) list else all.shuffled(java.util.Random(game.toLong())).take(players)
        }
    }

    fun config(args: SimArgs, game: Int, quotaOverride: Int? = null): GameConfig = GameConfig(
        players = args.seatLevels.mapIndexed { i, lvl -> PlayerSetup("Bot ${i + 1}", factionsFor(game, args.players)[i], isBot = true, botLevel = lvl) },
        balance = content.balance,
        mode = args.mode,
        infoPaymentQuotaOverride = quotaOverride,
    )

    fun run(args: SimArgs, quotaOverride: Int? = null, progress: ((Int) -> Unit)? = null): List<GameRecord> {
        val pool = Executors.newFixedThreadPool(args.threads)
        try {
            val jobs = (0 until args.games).map { g ->
                Callable {
                    val seed = args.seed + g
                    val r = match.play(config(args, g, quotaOverride), seed, args.maxActions)
                    check(r.illegal.isEmpty()) { "Bot memilih aksi ilegal pada seed $seed: ${r.illegal.first()}" }
                    GameRecord.from(g, seed, r.state, r.actions, r.aborted, args.secondsPerTurn)
                }
            }
            val futures = jobs.map { pool.submit(it) }
            return futures.mapIndexed { i, f -> f.get().also { progress?.invoke(i + 1) } }
        } finally {
            pool.shutdown()
        }
    }
}

// =====================================================================================
// Metrik
// =====================================================================================

data class Metrics(
    val games: Int,
    val anomalies: List<Long>,
    val turnsMean: Double,
    val turnsSd: Double,
    val turnsP10: Int,
    val turnsMedian: Int,
    val turnsP90: Int,
    val minutesMean: Double,
    val minutesMedian: Double,
    val minutesP10: Double,
    val minutesP90: Double,
    val actionsMean: Double,
    val winShare: Map<WinType, Double>,
    val factionWinRate: Map<Faction, Double>,
    val factionGames: Map<Faction, Int>,
    val seatWinRate: Map<Int, Double>,
    val infoPaysPerGame: Double,
    val moneyPaysPerGame: Double,
    val infoShareWinners: Double,
    val infoShareOthers: Double,
    val infoWinCorrelation: Double,
    val wrongAccusationRate: Double,
    val accusationsPerGame: Double,
    val detectiveWarStep: Double,
    val bankruptPerGame: Double,
    val arrestsPerGame: Double,
    val missionsPerGame: Double,
) {
    val averageWinRate: Double get() = if (factionWinRate.isEmpty()) 0.0 else factionWinRate.values.average()
    val maxFactionRatio: Double get() = if (averageWinRate == 0.0) 0.0 else factionWinRate.values.max() / averageWinRate

    companion object {
        private fun percentile(sorted: List<Double>, p: Double): Double =
            if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * p).toInt()]

        fun of(records: List<GameRecord>): Metrics {
            val done = records.filter { !it.aborted }
            val turns = done.map { it.turns.toDouble() }.sorted()
            val minutes = done.map { it.minutes }.sorted()
            val mean = turns.average().takeIf { !it.isNaN() } ?: 0.0
            val sd = if (turns.size > 1) sqrt(turns.sumOf { (it - mean) * (it - mean) } / (turns.size - 1)) else 0.0
            val seats = done.flatMap { it.seats }
            val byFaction = seats.groupBy { it.faction }
            val shares = seats.map { s -> s.infoPays.toDouble() / (s.infoPays + s.moneyPays).coerceAtLeast(1) }
            val wins = seats.map { if (it.won > 0) 1.0 else 0.0 }
            val accusations = done.sumOf { it.correctAccusations + it.wrongAccusations }
            val detective = done.filter { it.winType == WinType.DETECTIVE }
            return Metrics(
                games = records.size,
                anomalies = records.filter { it.aborted }.map { it.seed },
                turnsMean = mean,
                turnsSd = sd,
                turnsP10 = percentile(turns, 0.1).toInt(),
                turnsMedian = percentile(turns, 0.5).toInt(),
                turnsP90 = percentile(turns, 0.9).toInt(),
                minutesMean = minutes.average().takeIf { !it.isNaN() } ?: 0.0,
                minutesMedian = percentile(minutes, 0.5),
                minutesP10 = percentile(minutes, 0.1),
                minutesP90 = percentile(minutes, 0.9),
                actionsMean = done.map { it.actions }.average().takeIf { !it.isNaN() } ?: 0.0,
                winShare = WinType.entries.associateWith { t -> if (done.isEmpty()) 0.0 else done.count { it.winType == t }.toDouble() / done.size },
                factionWinRate = byFaction.mapValues { (_, list) -> list.sumOf { it.won } / list.size },
                factionGames = byFaction.mapValues { it.value.size },
                seatWinRate = seats.groupBy { it.seat }.mapValues { (_, list) -> list.sumOf { it.won } / list.size },
                infoPaysPerGame = if (done.isEmpty()) 0.0 else seats.sumOf { it.infoPays }.toDouble() / done.size,
                moneyPaysPerGame = if (done.isEmpty()) 0.0 else seats.sumOf { it.moneyPays }.toDouble() / done.size,
                infoShareWinners = shares.filterIndexed { i, _ -> wins[i] > 0 }.average().takeIf { !it.isNaN() } ?: 0.0,
                infoShareOthers = shares.filterIndexed { i, _ -> wins[i] == 0.0 }.average().takeIf { !it.isNaN() } ?: 0.0,
                infoWinCorrelation = correlation(shares, wins),
                wrongAccusationRate = if (accusations == 0) 0.0 else done.sumOf { it.wrongAccusations }.toDouble() / accusations,
                accusationsPerGame = if (done.isEmpty()) 0.0 else accusations.toDouble() / done.size,
                detectiveWarStep = detective.map { it.warStep.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0,
                bankruptPerGame = if (done.isEmpty()) 0.0 else seats.count { it.bankrupt }.toDouble() / done.size,
                arrestsPerGame = done.map { it.arrests.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0,
                missionsPerGame = done.map { it.missionsCompleted.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0,
            )
        }

        /** Korelasi Pearson (point-biserial bila y biner). */
        fun correlation(x: List<Double>, y: List<Double>): Double {
            if (x.size < 2 || x.size != y.size) return 0.0
            val mx = x.average()
            val my = y.average()
            var sxy = 0.0
            var sxx = 0.0
            var syy = 0.0
            for (i in x.indices) {
                val dx = x[i] - mx
                val dy = y[i] - my
                sxy += dx * dy
                sxx += dx * dx
                syy += dy * dy
            }
            return if (sxx == 0.0 || syy == 0.0) 0.0 else sxy / sqrt(sxx * syy)
        }
    }
}

/** Target keseimbangan (BAGIAN 11, diperbarui: durasi ±4 jam). */
data class Targets(
    val minMinutes: Double = 210.0,
    val maxMinutes: Double = 270.0,
    val maxFactionRatio: Double = 1.3,
    val minWinTypeShare: Double = 0.10,
) {
    fun evaluate(m: Metrics): List<Pair<String, Boolean>> = listOf(
        "Durasi median ${fmt0(minMinutes)}–${fmt0(maxMinutes)} menit (hasil ${fmt0(m.minutesMedian)})" to (m.minutesMedian in minMinutes..maxMinutes),
        "Tidak ada faksi dengan win rate > ${fmt1(maxFactionRatio)}× rata-rata (maks ${fmt2(m.maxFactionRatio)}×)" to (m.maxFactionRatio <= maxFactionRatio),
        "Ketiga cara menang ≥ ${pct(minWinTypeShare)} (Detektif ${pct(m.winShare[WinType.DETECTIVE] ?: 0.0)}, " +
            "Ekonomi ${pct(m.winShare[WinType.ECONOMIC] ?: 0.0)}, Akhir Perang ${pct(m.winShare[WinType.WAR_END] ?: 0.0)})" to
            listOf(WinType.DETECTIVE, WinType.ECONOMIC, WinType.WAR_END).all { (m.winShare[it] ?: 0.0) >= minWinTypeShare },
        "Tidak ada anomali (> batas aksi): ${m.anomalies.size}" to m.anomalies.isEmpty(),
    )
}

internal fun fmt0(v: Double) = String.format(java.util.Locale.ROOT, "%.0f", v)
internal fun fmt1(v: Double) = String.format(java.util.Locale.ROOT, "%.1f", v)
internal fun fmt2(v: Double) = String.format(java.util.Locale.ROOT, "%.2f", v)
internal fun pct(v: Double) = String.format(java.util.Locale.ROOT, "%.1f%%", v * 100)
