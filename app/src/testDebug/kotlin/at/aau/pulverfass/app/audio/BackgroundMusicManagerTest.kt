package at.aau.pulverfass.app.audio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundMusicManagerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val manager = BackgroundMusicManager(context)

    @After
    fun tearDown() {
        manager.setMusicMuted(false) // reset state for next test
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
}
