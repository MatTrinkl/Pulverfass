package at.aau.pulverfass.client.audio

import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults

/**
 * iOS-Implementierung über AVAudioPlayer + NSUserDefaults.
 *
 * Spiegelt die Semantik der Android-Implementierung: Mute-Flags werden
 * persistiert, [play] merkt sich den Track auch im stummen Zustand, damit
 * Entstummen denselben Track wieder aufnimmt.
 */
actual class BackgroundMusicManager actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    private var player: AVAudioPlayer? = null
    private var currentTrack: MusicTrack? = null
    private var currentLoop: Boolean = true

    /** Aktive One-Shot SFX-Player, damit [release] sie freigeben kann. */
    private val activeSfxPlayers = mutableListOf<AVAudioPlayer>()

    actual val isMusicMuted: Boolean
        get() = defaults.boolForKey(KEY_MUSIC_MUTED)

    actual val isSfxMuted: Boolean
        get() = defaults.boolForKey(KEY_SFX_MUTED)

    actual fun play(
        track: MusicTrack,
        loop: Boolean,
    ) {
        if (currentTrack == track && currentLoop == loop && player?.playing == true) return
        stop()
        currentTrack = track
        currentLoop = loop
        if (isMusicMuted) return

        val url = bundleUrl(track.fileName) ?: return
        player =
            AVAudioPlayer(contentsOfURL = url, error = null).apply {
                numberOfLoops = if (loop) -1 else 0
                prepareToPlay()
                play()
            }
    }

    actual fun stop() {
        player?.stop()
        player = null
        currentTrack = null
        currentLoop = true
    }

    actual fun pause() {
        player?.takeIf { it.playing }?.pause()
    }

    actual fun resume() {
        if (isMusicMuted) return
        player?.takeIf { !it.playing }?.play()
    }

    actual fun setMusicMuted(muted: Boolean) {
        defaults.setBool(muted, forKey = KEY_MUSIC_MUTED)
        if (muted) {
            pause()
        } else {
            val track = currentTrack
            if (player == null && track != null) {
                play(track, currentLoop)
            } else {
                resume()
            }
        }
    }

    actual fun setSfxMuted(muted: Boolean) {
        defaults.setBool(muted, forKey = KEY_SFX_MUTED)
        if (muted) stopActiveSfx()
    }

    actual fun playSfx(sound: SfxSound) {
        if (isSfxMuted) return
        val url = bundleUrl(sound.fileName) ?: return
        val sfxPlayer =
            AVAudioPlayer(contentsOfURL = url, error = null).apply {
                prepareToPlay()
            }
        activeSfxPlayers.removeAll { !it.playing }
        activeSfxPlayers.add(sfxPlayer)
        sfxPlayer.play()
    }

    actual fun release() {
        stop()
        stopActiveSfx()
    }

    /** Stoppt alle aktuell laufenden One-Shot-Sounds sofort. */
    private fun stopActiveSfx() {
        activeSfxPlayers.forEach { sfx ->
            if (sfx.playing) sfx.stop()
        }
        activeSfxPlayers.clear()
    }

    private fun bundleUrl(name: String): NSURL? =
        NSBundle.mainBundle.URLForResource(
            name,
            withExtension = "mp3",
        )

    private companion object {
        const val KEY_MUSIC_MUTED = "is_music_muted"
        const val KEY_SFX_MUTED = "is_sfx_muted"
    }
}
