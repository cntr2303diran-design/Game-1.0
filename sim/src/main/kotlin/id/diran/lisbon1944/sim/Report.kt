package id.diran.lisbon1944.sim

import id.diran.lisbon1944.engine.model.WinType
import java.io.File

/** Format laporan konsol & CSV. */
object Report {

    private val WIN_NAMES = mapOf(
        WinType.DETECTIVE to "Detektif",
        WinType.ECONOMIC to "Ekonomi",
        WinType.WAR_END to "Skor Mei 1945",
        WinType.DOUBLE_AGENT to "Agen Ganda",
    )

    fun console(args: SimArgs, m: Metrics, ab: Metrics?, targets: Targets = Targets()): String = buildString {
        appendLine("LISBON 1944 — simulasi ${m.games} permainan, ${args.players} pemain, bot ${args.seatLevels.joinToString(",") { it.name.lowercase() }}, mode ${args.mode.name.lowercase()}, seed ${args.seed}")
        appendLine()
        appendLine("Durasi")
        appendLine("  Giliran/game     : rata-rata ${fmt1(m.turnsMean)} (sd ${fmt1(m.turnsSd)}), P10 ${m.turnsP10} · median ${m.turnsMedian} · P90 ${m.turnsP90}")
        appendLine("  Perkiraan menit  : rata-rata ${fmt0(m.minutesMean)}, P10 ${fmt0(m.minutesP10)} · median ${fmt0(m.minutesMedian)} · P90 ${fmt0(m.minutesP90)} (${args.secondsPerTurn} detik/giliran)")
        appendLine("  Aksi/game        : ${fmt0(m.actionsMean)}")
        appendLine()
        appendLine("Cara menang")
        listOf(WinType.DETECTIVE, WinType.ECONOMIC, WinType.WAR_END, WinType.DOUBLE_AGENT).forEach { t ->
            val v = m.winShare[t] ?: 0.0
            if (t != WinType.DOUBLE_AGENT || v > 0) appendLine("  ${WIN_NAMES.getValue(t).padEnd(16)} : ${pct(v).padStart(6)}  ${bar(v)}")
        }
        appendLine()
        appendLine("Win rate per faksi (rata-rata ${pct(m.averageWinRate)}, maks ${fmt2(m.maxFactionRatio)}× rata-rata)")
        m.factionWinRate.entries.sortedByDescending { it.value }.forEach { (f, r) ->
            appendLine("  ${f.name.padEnd(18)} : ${pct(r).padStart(6)}  (${m.factionGames[f]} kursi)")
        }
        appendLine()
        appendLine("Win rate per posisi duduk")
        m.seatWinRate.entries.sortedBy { it.key }.forEach { (s, r) -> appendLine("  Kursi ${s + 1}          : ${pct(r).padStart(6)}") }
        appendLine()
        appendLine("Bayar sewa")
        appendLine("  Informasi/game   : ${fmt1(m.infoPaysPerGame)}   Uang/game: ${fmt1(m.moneyPaysPerGame)}")
        appendLine("  Porsi informasi  : pemenang ${pct(m.infoShareWinners)} vs lainnya ${pct(m.infoShareOthers)}; korelasi dengan menang ${fmt2(m.infoWinCorrelation)}")
        appendLine()
        appendLine("Misteri & lainnya")
        appendLine("  Tuduhan/game     : ${fmt2(m.accusationsPerGame)}, salah ${pct(m.wrongAccusationRate)}")
        appendLine("  Jam Perang saat misteri terpecahkan (menang Detektif): ${fmt1(m.detectiveWarStep)}")
        appendLine("  Bangkrut/game ${fmt2(m.bankruptPerGame)} · ditangkap/game ${fmt2(m.arrestsPerGame)} · misi selesai/game ${fmt2(m.missionsPerGame)}")
        if (m.anomalies.isNotEmpty()) {
            appendLine("  ANOMALI (> ${args.maxActions} aksi): ${m.anomalies.size} — seed ${m.anomalies.take(20).joinToString()}")
        }
        if (ab != null) {
            appendLine()
            appendLine("A/B: infoPaymentQuota = 1 (seed sama)")
            appendLine("  ${"Metrik".padEnd(30)} ${"Kuota bawaan".padStart(13)} ${"Kuota 1".padStart(10)} ${"Selisih".padStart(10)}")
            abRows(m, ab).forEach { (name, a, b, fmt) ->
                appendLine("  ${name.padEnd(30)} ${fmt(a).padStart(13)} ${fmt(b).padStart(10)} ${signed(b - a, fmt).padStart(10)}")
            }
        }
        appendLine()
        appendLine("Target keseimbangan")
        targets.evaluate(m).forEach { (text, ok) -> appendLine("  [${if (ok) "✓" else "✗"}] $text") }
    }

    private fun bar(v: Double) = "█".repeat((v * 40).toInt())

    private fun signed(d: Double, fmt: (Double) -> String) = (if (d > 0) "+" else "") + fmt(d)

    data class Row(val name: String, val a: Double, val b: Double, val fmt: (Double) -> String)

    fun abRows(a: Metrics, b: Metrics): List<Row> = listOf(
        Row("Menit median", a.minutesMedian, b.minutesMedian, ::fmt0),
        Row("Giliran rata-rata", a.turnsMean, b.turnsMean, ::fmt1),
        Row("Menang Detektif", a.winShare[WinType.DETECTIVE] ?: 0.0, b.winShare[WinType.DETECTIVE] ?: 0.0, ::pct),
        Row("Menang Ekonomi", a.winShare[WinType.ECONOMIC] ?: 0.0, b.winShare[WinType.ECONOMIC] ?: 0.0, ::pct),
        Row("Menang Skor Mei 1945", a.winShare[WinType.WAR_END] ?: 0.0, b.winShare[WinType.WAR_END] ?: 0.0, ::pct),
        Row("Bayar informasi/game", a.infoPaysPerGame, b.infoPaysPerGame, ::fmt1),
        Row("Bayar uang/game", a.moneyPaysPerGame, b.moneyPaysPerGame, ::fmt1),
        Row("Korelasi info–menang", a.infoWinCorrelation, b.infoWinCorrelation, ::fmt2),
        Row("Tuduhan salah", a.wrongAccusationRate, b.wrongAccusationRate, ::pct),
        Row("Jam saat terpecahkan", a.detectiveWarStep, b.detectiveWarStep, ::fmt1),
        Row("Bangkrut/game", a.bankruptPerGame, b.bankruptPerGame, ::fmt2),
        Row("Rasio faksi maks", a.maxFactionRatio, b.maxFactionRatio, ::fmt2),
    )

    /** CSV per permainan. */
    fun gamesCsv(records: List<GameRecord>, label: String): String = buildString {
        val maxSeats = records.maxOfOrNull { it.seats.size } ?: 0
        val seatCols = (1..maxSeats).joinToString(",") { "kursi${it}_faksi,kursi${it}_menang,kursi${it}_info,kursi${it}_uang,kursi${it}_bangkrut" }
        appendLine("varian,game,seed,anomali,aksi,giliran,putaran,menit,cara_menang,pemenang,jam_akhir,tuduhan_benar,tuduhan_salah,ditangkap,misi,$seatCols")
        records.forEach { r ->
            append("$label,${r.index},${r.seed},${r.aborted},${r.actions},${r.turns},${r.rounds},${fmt1(r.minutes)},")
            append("${r.winType?.name ?: ""},${r.winners.joinToString(";") { (it + 1).toString() }},${r.warStep},")
            append("${r.correctAccusations},${r.wrongAccusations},${r.arrests},${r.missionsCompleted}")
            r.seats.forEach { s -> append(",${s.faction.name},${fmt2(s.won)},${s.infoPays},${s.moneyPays},${s.bankrupt}") }
            appendLine()
        }
    }

    /** CSV ringkasan metrik + A/B. */
    fun summaryCsv(m: Metrics, ab: Metrics?): String = buildString {
        appendLine("metrik,kuota_bawaan,kuota_1,selisih")
        val rows = if (ab != null) abRows(m, ab) else abRows(m, m).map { it.copy(b = Double.NaN) }
        rows.forEach { r ->
            val b = if (r.b.isNaN()) "" else r.b.toString()
            val d = if (r.b.isNaN()) "" else (r.b - r.a).toString()
            appendLine("${r.name},${r.a},$b,$d")
        }
        m.factionWinRate.forEach { (f, v) -> appendLine("winrate_${f.name},$v,${ab?.factionWinRate?.get(f) ?: ""},") }
        m.seatWinRate.forEach { (s, v) -> appendLine("winrate_kursi${s + 1},$v,${ab?.seatWinRate?.get(s) ?: ""},") }
        appendLine("anomali,${m.anomalies.size},${ab?.anomalies?.size ?: ""},")
    }

    fun write(path: String, records: List<GameRecord>, abRecords: List<GameRecord>?, m: Metrics, ab: Metrics?) {
        val file = File(path)
        file.absoluteFile.parentFile?.mkdirs()
        file.writeText(gamesCsv(records, "bawaan") + (abRecords?.let { gamesCsv(it, "kuota1").lineSequence().drop(1).joinToString("\n") } ?: ""))
        val summary = File(file.absoluteFile.parentFile, file.nameWithoutExtension + "-summary.csv")
        summary.writeText(summaryCsv(m, ab))
    }
}
