package at.aau.pulverfass.app.ui.map

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import at.aau.pulverfass.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MapAssetPreloader {
    private val mapAssetIds: List<Int> =
        listOf(
            R.drawable.map_world,
            R.drawable.map_region_id,
        ) + PulverfassMapDefaults.regions.map { region -> region.maskResId }

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

    private fun decodeAndRelease(
        resources: Resources,
        @DrawableRes assetId: Int,
    ) {
        BitmapFactory.decodeResource(resources, assetId)?.recycle()
    }
}
