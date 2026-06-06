package at.aau.pulverfass.app.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import androidx.annotation.RawRes
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Verwaltet Hintergrundmusik + One-Shot SFX-Wiedergabe.
 *
 * Der Activity-Lifecycle steuert [pause], [resume] und [release]. Die Screens
 * wählen nur über [play] den passenden Track oder lösen über [playSfx]
 * kurze Effekte aus. Mute-Werte werden getrennt gespeichert, damit Musik und
 * SFX unabhängig voneinander sofort reagieren.
 *
 * @param context Android-Kontext für Ressourcen und private SharedPreferences.
 */
class BackgroundMusicManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var player: MediaPlayer? = null
    private var currentTrack: Int? = null
    private var currentLoop: Boolean = true

    /**
     * Thread-safe Tracking der aktiven One-Shot SFX-Player damit wir sie bei
     * [release] freigeben falls Completion vorzeitig abgebrochen wurde.
     */
    private val activeSfxPlayers = CopyOnWriteArraySet<MediaPlayer>()

    val isMusicMuted: Boolean
        get() = prefs.getBoolean(KEY_MUSIC_MUTED, false)

    val isSfxMuted: Boolean
        get() = prefs.getBoolean(KEY_SFX_MUTED, false)

    /**
     * Spielt einen neuen Hintergrundtrack oder lässt den aktuellen Track weiterlaufen.
     *
     * Die Vorbereitung läuft asynchron, damit Navigation und Animationen auf dem
     * UI-Thread nicht blockieren. [currentTrack] wird auch bei stummgeschalteter
     * Musik gesetzt, damit ein späteres Entstummen denselben Track wieder aufnehmen
     * kann.
     *
     * @param resId Raw-Resource des Musiktracks.
     * @param loop `true`, wenn der Track endlos wiederholt werden soll.
     */
    @Synchronized
    fun play(
        @RawRes resId: Int,
        loop: Boolean = true,
    ) {
        if (currentTrack == resId && currentLoop == loop && player?.isPlaying == true) return
        stop()
        currentTrack = resId
        currentLoop = loop
        if (isMusicMuted) return
        /*
         * MediaPlayer.prepareAsync verhindert kurze UI-Hänger beim Route-Wechsel,
         * besonders wenn direkt danach ein Compose-Screen mit Video oder Karte
         * aufgebaut wird.
         */
        val afd = appContext.resources.openRawResourceFd(resId)
        afd?.use { fileDesc ->
            player =
                MediaPlayer().apply {
                    setDataSource(fileDesc.fileDescriptor, fileDesc.startOffset, fileDesc.length)
                    setOnPreparedListener { mp ->
                        mp.isLooping = loop
                        mp.start()
                    }
                    setOnErrorListener { mp, _, _ ->
                        runCatching { mp.release() }
                        true
                    }
                    prepareAsync()
                }
        }
    }

    @Synchronized
    fun stop() {
        player?.let {
            runCatching {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        currentTrack = null
        currentLoop = true
    }

    @Synchronized
    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    @Synchronized
    fun resume() {
        if (isMusicMuted) return
        player?.takeIf { !it.isPlaying }?.start()
    }

    /**
     * Aktiviert oder deaktiviert Musik und merkt die Entscheidung dauerhaft.
     *
     * @param muted `true`, wenn Musik sofort pausiert und künftig stumm bleiben soll.
     */
    fun setMusicMuted(muted: Boolean) {
        prefs.edit().putBoolean(KEY_MUSIC_MUTED, muted).apply()
        if (muted) {
            pause()
        } else {
            val track = currentTrack
            if (player == null && track != null) {
                play(track)
            } else {
                resume()
            }
        }
    }

    /**
     * Aktiviert oder deaktiviert SFX und stoppt laufende Effekte sofort.
     *
     * @param muted `true`, wenn SFX sofort beendet und künftig ignoriert werden sollen.
     */
    @Synchronized
    fun setSfxMuted(muted: Boolean) {
        prefs.edit().putBoolean(KEY_SFX_MUTED, muted).apply()
        if (muted) stopActiveSfx()
    }

    /**
     * Spielt einen Sound-Effect einmalig ab.
     *
     * @param resId Raw-Resource des Effekts.
     */
    @Synchronized
    fun playSfx(
        @RawRes resId: Int,
    ) {
        if (isSfxMuted) return
        val sfxPlayer = MediaPlayer.create(appContext, resId) ?: return
        if (isSfxMuted) {
            runCatching { sfxPlayer.release() }
            return
        }
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

    @Synchronized
    fun release() {
        stop()
        stopActiveSfx()
    }

    /**
     * Stoppt alle aktuell laufenden One-Shot-Sounds sofort.
     *
     * Dadurch verhält sich der SFX-Schalter wie der Musik-Schalter: sobald
     * SFX deaktiviert wird, laufen auch bereits gestartete oder gerade erst
     * aus der Queue gestartete Sounds nicht hörbar weiter.
     */
    @Synchronized
    private fun stopActiveSfx() {
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
        private const val KEY_MUSIC_MUTED = "is_music_muted"
        private const val KEY_SFX_MUTED = "is_sfx_muted"
    }
}
