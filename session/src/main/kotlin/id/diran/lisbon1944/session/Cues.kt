package id.diran.lisbon1944.session

import id.diran.lisbon1944.engine.GameEvents as E
import id.diran.lisbon1944.engine.model.GameEvent
import id.diran.lisbon1944.engine.model.PlayerId
import id.diran.lisbon1944.engine.model.Visibility

/** Efek suara yang bisa dipicu UI. */
enum class Sfx { DICE, STEP, COIN, JACKPOT, TELEGRAPH, CIPHER, SIREN, STAMP, CELL_DOOR, CARD, ALARM }

/** Jenis cap besar di layar. */
enum class StampKind { SOLVED, BURNED, BANKRUPT, ARRESTED, JAIL, DOMINANCE, DOUBLE_AGENT }

enum class Haptic { LIGHT, STRONG }

/**
 * Isyarat animasi/suara yang diturunkan dari GameEvent (BAGIAN 12.1: "event animasi diantrikan dari daftar
 * GameEvent agar UI selalu sinkron dengan engine"). Hanya event yang boleh dilihat [viewer] yang dipakai.
 */
sealed interface Cue {
    val sfx: Sfx?
    val haptic: Haptic? get() = null

    data class Dice(val player: Int, val d1: Int, val d2: Int, val mine: Boolean) : Cue {
        override val sfx get() = Sfx.DICE
        override val haptic get() = if (mine) Haptic.LIGHT else null
    }

    data class Money(val from: Int?, val to: Int?, val amount: Int, val reason: String, val toMe: Boolean) : Cue {
        override val sfx get() = Sfx.COIN
        override val haptic get() = if (toMe && amount >= BIG_RENT && reason == "rent") Haptic.STRONG else null
    }

    data class Front(val cardId: Int, val month: String) : Cue {
        override val sfx get() = Sfx.SIREN
    }

    data class CardDrawn(val player: Int, val deck: String, val cardId: Int) : Cue {
        override val sfx get() = if (deck == "TELEGRAM") Sfx.TELEGRAPH else Sfx.CIPHER
    }

    data class Stamp(val kind: StampKind, val player: Int?) : Cue {
        override val sfx get() = when (kind) {
            StampKind.JAIL -> Sfx.CELL_DOOR
            StampKind.ARRESTED -> Sfx.ALARM
            else -> Sfx.STAMP
        }
        override val haptic get() = if (kind == StampKind.SOLVED || kind == StampKind.BURNED) Haptic.STRONG else null
        /** Razia/penangkapan: papan bergetar. */
        val shake: Boolean get() = kind == StampKind.ARRESTED || kind == StampKind.JAIL
    }

    data class Jackpot(val player: Int, val amount: Int) : Cue {
        override val sfx get() = Sfx.JACKPOT
    }

    data class Decode(val player: Int, val success: Boolean?) : Cue {
        override val sfx get() = Sfx.CIPHER
    }

    data class Tactic(val player: Int, val cardId: Int) : Cue {
        override val sfx get() = Sfx.CARD
    }

    /** Tuduhan dibuka — amplop disobek (haptik). */
    data class Accusation(val player: Int) : Cue {
        override val sfx get() = Sfx.CARD
        override val haptic get() = Haptic.STRONG
    }

    companion object {
        const val BIG_RENT = 200

        fun visible(e: GameEvent, viewer: PlayerId): Boolean = when (val v = e.visibility) {
            Visibility.Public -> true
            is Visibility.Private -> viewer in v.playerIds
        }

        fun from(events: List<GameEvent>, viewer: PlayerId): List<Cue> = events.filter { visible(it, viewer) }.mapNotNull { e ->
            val p = e.payload
            fun int(k: String) = p[k]?.toIntOrNull()
            when (e.type) {
                E.DICE_ROLLED -> if (p["purpose"] == "casino") null else Dice(int("player") ?: -1, int("d1") ?: 1, int("d2") ?: 1, int("player") == viewer.value)
                E.MONEY -> {
                    val amount = int("amount") ?: 0
                    val reason = p["reason"].orEmpty()
                    if (amount <= 0 || reason == "decode") null
                    else Money(int("from"), int("to"), amount, reason, int("to") == viewer.value)
                }
                E.RENT_PAID_INFO -> Money(int("player"), int("owner"), 0, "info", int("owner") == viewer.value)
                E.FRONT_REVEALED -> int("card")?.let { Front(it, p["month"].orEmpty()) }
                E.CARD_DRAWN -> int("card")?.let { CardDrawn(int("player") ?: -1, p["deck"].orEmpty(), it) }
                E.ACCUSATION_CORRECT -> Stamp(StampKind.SOLVED, int("player"))
                E.BURNED -> Stamp(StampKind.BURNED, int("player"))
                E.BANKRUPT -> Stamp(StampKind.BANKRUPT, int("player"))
                E.SUSPICION_ARREST -> Stamp(StampKind.ARRESTED, int("player"))
                E.JAIL_ENTERED -> Stamp(StampKind.JAIL, int("player"))
                E.ECONOMIC_DOMINANCE -> Stamp(StampKind.DOMINANCE, int("player"))
                E.DOUBLE_AGENT_REVEALED -> Stamp(StampKind.DOUBLE_AGENT, int("player"))
                E.CASINO_RESULT -> if (p["result"] == "seven" || p["result"] == "doubles") Jackpot(int("player") ?: -1, int("won") ?: 0) else null
                E.DECODE_STARTED -> Decode(int("player") ?: -1, null)
                E.DECODE_SUCCESS -> Decode(int("player") ?: -1, true)
                E.DECODE_FAILED -> Decode(int("player") ?: -1, false)
                E.TACTIC_PLAYED -> int("card")?.let { Tactic(int("player") ?: -1, it) }
                E.ACCUSATION_DECLINED -> null
                E.ACCUSATION_WRONG_FAMILY -> Stamp(StampKind.BURNED, int("player"))
                else -> null
            }
        }.let { cues ->
            // Tuduhan: haptik saat amplop dibuka, sebelum capnya.
            val out = ArrayList<Cue>(cues.size + 1)
            for (c in cues) {
                if (c is Stamp && (c.kind == StampKind.SOLVED || c.kind == StampKind.BURNED)) out += Accusation(c.player ?: -1)
                out += c
            }
            out
        }
    }
}
