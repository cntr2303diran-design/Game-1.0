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
    exitProcess(runValidate(parsed))
}

/** Mengembalikan kode keluar: 0 sukses, 1 konten tidak valid. */
fun runValidate(args: SimArgs, out: Appendable = System.out): Int {
    return try {
        val base = ContentLoader.loadDefault()
        val content = args.balancePath?.let { path ->
            val balance = ContentLoader.parseBalanceOnly(File(path).readText())
            val errors = ContentValidator.validateBalance(balance)
            if (errors.isNotEmpty()) throw ContentValidationException(errors)
            base.copy(balance = balance)
        } ?: base
        out.append(summary(content))
        0
    } catch (e: ContentFormatException) {
        out.append("GAGAL (format): ${e.message}\n")
        1
    } catch (e: ContentValidationException) {
        out.append("GAGAL (validasi): ${e.message}\n")
        1
    }
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
    appendLine("  Uang awal    : ${c.balance.startingMoney}, gaji START ${c.balance.startSalary}")
    appendLine("  Kuota info   : ${c.balance.infoPaymentQuota}× per langkah Jam Perang, maks sewa ${c.balance.infoPaymentMaxRent}")
    appendLine("  Kombinasi    : ${EvidenceType.entries.fold(1) { acc, t -> acc * c.evidenceOfType(t).size }} jawaban amplop")
}
