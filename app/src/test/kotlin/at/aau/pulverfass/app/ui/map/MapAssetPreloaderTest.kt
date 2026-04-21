package at.aau.pulverfass.app.ui.map

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

@RunWith(RobolectricTestRunner::class)
class MapAssetPreloaderTest {
    @Test
    fun preload_reports_progress_until_all_assets_are_loaded() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val updates = mutableListOf<Pair<Int, Int>>()
        val executor = Executors.newSingleThreadExecutor()

        try {
            val preloadFuture =
                executor.submit {
                    runBlocking {
                        MapAssetPreloader.preload(context.resources) { loaded, total ->
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
