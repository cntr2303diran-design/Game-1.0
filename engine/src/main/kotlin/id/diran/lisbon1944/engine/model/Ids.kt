package id.diran.lisbon1944.engine.model

import kotlinx.serialization.Serializable

/** Kursi pemain, 0-based, searah jarum jam. Pemain "di kiri" = kursi berikutnya. */
@Serializable
@JvmInline
value class PlayerId(val value: Int) {
    override fun toString(): String = "P$value"
}

/** Id kartu Bukti, mis. "S1", "L3", "I6". */
@Serializable
@JvmInline
value class CardId(val value: String) {
    override fun toString(): String = value
}

/** Nomor kartu Front 1..12, sama dengan langkah Jam Perang. */
@Serializable
@JvmInline
value class FrontId(val value: Int) {
    override fun toString(): String = "F$value"
}
