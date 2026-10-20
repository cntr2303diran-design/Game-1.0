package id.diran.lisbon1944.engine.internal

import id.diran.lisbon1944.engine.GameEvents as E
import id.diran.lisbon1944.engine.model.Faction
import id.diran.lisbon1944.engine.model.PlayerId

/** Batas Intel pemain (Pembelot Abwehr mendapat bonus). */
internal fun Tx.intelCap(p: PlayerId): Int =
    b.intel.max + if (player(p).faction == Faction.ABWEHR_DEFECTOR) b.intel.defectorBonus else 0

internal fun Tx.changeIntel(p: PlayerId, delta: Int, reason: String) {
    if (delta == 0 || player(p).bankrupt) return
    val before = player(p).intel
    val after = (before + delta).coerceIn(0, intelCap(p))
    if (after == before) return
    upd(p) { it.copy(intel = after) }
    emit(E.INTEL, "player" to p, "delta" to after - before, "value" to after, "reason" to reason)
}

/** Tambah Kecurigaan; mencapai batas → ditangkap (masuk Penjara) dan meteran kembali 0. */
internal fun Tx.changeSuspicion(p: PlayerId, delta: Int, reason: String) {
    if (delta == 0 || player(p).bankrupt) return
    val before = player(p).suspicion
    val after = (before + delta).coerceAtLeast(0)
    if (after >= b.suspicion.limit) {
        upd(p) { it.copy(suspicion = after) }
        emit(E.SUSPICION, "player" to p, "delta" to after - before, "value" to after, "reason" to reason)
        emit(E.SUSPICION_ARREST, "player" to p)
        sendToJail(p)
        return
    }
    if (after == before) return
    upd(p) { it.copy(suspicion = after) }
    emit(E.SUSPICION, "player" to p, "delta" to after - before, "value" to after, "reason" to reason)
}

internal fun Tx.blackMarketPrice(p: PlayerId): Int =
    if (player(p).faction == Faction.SPANISH_SMUGGLER) b.smugglerBlackMarketPrice else b.blackMarketPrice
