package at.aau.pulverfass.app.game

import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.app.lobby.LobbyPlayerUi
import at.aau.pulverfass.app.ui.map.GameMapRegionState
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot

private val PlayerPalette =
    listOf(
        Color(0xFF6FD4C5),
        Color(0xFFE0B35C),
        Color(0xFFE78D91),
        Color(0xFF79A8E8),
        Color(0xFFA7D36B),
        Color(0xFFB78BEA),
    )

private val NeutralTerritoryColor = Color(0xFF8F8F8F)

/**
 * Abbildung zwischen Backend-Territories und vorhandenen Android-Kartenmasken.
 *
 * Die Map aus dem Backend nutzt deutsche IDs, während die aktuelle Android-Grafik
 * ältere englische Region-IDs besitzt. Diese Tabelle hält die Übersetzung an
 * einer Stelle, bis beide Seiten dieselben IDs verwenden.
 */
object GameMapTerritoryMapper {
    private val serverToAndroidRegionId =
        mapOf(
            "argentinien" to "argentina",
            "brasilien" to "brazil",
            "mittelamerika" to "mexico",
            "usa" to "america",
            "andengemeinschaft" to "andean_community",
            "alaska" to "east_siberia",
            "kanada" to "canada",
            "groenland" to "greenland",
            "grossbritannien" to "british_islands",
            "westeuropa" to "west_europe",
            "skandinavien" to "scandinavia",
            "mitteleuropa" to "central_europe",
            "russland" to "russia",
            "naher_osten" to "middle_east",
            "sibirien" to "siberia",
            "china" to "china",
            "japan" to "japan",
            "ferner_osten" to "orient",
            "australien" to "australia",
            "aegypten" to "egypt",
            "sahara" to "west_africa",
            "zentral_afrika" to "central_africa",
            "sued_afrika" to "south_africa",
        )

    fun toAndroidRegionId(territoryId: TerritoryId): String? =
        serverToAndroidRegionId[territoryId.value] ?: territoryId.value
}

fun lobbyPlayersToGamePlayers(players: List<LobbyPlayerUi>): List<GamePlayerUi> =
    players.mapIndexed { index, player ->
        GamePlayerUi(
            playerId = player.playerId,
            name = player.displayName,
            avatarText = player.displayName.toAvatarText(),
            color = PlayerPalette[index % PlayerPalette.size],
            isHost = player.isHost,
        )
    }

fun territorySnapshotsToUiStates(
    territoryStates: List<MapTerritoryStateSnapshot>,
): Map<TerritoryId, GameTerritoryUiState> =
    territoryStates.associate { snapshot ->
        snapshot.territoryId to
            GameTerritoryUiState(
                territoryId = snapshot.territoryId,
                ownerId = snapshot.ownerId,
                troopCount = snapshot.troopCount,
            )
    }

fun buildRegionStates(
    territoryStates: Map<TerritoryId, GameTerritoryUiState>,
    players: List<LobbyPlayerUi>,
): Map<String, GameMapRegionState> {
    val playersById = lobbyPlayersToGamePlayers(players).associateBy { it.playerId }
    return territoryStates.values.mapNotNull { territory ->
        val regionId =
            GameMapTerritoryMapper.toAndroidRegionId(territory.territoryId)
                ?: return@mapNotNull null
        val owner = territory.ownerId?.let(playersById::get)
        regionId to
            GameMapRegionState(
                ownerPlayerId = owner?.playerId?.value?.toString() ?: NEUTRAL_OWNER_ID,
                ownerName = owner?.name ?: NEUTRAL_OWNER_NAME,
                troopCount = territory.troopCount,
                accentColor = owner?.color ?: NeutralTerritoryColor,
            )
    }.toMap()
}

internal fun String.toAvatarText(): String =
    trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }

private const val NEUTRAL_OWNER_ID = "neutral"
private const val NEUTRAL_OWNER_NAME = "Neutral"
