package at.aau.pulverfass.app.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class PlayerNameStoreTest {
    @Test
    fun `shared preferences store should persist player name across instances`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SharedPreferencesPlayerNameStore(context)

        store.savePlayerName("")
        assertNull(store.readPlayerName())

        store.savePlayerName("Anne Bonny")

        val restoredStore = SharedPreferencesPlayerNameStore(context)
        assertEquals("Anne Bonny", restoredStore.readPlayerName())
    }

    @Test
    fun `no op store should never persist player name`() {
        NoOpPlayerNameStore.savePlayerName("Anne Bonny")

        assertNull(NoOpPlayerNameStore.readPlayerName())
    }

    @Test
    fun `shared preferences store should persist character id across instances`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SharedPreferencesPlayerNameStore(context)

        store.saveCharacterId("doctor")

        val restoredStore = SharedPreferencesPlayerNameStore(context)
        assertEquals("doctor", restoredStore.readCharacterId())
    }

    @Test
    fun `shared preferences store should return null when no character id saved`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SharedPreferencesPlayerNameStore(context)
        store.saveCharacterId("")
        assertNull(store.readCharacterId())
    }

    @Test
    fun `no op store should never persist character id`() {
        NoOpPlayerNameStore.saveCharacterId("doctor")

        assertNull(NoOpPlayerNameStore.readCharacterId())
    }
}
