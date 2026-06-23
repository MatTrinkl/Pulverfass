package at.aau.pulverfass.app.ui.screens

import androidx.compose.ui.graphics.Color
import at.aau.pulverfass.app.game.AttackUiState
import at.aau.pulverfass.app.game.AutoAttackUiState
import at.aau.pulverfass.app.game.GamePlayerUi
import at.aau.pulverfass.app.game.GameUiState
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.GameStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Prüft die reine Zustandslogik des GameScreens ohne Compose-Rendering.
 *
 * Im Fokus steht das Angriffs-Overlay, weil es nur während einer laufenden
 * Angriffsanfrage sichtbar sein darf und seine Daten aus Auswahl, UI-State und
 * Spieleranzeige zusammensetzt.
 */
class GameScreenStateTest {
    @Test
    fun `createAttackResolutionOverlayState returns null when no attack request is pending`() {
        val result =
            createAttackResolutionOverlayState(
                selection = "siberia" to "japan",
                uiState = pendingAttackUiState(),
                players = listOf(gamePlayer()),
                fallbackPlayerName = "Fallback",
                isAttackRequestPending = false,
            )

        assertNull(result)
    }

    @Test
    fun `createAttackResolutionOverlayState returns null when attack selection is incomplete`() {
        val result =
            createAttackResolutionOverlayState(
                selection = null,
                uiState = pendingAttackUiState(),
                players = listOf(gamePlayer()),
                fallbackPlayerName = "Fallback",
                isAttackRequestPending = true,
            )

        assertNull(result)
    }

    @Test
    fun `createAttackResolutionOverlayState returns null during running auto attack`() {
        val result =
            createAttackResolutionOverlayState(
                selection = "siberia" to "japan",
                uiState =
                    pendingAttackUiState().copy(
                        attackState =
                            AttackUiState(
                                attackTroops = 3,
                                autoAttack =
                                    AutoAttackUiState(
                                        isEnabled = true,
                                        isAwaitingResult = true,
                                    ),
                            ),
                    ),
                players = listOf(gamePlayer()),
                fallbackPlayerName = "Fallback",
                isAttackRequestPending = true,
            )

        assertNull(result)
    }

    @Test
    fun `createAttackResolutionOverlayState builds pending attack announcement state`() {
        val result =
            createAttackResolutionOverlayState(
                selection = "siberia" to "japan",
                uiState = pendingAttackUiState(),
                players = listOf(gamePlayer()),
                fallbackPlayerName = "Fallback",
                isAttackRequestPending = true,
            )

        assertEquals(
            AttackResolutionOverlayState(
                attackerName = "Alice",
                fromRegionId = "siberia",
                toRegionId = "japan",
                troopCount = 3,
            ),
            result,
        )
    }

    @Test
    fun `createWinningOverlayState returns null for running match`() {
        val result =
            createWinningOverlayState(
                uiState = GameUiState(gameStatus = GameStatus.RUNNING),
                players = listOf(gamePlayer()),
                localPlayerId = PlayerId(1),
            )

        assertNull(result)
    }

    @Test
    fun `createWinningOverlayState resolves local winner`() {
        val player = gamePlayer()
        val result =
            createWinningOverlayState(
                uiState =
                    GameUiState(
                        gameStatus = GameStatus.FINISHED,
                        winnerPlayerId = player.playerId,
                    ),
                players = listOf(player),
                localPlayerId = player.playerId,
            )

        assertEquals(
            WinningOverlayState(
                winnerPlayer = player,
                winnerName = "Alice",
                isLocalWinner = true,
            ),
            result,
        )
    }

    private fun pendingAttackUiState(): GameUiState =
        GameUiState(
            activePlayerId = PlayerId(1),
            attackState = AttackUiState(attackTroops = 3),
        )

    private fun gamePlayer(): GamePlayerUi =
        GamePlayerUi(
            playerId = PlayerId(1),
            name = "Alice",
            avatarText = "A",
            color = Color(0xFFA6342B),
        )
}
