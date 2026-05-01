package at.aau.pulverfass.app.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ReconnectSessionStoreTest {
    @Test
    fun `shared preferences store should persist and clear reconnect session values`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SharedPreferencesReconnectSessionStore(context)

        store.clearSession()
        assertNull(store.readSessionToken())
        assertFalse(store.readWasGameStarted())

        store.saveSessionToken("123e4567-e89b-12d3-a456-426614174299")
        store.saveServerUrl("ws://127.0.0.1:8080/ws")
        store.saveWasGameStarted(true)

        assertEquals("123e4567-e89b-12d3-a456-426614174299", store.readSessionToken())
        assertEquals("ws://127.0.0.1:8080/ws", store.readServerUrl())
        assertTrue(store.readWasGameStarted())

        store.clearSession()
        assertNull(store.readSessionToken())
        assertEquals("ws://127.0.0.1:8080/ws", store.readServerUrl())
        assertFalse(store.readWasGameStarted())
    }

    @Test
    fun `shared preferences store should ignore blank persisted strings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SharedPreferencesReconnectSessionStore(context)

        store.clearSession()
        store.saveSessionToken(" ")
        store.saveServerUrl("")

        assertNull(store.readSessionToken())
        assertNull(store.readServerUrl())
    }

    @Test
    fun `shared preferences store should clear only session token when requested`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SharedPreferencesReconnectSessionStore(context)

        store.clearSession()
        store.saveSessionToken("123e4567-e89b-12d3-a456-426614174299")
        store.saveServerUrl("ws://127.0.0.1:8080/ws")
        store.saveWasGameStarted(true)

        store.clearSessionToken()

        assertNull(store.readSessionToken())
        assertEquals("ws://127.0.0.1:8080/ws", store.readServerUrl())
        assertTrue(store.readWasGameStarted())
    }

    @Test
    fun `no op store should never persist reconnect state`() {
        NoOpReconnectSessionStore.saveSessionToken("token")
        NoOpReconnectSessionStore.saveServerUrl("ws://127.0.0.1:8080/ws")
        NoOpReconnectSessionStore.saveWasGameStarted(true)
        NoOpReconnectSessionStore.clearSessionToken()
        NoOpReconnectSessionStore.clearSession()

        assertNull(NoOpReconnectSessionStore.readSessionToken())
        assertNull(NoOpReconnectSessionStore.readServerUrl())
        assertFalse(NoOpReconnectSessionStore.readWasGameStarted())
    }
}
