package at.aau.pulverfass.app.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import androidx.annotation.RawRes
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Verwaltet Hintergrundmusik + One-Shot SFX-Wiedergabe.
 *
 * Lifecycle wird vom Caller (MainActivity) gemanagt:
 *  - [play] / [stop] / [pause] / [resume] für die Loop-Music
 *  - [playSfx] für einmalige Sound-Effects (auto-released onCompletion)
 *  - [release] beim onDestroy gibt alles frei (inkl. aktiver SFX)
 *
 * Mute-State persistiert via SharedPreferences (app-restart-stabil).
 */
class BackgroundMusicManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var player: MediaPlayer? = null
    private var currentTrack: Int? = null

    /**
     * Thread-safe Tracking der aktiven One-Shot SFX-Player damit wir sie bei
     * [release] freigeben falls Completion vorzeitig abgebrochen wurde.
     */
    private val activeSfxPlayers = CopyOnWriteArraySet<MediaPlayer>()

    val isMuted: Boolean
        get() = prefs.getBoolean(KEY_MUTED, false)

    fun play(
        @RawRes resId: Int,
        loop: Boolean = true,
    ) {
        if (currentTrack == resId && player?.isPlaying == true) return
        stop()
        currentTrack = resId
        if (isMuted) return
        player =
            MediaPlayer.create(appContext, resId)?.apply {
                isLooping = loop
                start()
            }
    }

    fun stop() {
        player?.let {
            runCatching {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        currentTrack = null
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
            if (player == null && currentTrack != null) {
                play(currentTrack!!)
            } else {
                resume()
            }
        }
    }

    /**
     * Spielt einen Sound-Effect einmalig ab.
     *
     * Player wird in [activeSfxPlayers] getrackt und gibt sich selbst frei
     * via [MediaPlayer.OnCompletionListener] / [MediaPlayer.OnErrorListener].
     * Falls die App zerstört wird bevor das passiert, räumt [release] auf.
     */
    fun playSfx(
        @RawRes resId: Int,
    ) {
        if (isMuted) return
        val sfxPlayer = MediaPlayer.create(appContext, resId) ?: return
        activeSfxPlayers.add(sfxPlayer)
        sfxPlayer.setOnCompletionListener { mp ->
            activeSfxPlayers.remove(mp)
            runCatching { mp.release() }
        }
        sfxPlayer.setOnErrorListener { mp, _, _ ->
            activeSfxPlayers.remove(mp)
            runCatching { mp.release() }
            true
        }
        sfxPlayer.start()
    }

    fun release() {
        stop()
        activeSfxPlayers.forEach { sfx ->
            runCatching {
                if (sfx.isPlaying) sfx.stop()
                sfx.release()
            }
        }
        activeSfxPlayers.clear()
    }

    companion object {
        private const val PREFS_NAME = "pulverfass_audio"
        private const val KEY_MUTED = "is_muted"
    }
}
