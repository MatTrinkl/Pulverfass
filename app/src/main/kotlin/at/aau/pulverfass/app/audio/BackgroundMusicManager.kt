package at.aau.pulverfass.app.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import androidx.annotation.RawRes

/**
 * Verwaltet Hintergrundmusik-Wiedergabe via MediaPlayer.
 *
 * Lifecycle wird vom Caller (MainActivity) gemanagt:
 *  - [play] startet einen Track (loopable)
 *  - [pause] / [resume] für Activity-Lifecycle
 *  - [release] beim onDispose/onDestroy
 *
 * Mute-State persistiert via SharedPreferences damit's App-Restart-stabil ist.
 */
class BackgroundMusicManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var player: MediaPlayer? = null
    private var currentTrack: Int? = null

    val isMuted: Boolean
        get() = prefs.getBoolean(KEY_MUTED, false)

    /**
     * Spielt einen Raw-Resource-Track ab. Wenn derselbe Track schon läuft,
     * passiert nix. Bei muted: Track wird "vorgemerkt" aber nicht gestartet —
     * sobald unmuted, springt [resume] rein.
     */
    fun play(
        @RawRes resId: Int,
        loop: Boolean = true,
    ) {
        if (currentTrack == resId && player?.isPlaying == true) return
        stopInternal()
        currentTrack = resId
        if (isMuted) return
        player =
            MediaPlayer.create(appContext, resId)?.apply {
                isLooping = loop
                start()
            }
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        if (isMuted) return
        player?.takeIf { !it.isPlaying }?.start()
    }

    fun setMuted(muted: Boolean) {
        prefs.edit().putBoolean(KEY_MUTED, muted).apply()
        if (muted) {
            pause()
        } else {
            // Falls noch nie initialisiert aber Track vorgemerkt, starten
            if (player == null && currentTrack != null) {
                play(currentTrack!!)
            } else {
                resume()
            }
        }
    }

    /**
     * Spielt einen Sound-Effect einmalig ab (kein Loop).
     * MediaPlayer wird nach Ende automatisch freigegeben.
     * Wird stumm geschaltet wenn isMuted = true.
     */
    fun playSfx(
        @RawRes resId: Int,
    ) {
        if (isMuted) return
        MediaPlayer.create(appContext, resId)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }

    fun release() {
        stopInternal()
    }

    private fun stopInternal() {
        player?.let {
            runCatching {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
    }

    companion object {
        private const val PREFS_NAME = "pulverfass_audio"
        private const val KEY_MUTED = "is_muted"
    }
}
