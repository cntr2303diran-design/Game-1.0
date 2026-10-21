package id.diran.lisbon1944.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import id.diran.lisbon1944.session.Haptic
import id.diran.lisbon1944.session.Sfx
import id.diran.lisbon1944.session.audio.Synth
import java.io.File

/**
 * Audio game: efek lewat SoundPool, musik lewat MediaPlayer berulang. Semua suara disintesis sekali
 * (Synth, prosedural) lalu disimpan sebagai WAV di cache. Setiap kegagalan audio diabaikan — game tetap jalan.
 */
class SoundBoard(private val context: Context) {
    private var pool: SoundPool? = null
    private val ids = HashMap<Sfx, Int>()
    private var player: MediaPlayer? = null
    @Volatile private var ready = false
    private var musicVolume = 0.45f
    private var sfxVolume = 0.8f
    private var musicOn = false
    private var sfxOn = true

    /** Dipanggil di thread latar: menyintesis dan memuat semua suara. */
    fun prepare() {
        runCatching {
            val dir = File(context.cacheDir, "audio-v1").apply { mkdirs() }
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val p = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
            for (k in Sfx.entries) {
                val f = File(dir, "${k.name.lowercase()}.wav")
                if (!f.exists()) f.writeBytes(Synth.wav(Synth.sfx(k)))
                ids[k] = p.load(f.absolutePath, 1)
            }
            pool = p
            val m = File(dir, "fado.wav")
            if (!m.exists()) m.writeBytes(Synth.wav(Synth.music()))
            ready = true
            applyMusic()
        }
    }

    fun configure(s: Settings) {
        musicVolume = s.musicVolume
        sfxVolume = s.sfxVolume
        sfxOn = s.sfx
        musicOn = s.music
        applyMusic()
    }

    private fun applyMusic() {
        if (!ready) return
        runCatching {
            if (musicOn) {
                val mp = player ?: MediaPlayer().also {
                    it.setDataSource(File(File(context.cacheDir, "audio-v1"), "fado.wav").absolutePath)
                    it.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    it.isLooping = true
                    it.prepare()
                    player = it
                }
                mp.setVolume(musicVolume, musicVolume)
                if (!mp.isPlaying) mp.start()
            } else {
                player?.let { if (it.isPlaying) it.pause() }
            }
        }
    }

    fun play(s: Sfx, volumeScale: Float = 1f) {
        if (!sfxOn || !ready) return
        runCatching {
            val id = ids[s] ?: return
            val v = (sfxVolume * volumeScale).coerceIn(0f, 1f)
            pool?.play(id, v, v, 1, 0, 1f)
        }
    }

    fun pause() = runCatching { player?.let { if (it.isPlaying) it.pause() } }

    fun resume() = applyMusic()

    fun release() {
        runCatching { pool?.release() }
        runCatching { player?.release() }
        pool = null
        player = null
        ready = false
    }
}

/** Getaran singkat (BAGIAN 12.4: dadu, sewa besar, tuduhan). */
class Haptics(private val context: Context) {
    var enabled = true

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    fun buzz(h: Haptic) {
        if (!enabled) return
        runCatching {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            when (h) {
                Haptic.LIGHT -> v.vibrate(VibrationEffect.createOneShot(30, 90))
                Haptic.STRONG -> v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 60, 120), intArrayOf(0, 200, 0, 255), -1))
            }
        }
    }
}
