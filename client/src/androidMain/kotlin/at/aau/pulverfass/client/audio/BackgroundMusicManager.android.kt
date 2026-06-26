package at.aau.pulverfass.client.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import androidx.annotation.RawRes
import at.aau.pulverfass.client.AppContextHolder
import at.aau.pulverfass.client.R
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Android-Implementierung über MediaPlayer + SharedPreferences.
 * 1:1 aus dem app-Modul übernommen; nur die Raw-Resource-IDs sind hinter den
 * plattformneutralen [MusicTrack]/[SfxSound]-Enums gekapselt.
 */
actual class BackgroundMusicManager actual constructor() {
    private val appContext = AppContextHolder.context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var player: MediaPlayer? = null
    private var currentTrack: MusicTrack? = null
    private var currentLoop: Boolean = true

    /**
     * Thread-safe Tracking der aktiven One-Shot SFX-Player damit wir sie bei
     * [release] freigeben falls Completion vorzeitig abgebrochen wurde.
     */
    private val activeSfxPlayers = CopyOnWriteArraySet<MediaPlayer>()

    actual val isMusicMuted: Boolean
        get() = prefs.getBoolean(KEY_MUSIC_MUTED, false)

    actual val isSfxMuted: Boolean
        get() = prefs.getBoolean(KEY_SFX_MUTED, false)

    @Synchronized
    actual fun play(
        track: MusicTrack,
        loop: Boolean,
    ) {
        if (currentTrack == track && currentLoop == loop && player?.isPlaying == true) return
        stop()
        currentTrack = track
        currentLoop = loop
        if (isMusicMuted) return
        /**
         * MediaPlayer.prepareAsync verhindert kurze UI-Hänger beim Route-Wechsel,
         * besonders wenn direkt danach ein Compose-Screen mit Video oder Karte
         * aufgebaut wird.
         */
        val afd = appContext.resources.openRawResourceFd(track.toRawRes())
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
    actual fun stop() {
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
    actual fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    @Synchronized
    actual fun resume() {
        if (isMusicMuted) return
        player?.takeIf { !it.isPlaying }?.start()
    }

    actual fun setMusicMuted(muted: Boolean) {
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

    @Synchronized
    actual fun setSfxMuted(muted: Boolean) {
        prefs.edit().putBoolean(KEY_SFX_MUTED, muted).apply()
        if (muted) stopActiveSfx()
    }

    @Synchronized
    actual fun playSfx(sound: SfxSound) {
        if (isSfxMuted) return
        val sfxPlayer = MediaPlayer.create(appContext, sound.toRawRes()) ?: return
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
    actual fun release() {
        stop()
        stopActiveSfx()
    }

    /**
     * Stoppt alle aktuell laufenden One-Shot-Sounds sofort.
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

    private companion object {
        const val PREFS_NAME = "pulverfass_audio"
        const val KEY_MUSIC_MUTED = "is_music_muted"
        const val KEY_SFX_MUTED = "is_sfx_muted"
    }
}

@RawRes
private fun MusicTrack.toRawRes(): Int =
    when (this) {
        MusicTrack.MAIN_MENU -> R.raw.music_main_menu
        MusicTrack.MAIN_MENU_ALT -> R.raw.music_main_menu_alt
        MusicTrack.MAIN_THEME_ALT -> R.raw.music_main_theme_alt
        MusicTrack.LOBBY_MENU -> R.raw.music_lobby_menu
        MusicTrack.LOBBY_WAITING -> R.raw.music_lobby_waiting
        MusicTrack.OPTIONS_MENU -> R.raw.music_options_menu
        MusicTrack.GAMEPLAY_LOOP -> R.raw.music_gameplay_loop
        MusicTrack.GAME_TENSION -> R.raw.music_game_tension
        MusicTrack.GAME_VICTORY -> R.raw.music_game_victory
        MusicTrack.GAME_LOSS -> R.raw.music_game_loss
        MusicTrack.CHARACTER_PICKER -> R.raw.music_character_picker
        MusicTrack.STUDIO_INTRO -> R.raw.music_studio_intro
        MusicTrack.BONUS_TRACK_01 -> R.raw.music_bonus_track_01
        MusicTrack.BONUS_TRACK_02 -> R.raw.music_bonus_track_02
        MusicTrack.BONUS_TRACK_03 -> R.raw.music_bonus_track_03
    }

@RawRes
private fun SfxSound.toRawRes(): Int =
    when (this) {
        SfxSound.UI_CLICK -> R.raw.sfx_ui_click
        SfxSound.CARD_SELECT -> R.raw.sfx_card_select
        SfxSound.CARD_SELECT_ALT -> R.raw.sfx_card_select_alt
        SfxSound.ATTACK_CONFIRM -> R.raw.sfx_attack_confirm
        SfxSound.ATTACK_ROLL -> R.raw.sfx_attack_roll
    }
