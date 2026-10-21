package id.diran.lisbon1944.ui

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ArchivePlayer(
    val name: String,
    val faction: String,
    val bot: Boolean,
    val level: String? = null,
    val score: Int = 0,
    val winner: Boolean = false,
)

@Serializable
data class ArchiveEntry(
    val id: String,
    val endedAt: Long,
    val seed: Long,
    val mode: String,
    val winType: String,
    val rounds: Int,
    val warStep: Int,
    val players: List<ArchivePlayer>,
)

data class FactionRecord(val faction: String, val games: Int, val wins: Int) {
    val rate: Double get() = if (games == 0) 0.0 else wins.toDouble() / games
}

data class ArchiveStats(
    val games: Int,
    val humanGames: Int,
    val humanWins: Int,
    val winTypes: Map<String, Int>,
    val factions: List<FactionRecord>,
    val averageRounds: Double,
) {
    companion object {
        fun of(entries: List<ArchiveEntry>): ArchiveStats {
            val seats = entries.flatMap { it.players }
            val human = entries.filter { e -> e.players.any { !it.bot } }
            return ArchiveStats(
                games = entries.size,
                humanGames = human.size,
                humanWins = human.count { e -> e.players.any { !it.bot && it.winner } },
                winTypes = entries.groupingBy { it.winType }.eachCount(),
                factions = seats.groupBy { it.faction }.map { (f, list) -> FactionRecord(f, list.size, list.count { it.winner }) }
                    .sortedByDescending { it.rate },
                averageRounds = if (entries.isEmpty()) 0.0 else entries.map { it.rounds }.average(),
            )
        }
    }
}

/** Riwayat permainan selesai (JSON Lines di penyimpanan internal), maksimal [MAX] entri terbaru. */
class ArchiveStore(context: Context) {
    private val file = File(context.filesDir, "archive.jsonl")
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<ArchiveEntry> = runCatching {
        if (!file.exists()) emptyList()
        else file.readLines().mapNotNull { l -> runCatching { json.decodeFromString<ArchiveEntry>(l) }.getOrNull() }
    }.getOrDefault(emptyList())

    fun add(e: ArchiveEntry) {
        val list = all()
        if (list.any { it.id == e.id }) return
        val keep = (list + e).takeLast(MAX)
        runCatching { file.writeText(keep.joinToString("\n") { json.encodeToString(it) } + "\n") }
    }

    fun clear() {
        file.delete()
    }

    companion object {
        const val MAX = 200
    }
}

/** Tanda manual Catatan Detektif (✓ ✗ ?) dan catatan teks, per permainan & per pemain. */
class NotesStore(context: Context) {
    private val prefs = context.getSharedPreferences("lisbon1944_notes", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(gameId: String, seat: Int) = "$gameId#$seat"

    fun marks(gameId: String, seat: Int): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(prefs.getString(key(gameId, seat), null) ?: "{}") }.getOrDefault(emptyMap())

    fun setMark(gameId: String, seat: Int, k: String, value: String?) {
        val m = marks(gameId, seat).toMutableMap()
        if (value == null) m.remove(k) else m[k] = value
        prefs.edit().putString(key(gameId, seat), json.encodeToString(m)).apply()
    }
}
