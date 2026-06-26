package at.aau.pulverfass.client.audio

/**
 * Verwaltet Hintergrundmusik + One-Shot SFX-Wiedergabe.
 *
 * Der Plattform-Lifecycle steuert [pause], [resume] und [release]. Die Screens
 * wählen nur über [play] den passenden Track oder lösen über [playSfx]
 * kurze Effekte aus. Mute-Werte werden getrennt persistiert, damit Musik und
 * SFX unabhängig voneinander sofort reagieren und App-Neustarts überleben.
 *
 * Android implementiert die Wiedergabe über MediaPlayer + SharedPreferences,
 * iOS über AVAudioPlayer + NSUserDefaults.
 */
expect class BackgroundMusicManager() {
    /** `true`, wenn Musik dauerhaft stummgeschaltet ist. */
    val isMusicMuted: Boolean

    /** `true`, wenn Soundeffekte dauerhaft stummgeschaltet sind. */
    val isSfxMuted: Boolean

    /**
     * Spielt einen neuen Hintergrundtrack oder lässt den aktuellen Track
     * weiterlaufen.
     *
     * @param track abzuspielender Musiktrack.
     * @param loop `true`, wenn der Track endlos wiederholt werden soll.
     */
    fun play(
        track: MusicTrack,
        loop: Boolean = true,
    )

    /** Stoppt die Hintergrundmusik und gibt den Player frei. */
    fun stop()

    /** Pausiert die Hintergrundmusik (z.B. wenn die App in den Hintergrund geht). */
    fun pause()

    /** Setzt die Hintergrundmusik fort, sofern sie nicht stummgeschaltet ist. */
    fun resume()

    /**
     * Aktiviert oder deaktiviert Musik und merkt die Entscheidung dauerhaft.
     *
     * @param muted `true`, wenn Musik sofort pausiert und künftig stumm bleiben soll.
     */
    fun setMusicMuted(muted: Boolean)

    /**
     * Aktiviert oder deaktiviert SFX und stoppt laufende Effekte sofort.
     *
     * @param muted `true`, wenn SFX sofort beendet und künftig ignoriert werden sollen.
     */
    fun setSfxMuted(muted: Boolean)

    /**
     * Spielt einen Sound-Effect einmalig ab.
     *
     * @param sound abzuspielender Effekt.
     */
    fun playSfx(sound: SfxSound)

    /** Gibt alle Player-Ressourcen frei. */
    fun release()
}
