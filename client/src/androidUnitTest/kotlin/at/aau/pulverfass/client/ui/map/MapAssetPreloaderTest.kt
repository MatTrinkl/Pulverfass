package at.aau.pulverfass.client.ui.map

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prüft den echten Asset-Preload der Karte unter Robolectric.
 *
 * Der Test verarbeitet den Main-Looper manuell weiter, damit Bitmap-Decodes und
 * Fortschrittscallbacks deterministisch abgeschlossen werden können.
 */
@RunWith(RobolectricTestRunner::class)
class MapAssetPreloaderTest {
    @Test
    fun preload_reports_progress_until_all_assets_are_loaded() {
        // Context wird nur noch gebraucht, damit Robolectric/AndroidContextProvider
        // die Compose-Resources auflösen können.
        ApplicationProvider.getApplicationContext<Context>()
        val updates = mutableListOf<Pair<Int, Int>>()
        val executor = Executors.newSingleThreadExecutor()

        try {
            val preloadFuture =
                executor.submit {
                    runBlocking {
                        MapAssetPreloader.preload { loaded, total ->
                            updates += loaded to total
                        }
                    }
                }

            val startedAt = System.nanoTime()
            while (!preloadFuture.isDone) {
                shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
                if (System.nanoTime() - startedAt > TimeUnit.SECONDS.toNanos(30)) {
                    throw AssertionError("MapAssetPreloader.preload timed out after 30 seconds")
                }
            }

            preloadFuture.get(1, TimeUnit.SECONDS)

            assertTrue(updates.isNotEmpty())
            val total = updates.last().second
            assertEquals(total, updates.size)
            assertEquals(total, updates.last().first)
            updates.forEachIndexed { index, (loaded, currentTotal) ->
                assertEquals(total, currentTotal)
                assertEquals(index + 1, loaded)
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
