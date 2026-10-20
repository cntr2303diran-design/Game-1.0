package id.diran.lisbon1944.sim

import id.diran.lisbon1944.engine.content.ContentFormatException
import id.diran.lisbon1944.engine.content.ContentLoader
import id.diran.lisbon1944.engine.content.ContentValidationException
import id.diran.lisbon1944.engine.content.ContentValidator
import id.diran.lisbon1944.engine.content.GameContent
import id.diran.lisbon1944.engine.model.EvidenceType
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8))
    val parsed = try {
        SimArgs.parse(args)
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        exitProcess(2)
    }
    exitProcess(if (parsed.validateOnly) runValidate(parsed) else runSimulation(parsed))
}

/** Muat konten (dengan balance alternatif bila diberikan) dan validasi. */
fun loadContent(args: SimArgs): GameContent {
    val base = ContentLoader.loadDefault()
    return args.balancePath?.let { path ->
        val balance = ContentLoader.parseBalanceOnly(File(path).readText())
        val errors = ContentValidator.validateBalance(balance)
        if (errors.isNotEmpty()) throw ContentValidationException(errors)
        base.copy(balance = balance)
    } ?: base
}

/** Mengembalikan kode keluar: 0 sukses, 1 konten tidak valid. */
fun runValidate(args: SimArgs, out: Appendable = System.out): Int {
    return try {
        out.append(summary(loadContent(args)))
        0
    } catch (e: ContentFormatException) {
        out.append("GAGAL (format): ${e.message}\n")
        1
    } catch (e: ContentValidationException) {
        out.append("GAGAL (validasi): ${e.message}\n")
        1
    }
}

/** Simulasi + laporan. Kode keluar 0 bila selesai (target tidak tercapai tetap 0 — dilaporkan, bukan galat). */
fun runSimulation(args: SimArgs, out: Appendable = System.out, err: Appendable = System.err): Int {
    val content = try {
        loadContent(args)
    } catch (e: RuntimeException) {
        out.append("GAGAL memuat konten: ${e.message}\n")
        return 1
    }
    val sim = Simulation(content)
    val t0 = System.currentTimeMillis()
    val step = (args.games / 10).coerceAtLeast(1)
    val records = sim.run(args) { done -> if (done % step == 0) err.append("  … $done/${args.games} permainan\n") }
    val abRecords = if (args.abTest) {
        err.append("A/B: infoPaymentQuota = 1\n")
        sim.run(args, quotaOverride = 1) { done -> if (done % step == 0) err.append("  … $done/${args.games} permainan (kuota 1)\n") }
    } else {
        null
    }
    val metrics = Metrics.of(records)
    val ab = abRecords?.let(Metrics::of)
    out.append(Report.console(args, metrics, ab))
    out.append("\nWaktu simulasi: ${fmt1((System.currentTimeMillis() - t0) / 1000.0)} detik\n")
    args.out?.let {
        Report.write(it, records, abRecords, metrics, ab)
        out.append("CSV: $it (+ ringkasan ${File(it).nameWithoutExtension}-summary.csv)\n")
    }
    return 0
}

fun summary(c: GameContent): String = buildString {
    appendLine("LISBON 1944 — konten valid")
    appendLine("  Papan        : ${c.board.size} petak (${c.properties.size} bangunan, ${c.escapeRoutes.size} jalur)")
    appendLine("  Faksi        : ${c.factions.joinToString { it.id.name }}")
    appendLine(
        "  Kartu Bukti  : ${c.evidence.size} (" +
            EvidenceType.entries.joinToString { "${it.name} ${c.evidenceOfType(it).size}" } + ")",
    )
    appendLine("  Front        : ${c.fronts.size} (${c.fronts.first().month} → ${c.fronts.last().month})")
    appendLine("  Sandi        : ${c.cipher.size} kartu")
    appendLine("  Telegram     : ${c.telegram.size} kartu")
    appendLine("  Misi Rahasia : ${c.missions.size}")
    appendLine("  Jam Perang   : ${c.balance.warClock.mode}, ±${c.balance.warClock.passesPerStep} lintasan START per langkah; Tuduhan sejak langkah ${c.balance.accuseUnlockStep}")
    appendLine("  Uang awal    : ${c.balance.startingMoney}, gaji START ${c.balance.startSalary}")
    appendLine("  Kuota info   : ${c.balance.infoPaymentQuota}× per langkah Jam Perang, maks sewa ${c.balance.infoPaymentMaxRent}")
    appendLine("  Kombinasi    : ${EvidenceType.entries.fold(1) { acc, t -> acc * c.evidenceOfType(t).size }} jawaban amplop")
}
