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
 * Es werden nur die Bildabmessungen gelesen. Dadurch fällt der Ladevorgang früh
 * fehl, wenn eine Karte oder Territory-Maske nicht als Bitmap lesbar ist, ohne die
 * kompletten Pixel-Daten in den Speicher zu laden.
 */
object MapAssetPreloader {
    // Enthält Weltkarte, Farb-ID-Karte und alle Territory-Masken der Spielkarte.
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
                readImageBounds(resources = resources, assetId = assetId)
                withContext(Dispatchers.Main.immediate) {
                    onProgressChanged(index + 1, mapAssetIds.size)
                }
            }
        }
    }

    /**
     * Liest nur die Bitmap-Abmessungen eines Drawables, ohne Pixel-Daten zu allokieren.
     */
    private fun readImageBounds(
        resources: Resources,
        @DrawableRes assetId: Int,
    ) {
        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        BitmapFactory.decodeResource(resources, assetId, options)

        check(options.outWidth > 0 && options.outHeight > 0) {
            "Karten-Asset konnte nicht dekodiert werden: $assetId"
        }
    }
}
