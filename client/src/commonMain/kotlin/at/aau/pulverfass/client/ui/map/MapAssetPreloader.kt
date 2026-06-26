package at.aau.pulverfass.client.ui.map

import at.aau.pulverfass.client.resources.Res
import at.aau.pulverfass.client.resources.map_region_id
import at.aau.pulverfass.client.resources.map_world
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * Lädt die Karten-Assets vor, bevor die App in Lobby oder Spielbildschirm wechselt.
 *
 * Jedes Asset wird einmal vollständig dekodiert. Dadurch fällt der Ladevorgang
 * früh fehl, wenn eine Karte oder Territory-Maske nicht lesbar ist, und die
 * Dekodierung ist beim eigentlichen Kartenaufbau bereits warmgelaufen.
 */
object MapAssetPreloader {
    /*
     * Enthält Weltkarte, Farb-ID-Karte und alle Territory-Masken der Spielkarte.
     * Wenn eines davon fehlt, wäre entweder das Rendering oder die farbbasierte
     * Region-Selektion unzuverlässig.
     */
    private val mapAssets: List<DrawableResource> =
        listOf(
            Res.drawable.map_world,
            Res.drawable.map_region_id,
        ) + PulverfassMapDefaults.regions.map { region -> region.maskResId }

    /**
     * Dekodiert alle hinterlegten Karten-Assets im Hintergrund.
     *
     * [onProgressChanged] wird nach jedem erfolgreich dekodierten Asset auf dem
     * Main-Dispatcher aufgerufen, damit Ladebildschirme ihren Fortschritt anzeigen können.
     *
     * @param onProgressChanged Callback mit geladenen und insgesamt erwarteten Assets.
     */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun preload(onProgressChanged: (loaded: Int, total: Int) -> Unit) {
        val environment = getSystemResourceEnvironment()
        withContext(Dispatchers.Default) {
            mapAssets.forEachIndexed { index, asset ->
                val image =
                    getDrawableResourceBytes(environment, asset).decodeToImageBitmap()
                check(image.width > 0 && image.height > 0) {
                    "Karten-Asset konnte nicht dekodiert werden: $asset"
                }
                withContext(Dispatchers.Main.immediate) {
                    onProgressChanged(index + 1, mapAssets.size)
                }
            }
        }
    }
}
