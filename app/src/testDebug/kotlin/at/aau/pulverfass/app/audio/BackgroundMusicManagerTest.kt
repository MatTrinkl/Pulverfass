package at.aau.pulverfass.app.audio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prüft die gemerkten Mute-Zustände für Musik und Soundeffekte.
 *
 * Der Manager nutzt den Android-Kontext und wird deshalb als Instrumentation-
 * naher Test ausgeführt, ohne echte Audioausgabe bewerten zu müssen.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundMusicManagerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val manager = BackgroundMusicManager(context)

    @After
    fun tearDown() {
        manager.setMusicMuted(false)
        manager.setSfxMuted(false)
        manager.release()
    }

    @Test
    fun muted_default_state_is_false() {
        manager.setMusicMuted(false)
        assertFalse(manager.isMusicMuted)
    }

    @Test
    fun set_muted_true_persists_state() {
        manager.setMusicMuted(true)
        assertTrue(manager.isMusicMuted)
    }

    @Test
    fun toggle_muted_back_to_false_works() {
        manager.setMusicMuted(true)
        manager.setMusicMuted(false)
        assertFalse(manager.isMusicMuted)
    }

    @Test
    fun set_sfx_muted_true_persists_state() {
        manager.setSfxMuted(true)
        assertTrue(manager.isSfxMuted)
    }

    @Test
    fun toggle_sfx_muted_back_to_false_works() {
        manager.setSfxMuted(true)
        manager.setSfxMuted(false)
        assertFalse(manager.isSfxMuted)
    }
}
