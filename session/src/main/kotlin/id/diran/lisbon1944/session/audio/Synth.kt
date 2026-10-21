package id.diran.lisbon1944.session.audio

import id.diran.lisbon1944.session.Sfx
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Semua audio game dibuat secara prosedural (tanpa aset berlisensi): gitar dipetik memakai Karplus-Strong,
 * efek dari derau & osilator sederhana. Hasilnya PCM 16-bit mono [RATE] Hz.
 */
object Synth {
    const val RATE = 22_050

    private fun buf(seconds: Double) = DoubleArray((seconds * RATE).roundToInt())

    private fun DoubleArray.toPcm(gain: Double = 1.0): ShortArray {
        val peak = maxOf(1e-9, this.maxOf { abs(it) })
        val g = gain * 0.89 / peak
        return ShortArray(size) { (this[it] * g * Short.MAX_VALUE).roundToInt().coerceIn(-32767, 32767).toShort() }
    }

    /** Senar dipetik (Karplus-Strong) ditambahkan ke [out] mulai [start] detik. */
    fun pluck(out: DoubleArray, start: Double, freq: Double, dur: Double, amp: Double, rnd: Random, bright: Double = 0.5, wrap: Boolean = false) {
        val n = max(2, (RATE / freq).roundToInt())
        val line = DoubleArray(n) { rnd.nextDouble(-1.0, 1.0) }
        // Prafilter: kecerahan petikan.
        for (i in 1 until n) line[i] = bright * line[i] + (1 - bright) * line[i - 1]
        val s0 = (start * RATE).roundToInt()
        val len = (dur * RATE).roundToInt()
        var idx = 0
        val decay = 0.996
        for (k in 0 until len) {
            var pos = s0 + k
            if (pos >= out.size) {
                if (!wrap) break
                pos -= out.size
                if (pos >= out.size) break
            }
            val cur = line[idx]
            val next = line[(idx + 1) % n]
            line[idx] = decay * 0.5 * (cur + next)
            val env = if (k < 40) k / 40.0 else 1.0
            out[pos] += amp * cur * env
            idx = (idx + 1) % n
        }
    }

    private fun lowpass(x: DoubleArray, a: Double) {
        var y = 0.0
        for (i in x.indices) { y += a * (x[i] - y); x[i] = y }
    }

    private fun noise(out: DoubleArray, start: Double, dur: Double, amp: Double, rnd: Random, decay: Double, lp: Double = 1.0) {
        val s0 = (start * RATE).roundToInt()
        val len = (dur * RATE).roundToInt()
        var y = 0.0
        for (k in 0 until len) {
            val i = s0 + k
            if (i !in out.indices) break
            y += lp * (rnd.nextDouble(-1.0, 1.0) - y)
            out[i] += amp * y * exp(-k / (decay * RATE))
        }
    }

    private fun tone(out: DoubleArray, start: Double, dur: Double, freq: Double, amp: Double, decay: Double, attack: Double = 0.003) {
        val s0 = (start * RATE).roundToInt()
        val len = (dur * RATE).roundToInt()
        val att = max(1, (attack * RATE).roundToInt())
        for (k in 0 until len) {
            val i = s0 + k
            if (i !in out.indices) break
            val env = min(1.0, k.toDouble() / att) * exp(-k / (decay * RATE))
            out[i] += amp * env * sin(2 * PI * freq * k / RATE)
        }
    }

    fun sfx(kind: Sfx): ShortArray {
        val r = Random(kind.ordinal * 7919 + 13)
        return when (kind) {
            Sfx.DICE -> buf(0.7).also { b ->
                // Dua dadu memantul di meja kayu: ketukan yang makin rapat.
                var t = 0.0
                var gap = 0.16
                repeat(7) {
                    noise(b, t, 0.05, 0.9 - it * 0.1, r, 0.008, 0.6)
                    tone(b, t, 0.05, 180.0 + r.nextInt(80), 0.4, 0.015)
                    t += gap
                    gap *= 0.7
                }
            }.toPcm(0.9)
            Sfx.STEP -> buf(0.06).also { b -> noise(b, 0.0, 0.06, 1.0, r, 0.01, 0.25) }.toPcm(0.35)
            Sfx.COIN -> buf(0.5).also { b ->
                for (t in listOf(0.0, 0.09)) {
                    tone(b, t, 0.4, 2093.0, 0.6, 0.12); tone(b, t, 0.4, 3136.0, 0.35, 0.08); tone(b, t, 0.4, 4699.0, 0.2, 0.05)
                }
            }.toPcm(0.6)
            Sfx.JACKPOT -> buf(1.6).also { b ->
                repeat(22) {
                    val t = r.nextDouble(0.0, 1.2)
                    val f = 1800.0 + r.nextInt(1600)
                    tone(b, t, 0.35, f, 0.5, 0.08); tone(b, t, 0.35, f * 1.5, 0.25, 0.05)
                }
            }.toPcm(0.8)
            Sfx.TELEGRAPH -> buf(0.9).also { b ->
                // ·−·· ·− (pola Morse pendek)
                var t = 0.0
                for (sym in ".-.. .-") {
                    when (sym) {
                        '.' -> { tone(b, t, 0.06, 820.0, 0.7, 1.0, 0.002); t += 0.1 }
                        '-' -> { tone(b, t, 0.18, 820.0, 0.7, 1.0, 0.002); t += 0.22 }
                        else -> t += 0.12
                    }
                }
            }.toPcm(0.55)
            Sfx.CIPHER -> buf(0.9).also { b ->
                // Roda sandi berputar: ratchet melambat.
                var t = 0.0
                var gap = 0.03
                while (t < 0.85) {
                    noise(b, t, 0.02, 0.8, r, 0.004, 0.9)
                    tone(b, t, 0.02, 1400.0, 0.3, 0.004)
                    t += gap
                    gap *= 1.09
                }
            }.toPcm(0.5)
            Sfx.SIREN -> buf(2.4).also { b ->
                var ph = 0.0
                for (i in b.indices) {
                    val t = i.toDouble() / RATE
                    val f = 520.0 + 160.0 * sin(2 * PI * 0.45 * t)
                    ph += 2 * PI * f / RATE
                    val env = min(1.0, t / 0.4) * min(1.0, (2.4 - t) / 0.6)
                    b[i] = env * (sin(ph) + 0.3 * sin(2 * ph))
                }
                lowpass(b, 0.12) // jauh
            }.toPcm(0.45)
            Sfx.STAMP -> buf(0.45).also { b ->
                tone(b, 0.0, 0.4, 70.0, 1.0, 0.09, 0.001)
                tone(b, 0.0, 0.2, 140.0, 0.5, 0.04, 0.001)
                noise(b, 0.0, 0.12, 0.8, r, 0.02, 0.5)
            }.toPcm(0.95)
            Sfx.CELL_DOOR -> buf(1.4).also { b ->
                noise(b, 0.0, 0.08, 1.0, r, 0.02, 0.7)
                for ((f, a) in listOf(196.0 to 0.8, 467.0 to 0.5, 811.0 to 0.35, 1290.0 to 0.2)) tone(b, 0.0, 1.3, f, a, 0.35, 0.001)
                noise(b, 0.9, 0.12, 0.5, r, 0.03, 0.4) // gerendel
            }.toPcm(0.8)
            Sfx.CARD -> buf(0.25).also { b -> noise(b, 0.0, 0.22, 1.0, r, 0.06, 0.35) }.toPcm(0.4)
            Sfx.ALARM -> buf(1.2).also { b ->
                for (k in 0 until 4) tone(b, k * 0.3, 0.25, if (k % 2 == 0) 880.0 else 660.0, 0.6, 1.0, 0.005)
            }.toPcm(0.5)
        }
    }

    /**
     * Musik latar ±16 detik yang bisa diulang mulus: progresi fado Am – Dm – E7 – Am dengan gitar arpeggio,
     * melodi gitar Portugis, lalu "gramofon": lowpass + derak.
     */
    fun music(seed: Int = 1944): ShortArray {
        val rnd = Random(seed)
        val bar = 4.0
        val b = buf(bar * 4)
        val chords = listOf(
            listOf(110.0, 164.81, 220.0, 261.63, 329.63),
            listOf(146.83, 220.0, 293.66, 349.23, 440.0),
            listOf(82.41, 123.47, 164.81, 207.65, 293.66),
            listOf(110.0, 164.81, 220.0, 261.63, 329.63),
        )
        val pattern = listOf(0, 2, 3, 4, 3, 2, 1, 3)
        chords.forEachIndexed { ci, ch ->
            for (k in 0 until 16) {
                val t = ci * bar + k * (bar / 16)
                val note = ch[pattern[k % pattern.size]]
                val amp = if (k % 8 == 0) 0.55 else 0.32
                pluck(b, t, note, 2.2, amp, rnd, bright = 0.45, wrap = true)
            }
        }
        val melody = listOf(
            0.0 to 659.25, 1.0 to 587.33, 1.5 to 523.25, 2.0 to 493.88, 3.0 to 523.25,
            4.0 to 698.46, 5.0 to 659.25, 5.5 to 587.33, 6.0 to 587.33, 7.0 to 523.25,
            8.0 to 493.88, 9.0 to 523.25, 9.5 to 587.33, 10.0 to 659.25, 11.0 to 830.61,
            12.0 to 880.0, 13.0 to 659.25, 13.5 to 587.33, 14.0 to 523.25, 15.0 to 493.88,
        )
        for ((t, f) in melody) {
            // Tremolo khas guitarra: tiga petikan cepat.
            for (k in 0 until 3) pluck(b, t + k * 0.07, f, 1.4, 0.22 - k * 0.04, rnd, bright = 0.8, wrap = true)
        }
        lowpass(b, 0.35)
        // Derak gramofon.
        val crackle = Random(seed + 1)
        for (i in b.indices) {
            if (crackle.nextInt(1800) == 0) b[i] += crackle.nextDouble(-0.25, 0.25)
            b[i] += crackle.nextDouble(-0.004, 0.004)
        }
        return b.toPcm(0.7)
    }

    /** Bungkus PCM menjadi berkas WAV (RIFF) agar bisa dimuat SoundPool/MediaPlayer. */
    fun wav(pcm: ShortArray, rate: Int = RATE): ByteArray {
        val data = pcm.size * 2
        val out = ByteArrayOutputStream(44 + data)
        fun i32(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF); out.write(v shr 16 and 0xFF); out.write(v shr 24 and 0xFF) }
        fun i16(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF) }
        out.write("RIFF".toByteArray()); i32(36 + data); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(rate); i32(rate * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(data)
        for (s in pcm) i16(s.toInt())
        return out.toByteArray()
    }
}
