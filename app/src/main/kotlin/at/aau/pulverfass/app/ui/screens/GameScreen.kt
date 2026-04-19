package at.aau.pulverfass.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.map.InteractiveGameMap
import at.aau.pulverfass.app.ui.map.MapSelectionOverlay
import at.aau.pulverfass.app.ui.map.PulverfassMapDefaults

@Composable
fun GameScreen() {
    var selectedRegionId by remember { mutableStateOf(PulverfassMapDefaults.regions.first().id) }
    val selectedRegion = PulverfassMapDefaults.regions.firstOrNull { it.id == selectedRegionId }

    Box(modifier = Modifier.fillMaxSize()) {
        InteractiveGameMap(
            regions = PulverfassMapDefaults.regions,
            selectedRegionId = selectedRegionId,
            onRegionSelected = { selectedRegionId = it.id },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Text(
                    text = stringResource(id = R.string.game_map_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        MapSelectionOverlay(
            selectedRegion = selectedRegion,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
        )
    }
}
