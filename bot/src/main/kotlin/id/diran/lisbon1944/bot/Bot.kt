package id.diran.lisbon1944.bot

import id.diran.lisbon1944.engine.model.Action
import id.diran.lisbon1944.engine.model.BotLevel
import id.diran.lisbon1944.engine.model.PlayerView
import id.diran.lisbon1944.engine.rng.GameRng

/**
 * Pemain komputer. Bot HANYA menerima [PlayerView] — tidak pernah GameState — sehingga
 * tidak mungkin "mengintip" amplop atau tangan lawan. Implementasi Easy/Normal/Hard dibuat di Tahap 3.
 */
interface Bot {
    val level: BotLevel

    /** Wajib mengembalikan salah satu elemen [legal]. */
    fun decide(view: PlayerView, legal: List<Action>, rng: GameRng): Action
}
