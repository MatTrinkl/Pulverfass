package at.aau.pulverfass.client.game

import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.client.lobby.Characters
import at.aau.pulverfass.client.lobby.LobbyPlayerUi
import at.aau.pulverfass.client.ui.map.GameMapRegionState
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.normalizePlayerDisplayName
import at.aau.pulverfass.shared.message.lobby.response.MapTerritoryStateSnapshot

private val NeutralTerritoryColor = Color(0xFFC2C2C2)

/**
 * Übergangsfarbe für das kurze Fenster zwischen Lobby-Join und Charakter-
 * Synchronisierung, in dem ein Spieler noch keine Charakter-ID hat. Im Normalfall
 * trägt jeder Spieler die Akzentfarbe seines Charakters.
 */
private val UnassignedPlayerColor = Color(0xFF9E9E9E)

/**
 * Abbildung zwischen Backend-Territories und vorhandenen Android-Kartenmasken.
 *
 * Die Map aus dem Backend nutzt deutsche IDs, während die aktuelle Android-Grafik
 * ältere englische Region-IDs besitzt. Diese Tabelle hält die Übersetzung an
 * einer Stelle, bis beide Seiten dieselben IDs verwenden.
 */
object GameMapTerritoryMapper {
    /**
     * Diese Tabelle ist die aktuelle Brücke zwischen Backend und Bildassets.
     * Links stehen die TerritoryIds aus shared/server, rechts die IDs der
     * Android-Masken und der Farbhitmap. Sobald beide Seiten dieselben IDs
     * verwenden, kann diese Übersetzung entfallen.
     */
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
            "ozeanien" to "oceania",
        )
    private val androidRegionIdToServer =
        serverToAndroidRegionId.entries.associate { (territoryId, regionId) ->
            regionId to territoryId
        }

    fun toAndroidRegionId(territoryId: TerritoryId): String? =
        serverToAndroidRegionId[territoryId.value] ?: territoryId.value

    fun toTerritoryId(regionId: String): TerritoryId =
        TerritoryId(androidRegionIdToServer[regionId] ?: regionId)
}

/**
 * Erzeugt die Spielerprojektion für HUD, Sidebar und Kartenfarben.
 *
 * @param players Lobby-Spieler aus dem Controller
 * @param ownPlayerId lokale Spieler-ID für noch nicht synchronisierte Charakterwechsel
 * @param ownCharacterId lokal gewählter Charakter, solange Server-Events noch nachlaufen
 * @return UI-Spieler mit stabiler Farbe und Avatar-Kürzel
 */
fun lobbyPlayersToGamePlayers(
    players: List<LobbyPlayerUi>,
    ownPlayerId: PlayerId? = null,
    ownCharacterId: String? = null,
): List<GamePlayerUi> =
    players.map { player ->
        val characterId = player.gameCharacterId(ownPlayerId, ownCharacterId)
        val displayName = normalizePlayerDisplayName(player.displayName).ifBlank { "?" }
        GamePlayerUi(
            playerId = player.playerId,
            name = displayName,
            avatarText = displayName.toAvatarText(),
            characterId = characterId,
            color = playerColorFor(characterId),
            isHost = player.isHost,
            connectionStatus = player.connectionStatus,
        )
    }

/**
 * Spielerfarbe = Akzentfarbe des gewählten Charakters.
 *
 * Da jeder Spieler beim Beitritt zwingend einen freien Charakter zugewiesen
 * bekommt und Doppelbelegungen ausgeschlossen sind, ist die Farbe automatisch
 * eindeutig. [UnassignedPlayerColor] greift nur im kurzen Fenster vor der
 * Charakter-Synchronisierung, wenn noch keine ID vorliegt.
 */
fun playerColorFor(characterId: String?): Color =
    characterId?.let { Characters.byId(it)?.color } ?: UnassignedPlayerColor

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
    /**
     * Der Renderer kennt nur Android-Region-IDs. Deshalb ist dies der letzte
     * Schritt vor der UI: fachliche TerritoryStates werden auf sichtbare
     * Regionen, Ownernamen, Truppenzahlen und Farben reduziert.
     */
    val playersById = lobbyPlayersToGamePlayers(players).associateBy { it.playerId }
    return territoryStates.values.mapNotNull { territory ->
        val regionId =
            GameMapTerritoryMapper.toAndroidRegionId(territory.territoryId)
                ?: return@mapNotNull null
        val owner = territory.ownerId?.let(playersById::get)
        regionId to
            GameMapRegionState(
                ownerPlayerId = territory.ownerId?.value?.toString() ?: NEUTRAL_OWNER_ID,
                ownerName =
                    owner?.name
                        ?: territory.ownerId?.let { DEPARTED_OWNER_NAME }
                        ?: NEUTRAL_OWNER_NAME,
                troopCount = territory.troopCount,
                // Unbeanspruchte und von verlassenen Spielern zurückgelassene
                // Gebiete teilen sich dasselbe neutrale Grau. Der Unterschied
                // bleibt nur über Name/Truppen sichtbar, nicht über die Farbe.
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

private fun LobbyPlayerUi.gameCharacterId(
    ownPlayerId: PlayerId?,
    ownCharacterId: String?,
): String? =
    if (playerId == ownPlayerId && ownCharacterId != null) {
        ownCharacterId
    } else {
        characterId
    }

private const val DEPARTED_OWNER_NAME = "Verlassener Spieler"
private const val NEUTRAL_OWNER_ID = "neutral"
private const val NEUTRAL_OWNER_NAME = "Neutral"
