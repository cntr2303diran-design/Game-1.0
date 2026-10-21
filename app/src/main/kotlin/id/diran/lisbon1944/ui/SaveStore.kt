package id.diran.lisbon1944.ui

import android.content.Context
import id.diran.lisbon1944.session.SaveMeta
import java.io.File

enum class Speed(val delayMs: Long) { SLOW(900), NORMAL(380), FAST(120), INSTANT(0) }

enum class AppLanguage(val tag: String?) { SYSTEM(null), ID("id"), EN("en") }

data class Settings(
    val speed: Speed = Speed.NORMAL,
    val showSecretsInLog: Boolean = true,
    val music: Boolean = true,
    val sfx: Boolean = true,
    val musicVolume: Float = 0.45f,
    val sfxVolume: Float = 0.8f,
    val vibration: Boolean = true,
    /** 1.0–2.0 (100–200%). */
    val textScale: Float = 1f,
    val colorBlind: Boolean = false,
    val showPossibilities: Boolean = true,
    val reduceMotion: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val tutorialSeen: Boolean = false,
)

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

    fun slots(): List<SlotInfo?> = (1..SaveSlots.SLOTS).map(::info)

    /** Slot kosong pertama, atau slot terlama bila semua terisi. */
    fun freeSlot(): Int {
        val all = slots()
        all.forEachIndexed { i, s -> if (s == null) return i + 1 }
        return all.filterNotNull().minByOrNull { it.savedAt }?.slot ?: 1
    }

    fun loadSettings(): Settings {
        val p = prefs
        val d = Settings()
        return Settings(
            speed = runCatching { Speed.valueOf(p.getString("speed", d.speed.name)!!) }.getOrDefault(d.speed),
            showSecretsInLog = p.getBoolean("secrets", d.showSecretsInLog),
            music = p.getBoolean("music", d.music),
            sfx = p.getBoolean("sfx", d.sfx),
            musicVolume = p.getFloat("musicVolume", d.musicVolume),
            sfxVolume = p.getFloat("sfxVolume", d.sfxVolume),
            vibration = p.getBoolean("vibration", d.vibration),
            textScale = p.getFloat("textScale", d.textScale).coerceIn(1f, 2f),
            colorBlind = p.getBoolean("colorBlind", d.colorBlind),
            showPossibilities = p.getBoolean("possibilities", d.showPossibilities),
            reduceMotion = p.getBoolean("reduceMotion", d.reduceMotion),
            language = runCatching { AppLanguage.valueOf(p.getString("language", d.language.name)!!) }.getOrDefault(d.language),
            tutorialSeen = p.getBoolean("tutorialSeen", d.tutorialSeen),
        )
    }

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putString("speed", s.speed.name)
            .putBoolean("secrets", s.showSecretsInLog)
            .putBoolean("music", s.music)
            .putBoolean("sfx", s.sfx)
            .putFloat("musicVolume", s.musicVolume)
            .putFloat("sfxVolume", s.sfxVolume)
            .putBoolean("vibration", s.vibration)
            .putFloat("textScale", s.textScale)
            .putBoolean("colorBlind", s.colorBlind)
            .putBoolean("possibilities", s.showPossibilities)
            .putBoolean("reduceMotion", s.reduceMotion)
            .putString("language", s.language.name)
            .putBoolean("tutorialSeen", s.tutorialSeen)
            .apply()
    }

    /** Bahasa yang dipilih — dibaca MainActivity sebelum Activity dibuat (static agar bisa dipakai di attachBaseContext). */
    companion object {
        fun language(context: Context): AppLanguage =
            runCatching {
                AppLanguage.valueOf(context.getSharedPreferences("lisbon1944", Context.MODE_PRIVATE).getString("language", "SYSTEM")!!)
            }.getOrDefault(AppLanguage.SYSTEM)
    }
}

object SaveSlots {
    const val SLOTS = 3
}
