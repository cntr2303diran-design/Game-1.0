package id.diran.lisbon1944.ui

import android.content.Context
import id.diran.lisbon1944.session.SaveMeta
import java.io.File

enum class Speed(val delayMs: Long) { SLOW(900), NORMAL(380), FAST(120), INSTANT(0) }

data class Settings(val speed: Speed = Speed.NORMAL, val showSecretsInLog: Boolean = true)

/** Ringkasan slot: meta + kapan disimpan. */
data class SlotInfo(val slot: Int, val meta: SaveMeta, val savedAt: Long)

/** Tiga slot simpan otomatis di penyimpanan internal aplikasi + pengaturan di SharedPreferences. */
class SaveStore(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "saves").apply { mkdirs() }
    private val prefs get() = context.getSharedPreferences("lisbon1944", Context.MODE_PRIVATE)

    private fun data(slot: Int) = File(dir, "slot_$slot.json")
    private fun meta(slot: Int) = File(dir, "slot_$slot.meta")

    fun write(slot: Int, text: String, m: SaveMeta) {
        val tmp = File(dir, "slot_$slot.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(data(slot))) {
            data(slot).writeText(text)
            tmp.delete()
        }
        meta(slot).writeText(
            listOf(
                m.players.joinToString("\u001F"),
                m.warStep.toString(),
                m.round.toString(),
                m.actions.toString(),
                m.finished.toString(),
                System.currentTimeMillis().toString(),
            ).joinToString("\n"),
        )
    }

    fun read(slot: Int): String? = data(slot).takeIf { it.exists() }?.readText()

    fun delete(slot: Int) {
        data(slot).delete()
        meta(slot).delete()
    }

    fun info(slot: Int): SlotInfo? {
        val f = meta(slot)
        if (!f.exists() || !data(slot).exists()) return null
        return runCatching {
            val l = f.readLines()
            SlotInfo(
                slot,
                SaveMeta(
                    players = l[0].split("\u001F"),
                    warStep = l[1].toInt(),
                    round = l[2].toInt(),
                    actions = l[3].toInt(),
                    finished = l[4].toBoolean(),
                ),
                l[5].toLong(),
            )
        }.getOrNull()
    }

    fun slots(): List<SlotInfo?> = (1..SLOTS).map(::info)

    /** Slot kosong pertama, atau slot terlama bila semua terisi. */
    fun freeSlot(): Int {
        val all = slots()
        all.forEachIndexed { i, s -> if (s == null) return i + 1 }
        return all.filterNotNull().minByOrNull { it.savedAt }?.slot ?: 1
    }

    fun loadSettings(): Settings = Settings(
        speed = runCatching { Speed.valueOf(prefs.getString("speed", Speed.NORMAL.name)!!) }.getOrDefault(Speed.NORMAL),
        showSecretsInLog = prefs.getBoolean("secrets", true),
    )

    fun saveSettings(s: Settings) {
        prefs.edit().putString("speed", s.speed.name).putBoolean("secrets", s.showSecretsInLog).apply()
    }

    companion object {
        const val SLOTS = 3
    }
}
