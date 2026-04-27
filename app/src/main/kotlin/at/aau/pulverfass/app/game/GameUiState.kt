package at.aau.pulverfass.app.game

import at.aau.pulverfass.app.ui.map.GameMapRegionState
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.TurnPhase

/**
 * Android-Projektion des serverautoritativen öffentlichen GameStates.
 *
 * Der Zustand hält shared-IDs als fachliche Quelle und bereitet zusätzlich die
 * vorhandene Karten-UI mit Android-Region-IDs auf.
 */
data class GameUiState(
    val isStarted: Boolean = false,
    val isCatchingUp: Boolean = false,
    val isDesynced: Boolean = false,
    val stateVersion: Long = 0,
    val schemaVersion: Int? = null,
    val mapHash: String? = null,
    val activePlayerId: PlayerId? = null,
    val turnPhase: TurnPhase? = null,
    val turnCount: Int = 0,
    val startPlayerId: PlayerId? = null,
    val isPaused: Boolean = false,
    val pauseReason: String? = null,
    val pausedPlayerId: PlayerId? = null,
    val selectedRegionId: String? = null,
    val cardsVisible: Boolean = false,
    val definitionTerritoryIds: List<TerritoryId> = emptyList(),
    val territoryStates: Map<TerritoryId, GameTerritoryUiState> = emptyMap(),
    val regionStates: Map<String, GameMapRegionState> = emptyMap(),
    val handCards: List<String> = emptyList(),
    val secretObjectives: List<String> = emptyList(),
    val lastSyncError: String? = null,
) {
    fun canRequestTurnAdvance(localPlayerId: PlayerId?): Boolean =
        localPlayerId != null &&
            activePlayerId == localPlayerId &&
            turnPhase != null &&
            !isPaused &&
            !isCatchingUp &&
            !isDesynced
}

/**
 * Fachlicher Territory-Zustand mit shared-ID, bevor er auf Android-Masken
 * abgebildet wird.
 */
data class GameTerritoryUiState(
    val territoryId: TerritoryId,
    val ownerId: PlayerId?,
    val troopCount: Int,
)
