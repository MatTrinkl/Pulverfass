package at.aau.pulverfass.app.ui.map

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import at.aau.pulverfass.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lädt die Karten-Assets vor, bevor die App in Lobby oder Spielbildschirm wechselt.
 *
 * Die Bitmaps werden nur dekodiert und direkt wieder freigegeben. Dadurch fällt der
 * Ladevorgang früh fehl, wenn eine Karte oder Territory-Maske nicht dekodierbar ist.
 */
object MapAssetPreloader {
    // Enthält Weltkarte, Farb-ID-Karte und alle Territory-Masken der Demo-Karte.
    private val mapAssetIds: List<Int> =
        listOf(
            R.drawable.map_world,
            R.drawable.map_region_id,
        ) + PulverfassMapDefaults.regions.map { region -> region.maskResId }

    /**
     * Dekodiert alle hinterlegten Karten-Assets im Hintergrund.
     *
     * [onProgressChanged] wird nach jedem erfolgreich dekodierten Asset auf dem
     * Main-Dispatcher aufgerufen, damit Ladebildschirme ihren Fortschritt anzeigen können.
     */
    suspend fun preload(
        resources: Resources,
        onProgressChanged: (loaded: Int, total: Int) -> Unit,
    ) {
        withContext(Dispatchers.Default) {
            mapAssetIds.forEachIndexed { index, assetId ->
                decodeAndRelease(resources = resources, assetId = assetId)
                withContext(Dispatchers.Main.immediate) {
                    onProgressChanged(index + 1, mapAssetIds.size)
                }
            }
        }
    }

    /**
     * Dekodiert ein Drawable einmal und gibt das erzeugte Bitmap sofort wieder frei.
     */
    private fun decodeAndRelease(
        resources: Resources,
        @DrawableRes assetId: Int,
    ) {
        BitmapFactory.decodeResource(resources, assetId)?.recycle()
    }
}
