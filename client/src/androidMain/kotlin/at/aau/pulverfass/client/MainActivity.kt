package at.aau.pulverfass.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import at.aau.pulverfass.client.audio.BackgroundMusicManager

/**
 * Android-Shell des Multiplatform-Clients.
 *
 * Die Activity initialisiert den [AppContextHolder] und verwaltet den
 * [BackgroundMusicManager] als Activity-Field, damit Lifecycle-Callbacks
 * (onPause/onResume/onDestroy) auf die selbe Instanz zugreifen können wie der
 * Compose-Tree. Vollbild ist eine Eigenschaft der gesamten Activity und nicht
 * eines einzelnen Screens.
 */
class MainActivity : ComponentActivity() {
    private lateinit var musicManager: BackgroundMusicManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppContextHolder.context = applicationContext
        ExitHandler.handler = { finish() }
        musicManager = BackgroundMusicManager()

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        setContent {
            App(musicManager = musicManager)
        }
    }

    override fun onPause() {
        super.onPause()
        musicManager.pause()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        musicManager.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        musicManager.release()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * Stellt den immersiven App-Modus für den Activity-Window wieder her.
     *
     * Android darf Bars temporär per Wischgeste oder bei Lifecycle-/Fokuswechsel
     * anzeigen. Der Helper wird beim Start, bei Resume und nach erneutem
     * Window-Fokus aufgerufen, damit kein einzelner Screen die
     * Vollbildgarantie besitzen oder zurücksetzen muss.
     */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
