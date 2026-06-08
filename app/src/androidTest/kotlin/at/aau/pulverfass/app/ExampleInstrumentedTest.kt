package at.aau.pulverfass.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimaler Instrumentation-Smoke-Test für die installierte App.
 *
 * Der Test läuft auf Gerät oder Emulator und prüft, ob der Android-Testkontext
 * zur erwarteten Paket-ID der App gehört.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun usesCorrectPackageName() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("at.aau.pulverfass.app", appContext.packageName)
    }
}
