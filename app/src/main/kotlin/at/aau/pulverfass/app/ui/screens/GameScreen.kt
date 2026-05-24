package at.aau.pulverfass.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.game.GamePlayerUi
import at.aau.pulverfass.app.game.GameUiState
import at.aau.pulverfass.app.game.PrivateHandCardUi
import at.aau.pulverfass.app.game.ReinforcementUiState
import at.aau.pulverfass.app.game.lobbyPlayersToGamePlayers
import at.aau.pulverfass.app.lobby.LobbyCommandKey
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.ui.map.InteractiveGameMap
import at.aau.pulverfass.app.ui.map.InteractiveGameMapOptions
import at.aau.pulverfass.app.ui.map.PulverfassMapDefaults
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import kotlinx.coroutines.delay

private val HudSurfaceColor = Color.White
private val HudSurfaceMutedColor = Color(0xFFF1F1F1)
private val HudBorderColor = Color.Black
private val HudContentColor = Color.Black
private val HudInverseColor = Color.White
private val TopBarHeight = 52.dp
private val BottomBarHeight = 54.dp
private val SidebarWidth = 156.dp
private val CardsSidebarWidth = SidebarWidth
private const val SYNC_FEEDBACK_DELAY_MILLIS = 500L

/**
 * Einstiegspunkt des Spielbildschirms.
 *
 * @param controller gemeinsamer LobbyController, der Lobby-, Netzwerk- und
 * GameState verwaltet
 */
@Composable
fun GameScreen(controller: LobbyController) {
    val lobbyState by controller.state.collectAsState()
    val players = remember(lobbyState.players) { lobbyPlayersToGamePlayers(lobbyState.players) }
    val mapPainter = painterResource(id = R.drawable.map_world)

    GameScreenContent(
        contentState =
            GameScreenContentState(
                players = players,
                localPlayerId = lobbyState.ownPlayerId,
                uiState = lobbyState.gameState,
                isConnected = lobbyState.isConnected,
                pendingCommandKeys = lobbyState.pendingCommandKeys,
                mapPainter = mapPainter,
            ),
        actions =
            GameScreenActions(
                onRegionSelected = controller::selectGameRegion,
                onToggleCards = controller::toggleCards,
                onAdvanceTurn = controller::advanceTurn,
                onAdjustReinforcementPlacementAmount =
                    controller::adjustReinforcementPlacementAmount,
                onPlaceReinforcements = controller::placeReinforcements,
                onConfirmReinforcementsDone = controller::confirmReinforcementsDone,
                onToggleTradeInCard = controller::toggleTradeInCard,
                onTradeInCards = controller::tradeInCards,
                onRefreshGameState = controller::refreshGameState,
            ),
    )
}

internal data class GameScreenContentState(
    val players: List<GamePlayerUi>,
    val localPlayerId: PlayerId?,
    val uiState: GameUiState,
    val isConnected: Boolean,
    val pendingCommandKeys: Set<LobbyCommandKey>,
    val mapPainter: Painter,
)

internal data class GameScreenActions(
    val onRegionSelected: (String) -> Unit,
    val onToggleCards: () -> Unit,
    val onAdvanceTurn: () -> Unit,
    val onAdjustReinforcementPlacementAmount: (Int) -> Unit = {},
    val onPlaceReinforcements: () -> Unit = {},
    val onConfirmReinforcementsDone: () -> Unit = {},
    val onToggleTradeInCard: (CardId) -> Unit = {},
    val onTradeInCards: () -> Unit = {},
    val onRefreshGameState: () -> Unit,
)

/**
 * Baut das aktive Spielfeld aus Karte, HUD, Seitenteilen und servergebundenen Aktionen.
 *
 * Der Screen sendet selbst keine Protokollnachrichten. Er entscheidet anhand
 * des serverautoritativen Zustands und der ausstehenden Commands, welche
 * Bedienhandlungen angeboten werden, und reicht diese an den Controller
 * weiter. Normale Phasenwechsel und das Abschließen der Verstärkungsphase
 * teilen sich beispielsweise denselben sichtbaren Button, benötigen aber
 * unterschiedliche Backend-Requests.
 *
 * @param contentState darstellbarer Zustand inklusive Karte, Spieler und Pending-Requests
 * @param actions Controller-Callbacks für die ausgelösten Bedienhandlungen
 */
@Composable
internal fun GameScreenContent(
    contentState: GameScreenContentState,
    actions: GameScreenActions,
) {
    val players = contentState.players
    val localPlayerId = contentState.localPlayerId
    val uiState = contentState.uiState
    val isConnected = contentState.isConnected
    val pendingCommandKeys = contentState.pendingCommandKeys
    val mapPainter = contentState.mapPainter
    val onRegionSelected = actions.onRegionSelected
    val onToggleCards = actions.onToggleCards
    val onAdvanceTurn = actions.onAdvanceTurn
    val onAdjustReinforcementPlacementAmount = actions.onAdjustReinforcementPlacementAmount
    val onPlaceReinforcements = actions.onPlaceReinforcements
    val onConfirmReinforcementsDone = actions.onConfirmReinforcementsDone
    val onToggleTradeInCard = actions.onToggleTradeInCard
    val onTradeInCards = actions.onTradeInCards
    val onRefreshGameState = actions.onRefreshGameState
    val personalPlayer = players.firstOrNull { it.playerId == localPlayerId } ?: fallbackPlayer()
    val canUseGameActions = uiState.canUseGameActions(localPlayerId, isConnected)

    /*
     * Refresh-Pending bündelt die drei Snapshot-Requests, aus denen der aktuelle
     * öffentliche und private Spielstand besteht. Der Reload-Button bleibt
     * gesperrt, bis diese Runde abgeschlossen ist.
     */
    val isRefreshPending =
        pendingCommandKeys.any {
            it == LobbyCommandKey.MAP_GET ||
                it == LobbyCommandKey.TURN_STATE_GET ||
                it == LobbyCommandKey.CATCH_UP
        }
    val isReinforcementCommandPending =
        pendingCommandKeys.any {
            it == LobbyCommandKey.PLACE_REINFORCEMENTS ||
                it == LobbyCommandKey.CONFIRM_REINFORCEMENTS_DONE ||
                it == LobbyCommandKey.TRADE_IN_CARDS
        }
    val canManageReinforcements = uiState.canManageReinforcements(localPlayerId, isConnected)
    val remainingReinforcementAmount = uiState.reinforcementState.pendingAmount ?: 0

    /*
     * Das Platzierungs-Panel ist keine dauerhafte Phasenanzeige: Es wird nur
     * mit einem ausgewählten Ziel und einem positiven Restpool eingeblendet.
     * Ohne diese Voraussetzung würde es nur den zoombaren Kartenausschnitt
     * verdecken; bei einem leeren Pool übernimmt "Phase beenden".
     */
    val reinforcementPanelRegionId =
        if (
            uiState.turnPhase == TurnPhase.REINFORCEMENTS &&
            canManageReinforcements &&
            remainingReinforcementAmount > 0
        ) {
            uiState.selectedRegionId
        } else {
            null
        }

    /*
     * Der bestehende Button "Phase beenden" hat je nach Phase eine andere
     * Protokollbedeutung: TurnAdvance in regulären Phasen, aber
     * ConfirmReinforcementsDone nach vollständiger Verstärkungsplatzierung.
     */
    val canEndCurrentPhase =
        if (uiState.turnPhase == TurnPhase.REINFORCEMENTS) {
            uiState.canConfirmReinforcementsDone(localPlayerId, isConnected) &&
                !isReinforcementCommandPending
        } else {
            uiState.canRequestTurnAdvance(localPlayerId, isConnected) &&
                !pendingCommandKeys.contains(LobbyCommandKey.TURN_ADVANCE)
        }
    val onEndCurrentPhase =
        if (uiState.turnPhase == TurnPhase.REINFORCEMENTS) {
            onConfirmReinforcementsDone
        } else {
            onAdvanceTurn
        }
    var showCatchUpFeedback by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isCatchingUp) {
        showCatchUpFeedback = false
        if (uiState.isCatchingUp) {
            delay(SYNC_FEEDBACK_DELAY_MILLIS)
            showCatchUpFeedback = true
        }
    }

    /*
     * Priorität der Statusanzeige: Verbindungsausfall schlägt Catch-up,
     * Catch-up schlägt Desync, danach kommen konkrete Fehler und zuletzt reine
     * Auswahlhinweise. Sehr kurze Catch-ups bleiben unsichtbar, damit normale
     * Serverantworten keinen flackernden Synchronisationshinweis auslösen.
     */
    val statusMessage =
        when {
            !isConnected -> stringResource(id = R.string.game_sync_reconnecting)
            uiState.isCatchingUp && showCatchUpFeedback ->
                stringResource(id = R.string.game_sync_catching_up)
            uiState.isCatchingUp -> null
            uiState.isDesynced ->
                uiState.lastSyncError ?: stringResource(id = R.string.game_sync_desynced)
            uiState.lastSyncError != null -> uiState.lastSyncError
            uiState.selectionMessage != null -> uiState.selectionMessage
            else -> null
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("game_screen_root"),
    ) {
        InteractiveGameMap(
            regions = PulverfassMapDefaults.regions,
            regionStates = uiState.regionStates,
            selectedRegionId = uiState.selectedRegionId,
            onRegionSelected = { region ->
                /*
                 * Die Karte bleibt immer zoombar und sichtbar. Fachliche Eingaben
                 * werden aber nur weitergereicht, wenn der lokale Spieler gerade
                 * handeln darf und der Client synchron verbunden ist.
                 */
                if (canUseGameActions) {
                    onRegionSelected(region.id)
                }
            },
            options = InteractiveGameMapOptions(backgroundPainter = mapPainter),
            modifier = Modifier.fillMaxSize(),
        )

        GameTopBar(
            personalPlayer = personalPlayer,
            phase = uiState.turnPhase,
            round = uiState.turnCount.coerceAtLeast(1),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
        )

        if (statusMessage != null) {
            GameStatusBanner(
                message = statusMessage,
                canRefresh = isConnected && !isRefreshPending,
                isRefreshPending = isRefreshPending,
                onRefreshGameState = onRefreshGameState,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = TopBarHeight)
                        .fillMaxWidth(),
            )
        }

        if (uiState.isCatchingUp && showCatchUpFeedback) {
            SyncProgressOverlay(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        CardsSidebar(
            player = personalPlayer,
            handCards = uiState.handCards,
            privateHandCards = uiState.privateHandCards,
            selectedTradeInCardIds = uiState.selectedTradeInCardIds,
            showTradeControls = uiState.turnPhase == TurnPhase.REINFORCEMENTS,
            canSelectTradeCards =
                uiState.canUseGameActions(localPlayerId, isConnected) &&
                    !isReinforcementCommandPending,
            canTradeInCards =
                uiState.canTradeInCards(localPlayerId, isConnected) &&
                    !isReinforcementCommandPending,
            isTradePending = pendingCommandKeys.contains(LobbyCommandKey.TRADE_IN_CARDS),
            onToggleTradeInCard = onToggleTradeInCard,
            onTradeInCards = onTradeInCards,
            isVisible = uiState.cardsVisible,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = TopBarHeight, bottom = BottomBarHeight)
                    .requiredWidth(CardsSidebarWidth)
                    .fillMaxHeight(),
        )

        PlayerSidebar(
            players = players,
            activePlayerId = uiState.activePlayerId,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = TopBarHeight, bottom = BottomBarHeight)
                    .width(SidebarWidth)
                    .fillMaxHeight(),
        )

        reinforcementPanelRegionId?.let { selectedRegionId ->
            ReinforcementPanel(
                reinforcementState = uiState.reinforcementState,
                remainingAmount = remainingReinforcementAmount,
                placementAmount = uiState.reinforcementPlacementAmount,
                selectedRegionId = selectedRegionId,
                canAdjust =
                    canManageReinforcements &&
                        !isReinforcementCommandPending,
                canPlace =
                    uiState.canPlaceReinforcements(localPlayerId, isConnected) &&
                        !isReinforcementCommandPending,
                onDismiss = { onRegionSelected(selectedRegionId) },
                onAdjustPlacementAmount = onAdjustReinforcementPlacementAmount,
                onPlace = onPlaceReinforcements,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = BottomBarHeight + 8.dp),
            )
        }

        BottomActionClusters(
            currentPhase = uiState.turnPhase,
            canUseLocalInput = isConnected && !uiState.isCatchingUp && !uiState.isDesynced,
            canEndPhase = canEndCurrentPhase,
            cardsVisible = uiState.cardsVisible,
            onToggleCards = onToggleCards,
            onEndPhase = onEndCurrentPhase,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
        )
    }
}

@Composable
private fun GameStatusBanner(
    message: String,
    canRefresh: Boolean,
    isRefreshPending: Boolean,
    onRefreshGameState: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("game_sync_banner"),
        shape = RoundedCornerShape(0.dp),
        color = HudSurfaceMutedColor,
        contentColor = HudContentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = HudContentColor,
            )
            FilledTonalButton(
                onClick = onRefreshGameState,
                enabled = canRefresh,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("game_sync_reload_button"),
            ) {
                Text(
                    text =
                        if (isRefreshPending) {
                            stringResource(id = R.string.game_sync_reload_pending)
                        } else {
                            stringResource(id = R.string.game_sync_reload)
                        },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun SyncProgressOverlay(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("game_sync_overlay"),
        shape = RoundedCornerShape(6.dp),
        color = HudSurfaceColor,
        contentColor = HudContentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, HudBorderColor),
    ) {
        Text(
            text = stringResource(id = R.string.game_sync_catching_up),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = HudContentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GameTopBar(
    personalPlayer: GamePlayerUi,
    phase: TurnPhase?,
    round: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("game_top_bar"),
        shape = RoundedCornerShape(0.dp),
        color = HudSurfaceColor,
        contentColor = HudContentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TopBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerAvatar(player = personalPlayer, size = 28.dp)
                Column {
                    Text(
                        text = stringResource(id = R.string.game_personal_player_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = HudContentColor,
                    )
                    Text(
                        text = personalPlayer.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HudContentColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(id = R.string.game_phase_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = HudContentColor,
                )
                Text(
                    text = stringResource(id = phase.labelRes()),
                    modifier = Modifier.testTag("game_phase_value"),
                    style = MaterialTheme.typography.titleSmall,
                    color = HudContentColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(id = R.string.game_round_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = HudContentColor,
                )
                Text(
                    text = stringResource(id = R.string.game_round_value, round),
                    modifier = Modifier.testTag("game_round_value"),
                    style = MaterialTheme.typography.titleSmall,
                    color = HudContentColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CardsSidebar(
    player: GamePlayerUi,
    handCards: List<String>,
    privateHandCards: List<PrivateHandCardUi>,
    selectedTradeInCardIds: Set<CardId>,
    showTradeControls: Boolean,
    canSelectTradeCards: Boolean,
    canTradeInCards: Boolean,
    isTradePending: Boolean,
    onToggleTradeInCard: (CardId) -> Unit,
    onTradeInCards: () -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isVisible) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(0.dp),
            color = HudSurfaceColor,
            contentColor = HudContentColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            PrivateHandPanel(
                playerName = player.name,
                handCards = handCards,
                privateHandCards = privateHandCards,
                selectedTradeInCardIds = selectedTradeInCardIds,
                showTradeControls = showTradeControls,
                canSelectTradeCards = canSelectTradeCards,
                canTradeInCards = canTradeInCards,
                isTradePending = isTradePending,
                onToggleTradeInCard = onToggleTradeInCard,
                onTradeInCards = onTradeInCards,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun PlayerSidebar(
    players: List<GamePlayerUi>,
    activePlayerId: PlayerId?,
    modifier: Modifier = Modifier,
) {
    val playerListScrollState = rememberScrollState()
    val activePlayerIndex = players.indexOfFirst { it.playerId == activePlayerId }

    LaunchedEffect(activePlayerId, playerListScrollState.maxValue) {
        when (activePlayerIndex) {
            0 -> playerListScrollState.animateScrollTo(0)
            players.lastIndex ->
                playerListScrollState.animateScrollTo(
                    playerListScrollState.maxValue,
                )
        }
    }

    Surface(
        modifier =
            modifier
                .testTag("game_player_panel"),
        shape = RoundedCornerShape(0.dp),
        color = HudSurfaceColor,
        contentColor = HudContentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(playerListScrollState)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                players.forEach { player ->
                    PlayerSidebarRow(
                        player = player,
                        isActive = player.playerId == activePlayerId,
                        disableBringIntoView =
                            activePlayerIndex == 0 || activePlayerIndex == players.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PlayerSidebarRow(
    player: GamePlayerUi,
    isActive: Boolean,
    disableBringIntoView: Boolean,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(isActive) {
        if (isActive && !disableBringIntoView) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column {
        Row(
            modifier =
                Modifier
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .fillMaxWidth()
                    .background(
                        if (isActive) HudSurfaceMutedColor else Color.Transparent,
                        RoundedCornerShape(14.dp),
                    )
                    .wrapContentHeight()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ActiveTurnIndicator(isVisible = isActive)
            PlayerAvatar(player = player, size = 28.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = HudContentColor,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                )
                if (player.isHost) {
                    HostIndicator()
                }
            }
        }
    }
}

@Composable
private fun HostIndicator() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = HudContentColor,
        contentColor = HudInverseColor,
    ) {
        Text(
            text = stringResource(id = R.string.game_host_indicator),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = HudInverseColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ActiveTurnIndicator(isVisible: Boolean) {
    if (!isVisible) {
        Spacer(modifier = Modifier.width(8.dp))
        return
    }

    Canvas(
        modifier =
            Modifier
                .size(width = 8.dp, height = 14.dp)
                .testTag("active_player_marker"),
    ) {
        val trianglePath =
            Path().apply {
                moveTo(size.width, size.height / 2f)
                lineTo(0f, 0f)
                lineTo(0f, size.height)
                close()
            }

        drawPath(
            path = trianglePath,
            color = HudBorderColor,
        )
    }
}

@Composable
private fun PlayerAvatar(
    player: GamePlayerUi,
    size: Dp,
) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = HudSurfaceColor,
        border = BorderStroke(2.dp, player.color),
        shadowElevation = 0.dp,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = player.avatarText,
                style = MaterialTheme.typography.labelMedium,
                color = HudContentColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun PrivateHandPanel(
    playerName: String,
    handCards: List<String>,
    privateHandCards: List<PrivateHandCardUi> = emptyList(),
    selectedTradeInCardIds: Set<CardId> = emptySet(),
    showTradeControls: Boolean = false,
    canSelectTradeCards: Boolean = false,
    canTradeInCards: Boolean = false,
    isTradePending: Boolean = false,
    onToggleTradeInCard: (CardId) -> Unit = {},
    onTradeInCards: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val unknownCardLabel = stringResource(id = R.string.game_cards_unknown)
    val typeLabels =
        mapOf(
            CardType.A to stringResource(id = R.string.game_card_type_a),
            CardType.B to stringResource(id = R.string.game_card_type_b),
            CardType.C to stringResource(id = R.string.game_card_type_c),
            CardType.JOKER to stringResource(id = R.string.game_card_type_joker),
        )
    val handCardItems =
        remember(
            handCards,
            privateHandCards,
            selectedTradeInCardIds,
            unknownCardLabel,
            typeLabels,
        ) {
            if (privateHandCards.isNotEmpty()) {
                privateHandCards.map { card ->
                    HandCardItemUi(
                        stableKey = card.cardId.value,
                        label = typeLabels.getValue(card.type),
                        cardId = card.cardId,
                        isSelected = card.cardId in selectedTradeInCardIds,
                    )
                }
            } else {
                buildHandCardItems(
                    handCards = handCards,
                    unknownCardLabel = unknownCardLabel,
                )
            }
        }

    Column(
        modifier = modifier.testTag("game_cards_panel"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(id = R.string.game_cards_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = HudContentColor,
        )
        Text(
            text = playerName,
            style = MaterialTheme.typography.labelMedium,
            color = HudContentColor,
        )

        if (handCardItems.isEmpty()) {
            Text(
                text = stringResource(id = R.string.game_cards_empty),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .testTag("game_cards_list"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = handCardItems,
                    key = HandCardItemUi::stableKey,
                ) { item ->
                    HandCardRow(
                        item = item,
                        selectable = showTradeControls && canSelectTradeCards,
                        onSelected = { cardId -> onToggleTradeInCard(cardId) },
                    )
                }
            }
            if (showTradeControls && privateHandCards.isNotEmpty()) {
                FilledTonalButton(
                    onClick = onTradeInCards,
                    enabled = canTradeInCards,
                    modifier = Modifier.fillMaxWidth().testTag("trade_in_cards_button"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.game_cards_trade_in),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (isTradePending) {
                    Text(
                        text = stringResource(id = R.string.loading),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun HandCardRow(
    item: HandCardItemUi,
    selectable: Boolean,
    onSelected: (CardId) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("game_hand_card_${item.stableKey}")
                .clickable(enabled = selectable && item.cardId != null) {
                    item.cardId?.let(onSelected)
                },
        shape = RoundedCornerShape(6.dp),
        color = if (item.isSelected) Color(0xFFD7EEE9) else HudSurfaceMutedColor,
        contentColor = HudContentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, HudBorderColor),
    ) {
        Text(
            text = item.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = HudContentColor,
        )
    }
}

internal data class HandCardItemUi(
    val stableKey: String,
    val label: String,
    val cardId: CardId? = null,
    val isSelected: Boolean = false,
)

/**
 * Erzeugt Listeneinträge mit stabilen Keys aus einer älteren privaten Handdarstellung.
 *
 * Aktuelle Responses liefern [PrivateHandCardUi] mit stabilen IDs, damit
 * Karten für einen Trade-in auswählbar sind. Die Stringliste bleibt als
 * kompatibler Lesepfad für ältere Snapshots erhalten. Weil in diesem Pfad
 * keine Karten-ID vorhanden ist, nutzt der Key Label plus Duplikatnummer.
 */
internal fun buildHandCardItems(
    handCards: List<String>,
    unknownCardLabel: String,
): List<HandCardItemUi> {
    val occurrencesByLabel = mutableMapOf<String, Int>()

    return handCards.map { card ->
        val label = card.trim().ifBlank { unknownCardLabel }
        val occurrenceIndex = occurrencesByLabel.getOrDefault(label, 0)
        occurrencesByLabel[label] = occurrenceIndex + 1

        HandCardItemUi(
            stableKey = "$label#$occurrenceIndex",
            label = label,
        )
    }
}

/**
 * Zeigt die Platzierungssteuerung für ein bereits ausgewähltes eigenes Gebiet.
 *
 * Diese Composable wird ausschließlich bei einem positiven [remainingAmount]
 * und einem konkreten [selectedRegionId] erzeugt. Ein leerer Restpool erscheint
 * daher nicht als Popup; das Abschließen erfolgt über die untere Aktionsleiste.
 */
@Composable
private fun ReinforcementPanel(
    reinforcementState: ReinforcementUiState,
    remainingAmount: Int,
    placementAmount: Int,
    selectedRegionId: String,
    canAdjust: Boolean,
    canPlace: Boolean,
    onDismiss: () -> Unit,
    onAdjustPlacementAmount: (Int) -> Unit,
    onPlace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .widthIn(max = 560.dp)
                .testTag("reinforcement_panel"),
        shape = RoundedCornerShape(6.dp),
        color = HudSurfaceColor,
        contentColor = HudContentColor,
        border = BorderStroke(1.dp, HudBorderColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        stringResource(
                            id = R.string.game_reinforcements_remaining,
                            remainingAmount.toString(),
                        ),
                    modifier = Modifier.testTag("reinforcement_remaining"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (reinforcementState.isBonusBreakdownKnown) {
                    Text(
                        text =
                            stringResource(
                                id = R.string.game_reinforcements_bonus,
                                reinforcementState.territoryBonus,
                                reinforcementState.continentBonus,
                                reinforcementState.cardBonus,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                val closePanelDescription =
                    stringResource(id = R.string.game_reinforcements_close)
                BlockActionButton(
                    label = "X",
                    onClick = onDismiss,
                    selected = false,
                    enabled = true,
                    modifier =
                        Modifier
                            .size(34.dp)
                            .semantics {
                                contentDescription = closePanelDescription
                            }
                            .testTag("close_reinforcement_panel"),
                )
            }
            Text(
                text = stringResource(id = R.string.game_reinforcements_target, selectedRegionId),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BlockActionButton(
                    label = "-",
                    onClick = { onAdjustPlacementAmount(-1) },
                    selected = false,
                    enabled = canAdjust && placementAmount > 1,
                    modifier = Modifier.size(42.dp).testTag("reinforcement_decrease"),
                )
                Text(
                    text = placementAmount.toString(),
                    modifier = Modifier.width(32.dp).testTag("reinforcement_amount"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                BlockActionButton(
                    label = "+",
                    onClick = { onAdjustPlacementAmount(1) },
                    selected = false,
                    enabled =
                        canAdjust &&
                            placementAmount < remainingAmount,
                    modifier = Modifier.size(42.dp).testTag("reinforcement_increase"),
                )
                BlockActionButton(
                    label = stringResource(id = R.string.game_reinforcements_place),
                    onClick = onPlace,
                    selected = true,
                    enabled = canPlace,
                    modifier = Modifier.testTag("place_reinforcements_button"),
                )
            }
        }
    }
}

/**
 * Rendert die ständig sichtbare Aktionsleiste am unteren Rand.
 *
 * [onEndPhase] ist bereits vom aufrufenden Screen auf den fachlich korrekten
 * Request abgebildet: `TurnAdvance` außerhalb von Verstärkungen und
 * `ConfirmReinforcementsDone` nach vollständigem Verbrauch des Restpools.
 */
@Composable
private fun BottomActionClusters(
    currentPhase: TurnPhase?,
    canUseLocalInput: Boolean,
    canEndPhase: Boolean,
    cardsVisible: Boolean,
    onToggleCards: () -> Unit,
    onEndPhase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(0.dp),
        color = HudSurfaceColor,
        contentColor = HudContentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(BottomBarHeight)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BlockActionButton(
                label =
                    if (cardsVisible) {
                        stringResource(id = R.string.game_cards_hide)
                    } else {
                        stringResource(id = R.string.game_cards_button)
                    },
                onClick = onToggleCards,
                selected = false,
                enabled = canUseLocalInput,
                modifier = Modifier.width(CardsSidebarWidth - 20.dp),
            )

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhaseButton(
                    label = stringResource(id = R.string.game_action_reinforce),
                    selected = currentPhase == TurnPhase.REINFORCEMENTS,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                )
                PhaseButton(
                    label = stringResource(id = R.string.game_action_attack),
                    selected = currentPhase == TurnPhase.ATTACK,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                )
                PhaseButton(
                    label = stringResource(id = R.string.game_action_move),
                    selected = currentPhase == TurnPhase.FORTIFY,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.width(172.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BlockActionButton(
                    label = stringResource(id = R.string.game_end_round_button),
                    onClick = onEndPhase,
                    selected = true,
                    enabled = canEndPhase,
                    modifier = Modifier.fillMaxWidth().testTag("end_round_button"),
                )
            }
        }
    }
}

@Composable
private fun PhaseButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    BlockActionButton(
        label = label,
        onClick = {},
        selected = selected,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
private fun BlockActionButton(
    label: String,
    onClick: () -> Unit,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            contentPadding = contentPadding,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = HudContentColor,
                    contentColor = HudInverseColor,
                ),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        return
    }

    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = contentPadding,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = HudSurfaceMutedColor,
                contentColor = HudContentColor,
            ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun fallbackPlayer(): GamePlayerUi =
    GamePlayerUi(
        playerId = PlayerId(1),
        name = FALLBACK_PLAYER_NAME,
        avatarText = "?",
        color = Color(0xFF8F8F8F),
    )

private fun TurnPhase?.labelRes(): Int =
    when (this) {
        TurnPhase.REINFORCEMENTS -> R.string.game_action_reinforce
        TurnPhase.ATTACK -> R.string.game_action_attack
        TurnPhase.FORTIFY -> R.string.game_action_move
        TurnPhase.DRAW_CARD -> R.string.game_action_draw_card
        null -> R.string.game_action_waiting
    }

private const val FALLBACK_PLAYER_NAME = "Spieler"
