package at.aau.pulverfass.client.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private const val CHEAT_LIGHT_BASELINE_LUX = 8f
private const val CHEAT_LIGHT_COVERED_LUX = 2f

/**
 * Android-Implementierung über den Lichtsensor; 1:1 aus dem GameScreen des
 * app-Moduls extrahiert.
 *
 * Die Geste verlangt einen hellen Ausgangswert und danach ein deutliches
 * Abdunkeln, damit normale Raumlichtschwankungen keinen Bonus triggern.
 */
@Composable
actual fun LightSensorCheatTrigger(
    enabled: Boolean,
    onTriggered: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnTriggered = rememberUpdatedState(onTriggered)
    var previousLux by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context, enabled) {
        if (!enabled) {
            return@DisposableEffect onDispose {}
        }

        previousLux = null
        var triggered = false

        val sensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor == null) {
            return@DisposableEffect onDispose {}
        }

        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val lux = event.values.firstOrNull() ?: return
                    val wasBright = previousLux?.let { it >= CHEAT_LIGHT_BASELINE_LUX } ?: false
                    val isCovered = lux <= CHEAT_LIGHT_COVERED_LUX

                    if (wasBright && isCovered && !triggered) {
                        triggered = true
                        currentOnTriggered.value()
                    }

                    previousLux = lux
                }

                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int,
                ) = Unit
            }

        sensorManager.registerListener(
            listener,
            lightSensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        )

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
}
