package at.aau.pulverfass.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// einfacher instrumentierter test der auf einem android gerät läuft
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun usesCorrectPackageName() {
        // holt den kontext der app unter test
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        // überprüft ob der paketname korrekt ist
        assertEquals("at.aau.pulverfass.app", appContext.packageName)
    }
}
