package at.aau.pulverfass.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.unit.sp
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.audio.BackgroundMusicManager
import at.aau.pulverfass.app.game.AttackResultUiState
import at.aau.pulverfass.app.game.AttackUiState
import at.aau.pulverfass.app.game.GameMapTerritoryMapper
import at.aau.pulverfass.app.game.GamePlayerUi
import at.aau.pulverfass.app.game.GameUiState
import at.aau.pulverfass.app.game.MIN_ATTACK_TROOPS
import at.aau.pulverfass.app.game.PrivateHandCardUi
import at.aau.pulverfass.app.game.ReinforcementUiState
import at.aau.pulverfass.app.game.lobbyPlayersToGamePlayers
import at.aau.pulverfass.app.game.minimumOccupyingTroopsForAttack
import at.aau.pulverfass.app.lobby.LobbyCommandKey
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.ui.components.MainButton
import at.aau.pulverfass.app.ui.map.InteractiveGameMap
import at.aau.pulverfass.app.ui.map.InteractiveGameMapOptions
import at.aau.pulverfass.app.ui.map.PulverfassMapDefaults
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.app.ui.theme.PulverfassFonts
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
fun GameScreen(
    controller: LobbyController,
    musicManager: BackgroundMusicManager? = null,
    onNavigateToMain: () -> Unit = {},
) {
    val lobbyState by controller.state.collectAsState()
    val players =
        remember(lobbyState.players, lobbyState.ownPlayerId, lobbyState.playerColor) {
            lobbyPlayersToGamePlayers(
                lobbyState.players,
                lobbyState.ownPlayerId,
                lobbyState.playerColor,
            )
        }
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
                onAdjustAttackTroops = controller::adjustAttackTroops,
                onAdjustMoveAfterCapture = controller::adjustMoveAfterCapture,
                onAttack = controller::attack,
                onConfirmAttackDone = controller::confirmAttackDone,
                onRefreshGameState = controller::refreshGameState,
            ),
        musicManager = musicManager,
        onNavigateToMain = onNavigateToMain,
        onReconnect = controller::connect,
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
    val onAdjustAttackTroops: (Int) -> Unit = {},
    val onAdjustMoveAfterCapture: (Int) -> Unit = {},
    val onAttack: () -> Unit = {},
    val onConfirmAttackDone: () -> Unit = {},
    val onRefreshGameState: () -> Unit,
)

/**
 * Kompakter Anzeige- und Interaktionszustand der privaten Kartenhand.
 *
 * Die Gruppierung hält die Panel-Schnittstelle stabil, wenn weitere
 * kartenspezifische Eigenschaften aus dem privaten Snapshot hinzukommen.
 */
internal data class PrivateHandPanelState(
    val playerName: String,
    val handCards: List<String>,
    val privateHandCards: List<PrivateHandCardUi> = emptyList(),
    val selectedTradeInCardIds: Set<CardId> = emptySet(),
    val showTradeControls: Boolean = false,
    val canSelectTradeCards: Boolean = false,
    val canTradeInCards: Boolean = false,
    val isTradePending: Boolean = false,
)

internal data class PrivateHandPanelActions(
    val onToggleTradeInCard: (CardId) -> Unit = {},
    val onTradeInCards: () -> Unit = {},
)

private data class ReinforcementPanelState(
    val reinforcementState: ReinforcementUiState,
    val remainingAmount: Int,
    val placementAmount: Int,
    val selectedRegionId: String,
    val canAdjust: Boolean,
    val canPlace: Boolean,
)

private data class ReinforcementPanelActions(
    val onDismiss: () -> Unit,
    val onAdjustPlacementAmount: (Int) -> Unit,
    val onPlace: () -> Unit,
)

private data class ReinforcementPanelHostState(
    val selectedRegionId: String?,
    val uiState: GameUiState,
    val remainingAmount: Int,
    val canManageReinforcements: Boolean,
    val isCommandPending: Boolean,
    val localPlayerId: PlayerId?,
    val isConnected: Boolean,
)

private data class ReinforcementPanelHostActions(
    val onRegionSelected: (String) -> Unit,
    val onAdjustPlacementAmount: (Int) -> Unit,
    val onPlace: () -> Unit,
)

private data class AttackPanelState(
    val attackState: AttackUiState,
    val fromRegionId: String,
    val toRegionId: String,
    val maximumAttackTroops: Int,
    val canAdjust: Boolean,
    val canAttack: Boolean,
)

private data class AttackPanelActions(
    val onDismiss: () -> Unit,
    val onAdjustAttackTroops: (Int) -> Unit,
    val onAdjustMoveAfterCapture: (Int) -> Unit,
    val onAttack: () -> Unit,
)

private data class AttackPanelHostState(
    val selection: Pair<String, String>?,
    val uiState: GameUiState,
    val canManageAttacks: Boolean,
    val isCommandPending: Boolean,
    val localPlayerId: PlayerId?,
    val isConnected: Boolean,
)

private data class AttackPanelHostActions(
    val onRegionSelected: (String) -> Unit,
    val onAdjustAttackTroops: (Int) -> Unit,
    val onAdjustMoveAfterCapture: (Int) -> Unit,
    val onAttack: () -> Unit,
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
    musicManager: BackgroundMusicManager? = null,
    onNavigateToMain: () -> Unit = {},
    onReconnect: () -> Unit = {},
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
    val onAdjustAttackTroops = actions.onAdjustAttackTroops
    val onAdjustMoveAfterCapture = actions.onAdjustMoveAfterCapture
    val onAttack = actions.onAttack
    val onConfirmAttackDone = actions.onConfirmAttackDone
    val onRefreshGameState = actions.onRefreshGameState
    val personalPlayer = players.firstOrNull { it.playerId == localPlayerId } ?: fallbackPlayer()
    val canUseGameActions = uiState.canUseGameActions(localPlayerId, isConnected)

    val isRefreshPending = pendingCommandKeys.hasRefreshRequest()
    val isReinforcementCommandPending = pendingCommandKeys.hasReinforcementRequest()
    val isAttackCommandPending = pendingCommandKeys.hasAttackRequest()
    val canManageReinforcements = uiState.canManageReinforcements(localPlayerId, isConnected)
    val canManageAttacks = uiState.canManageAttacks(localPlayerId, isConnected)
    val remainingReinforcementAmount = uiState.reinforcementState.pendingAmount ?: 0

    val reinforcementPanelRegionId =
        visibleReinforcementTarget(uiState, canManageReinforcements, remainingReinforcementAmount)
    val attackPanelSelection = visibleAttackSelection(uiState, canManageAttacks)
    val canEndCurrentPhase =
        canEndCurrentPhase(
            uiState = uiState,
            localPlayerId = localPlayerId,
            isConnected = isConnected,
            isReinforcementCommandPending = isReinforcementCommandPending,
            isAttackCommandPending = isAttackCommandPending,
            pendingCommandKeys = pendingCommandKeys,
        )
    val onEndCurrentPhase =
        endCurrentPhaseAction(
            uiState,
            onConfirmReinforcementsDone,
            onConfirmAttackDone,
            onAdvanceTurn,
        )
    val showCatchUpFeedback = rememberDelayedCatchUpFeedback(uiState.isCatchingUp)

    var showCountdown by remember { mutableStateOf(true) }
    var countdownValue by remember { mutableStateOf(3) }
    LaunchedEffect(Unit) {
        for (i in 3 downTo 1) {
            countdownValue = i
            musicManager?.playSfx(R.raw.sfx_ingame)
            delay(1000L)
        }
        delay(800L)
        showCountdown = false
    }
    val statusMessage =
        gameStatusMessage(uiState, isConnected, showCatchUpFeedback)

    val reconnectingText = stringResource(id = R.string.game_sync_reconnecting)
    val desyncedText = stringResource(id = R.string.game_sync_desynced)
    val isDisconnectState = !isConnected || uiState.isDesynced
    val disconnectMessage =
        when {
            !isConnected -> ""
            uiState.isDesynced -> uiState.lastSyncError ?: desyncedText
            else -> ""
        }

    var showOptionsOverlay by remember { mutableStateOf(false) }
    var isMusicEnabled by remember { mutableStateOf(musicManager?.isMusicMuted?.not() ?: true) }
    var isSfxEnabled by remember { mutableStateOf(musicManager?.isSfxMuted?.not() ?: true) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("game_screen_root"),
    ) {
        // Game content group — blurred when disconnected so the sharp overlay reads clearly
        Box(
            modifier =
                if (isDisconnectState) {
                    Modifier.fillMaxSize().blur(20.dp)
                } else {
                    Modifier.fillMaxSize()
                },
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

            CatchUpProgressOverlay(
                isCatchingUp = uiState.isCatchingUp,
                showFeedback = showCatchUpFeedback,
                modifier = Modifier.align(Alignment.Center),
            )

            OptionalGameStatusBanner(
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

            CardsSidebar(
                state =
                    privateHandPanelState(
                        player = personalPlayer,
                        uiState = uiState,
                        localPlayerId = localPlayerId,
                        isConnected = isConnected,
                        isReinforcementCommandPending = isReinforcementCommandPending,
                        pendingCommandKeys = pendingCommandKeys,
                    ),
                actions =
                    PrivateHandPanelActions(
                        onToggleTradeInCard = onToggleTradeInCard,
                        onTradeInCards = onTradeInCards,
                    ),
                isVisible = uiState.cardsVisible,
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(top = TopBarHeight, bottom = BottomBarHeight)
                        .requiredWidth(CardsSidebarWidth)
                        .fillMaxHeight(),
                musicManager = musicManager,
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

            ReinforcementPanelHost(
                state =
                    ReinforcementPanelHostState(
                        selectedRegionId = reinforcementPanelRegionId,
                        uiState = uiState,
                        remainingAmount = remainingReinforcementAmount,
                        canManageReinforcements = canManageReinforcements,
                        isCommandPending = isReinforcementCommandPending,
                        localPlayerId = localPlayerId,
                        isConnected = isConnected,
                    ),
                actions =
                    ReinforcementPanelHostActions(
                        onRegionSelected = onRegionSelected,
                        onAdjustPlacementAmount = onAdjustReinforcementPlacementAmount,
                        onPlace = onPlaceReinforcements,
                    ),
            )

            AttackPanelHost(
                state =
                    AttackPanelHostState(
                        selection = attackPanelSelection,
                        uiState = uiState,
                        canManageAttacks = canManageAttacks,
                        isCommandPending = isAttackCommandPending,
                        localPlayerId = localPlayerId,
                        isConnected = isConnected,
                    ),
                actions =
                    AttackPanelHostActions(
                        onRegionSelected = onRegionSelected,
                        onAdjustAttackTroops = onAdjustAttackTroops,
                        onAdjustMoveAfterCapture = onAdjustMoveAfterCapture,
                        onAttack = onAttack,
                    ),
            )

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
                musicManager = musicManager,
            )

            FilledTonalButton(
                onClick = { showOptionsOverlay = true },
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = TopBarHeight + 8.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = PulverfassColors.SurfaceDark.copy(alpha = 0.85f),
                        contentColor = PulverfassColors.TextOnDark,
                    ),
            ) {
                Text(
                    text = "⚙ OPTIONEN",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                )
            }

            if (showOptionsOverlay) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(PulverfassColors.SurfaceVoid.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth(0.45f)
                                .background(
                                    PulverfassColors.SurfaceDark.copy(alpha = 0.75f),
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 32.dp, vertical = 24.dp),
                    ) {
                        Text(
                            text = "OPTIONEN",
                            fontFamily = PulverfassFonts.CinzelDecorative,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = PulverfassColors.GoldBright,
                            letterSpacing = 3.sp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        InGameAudioToggleRow(
                            label = "MUSIK",
                            isEnabled = isMusicEnabled,
                            onToggle = { enabled ->
                                isMusicEnabled = enabled
                                musicManager?.setMusicMuted(!enabled)
                            },
                        )
                        InGameAudioToggleRow(
                            label = "SOUND-EFFEKTE",
                            isEnabled = isSfxEnabled,
                            onToggle = { enabled ->
                                isSfxEnabled = enabled
                                musicManager?.setSfxMuted(!enabled)
                            },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        MainButton(
                            text = "ZURÜCK ZUM HAUPTMENÜ",
                            onClick = onNavigateToMain,
                        )
                        MainButton(
                            text = "SCHLIESSEN",
                            onClick = { showOptionsOverlay = false },
                        )
                    }
                }
            }

            if (showCountdown) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(PulverfassColors.SurfaceVoid.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "MACH DICH BEREIT!",
                            fontFamily = PulverfassFonts.CinzelDecorative,
                            fontWeight = FontWeight.Bold,
                            fontSize = 48.sp,
                            color = PulverfassColors.GoldBright,
                        )
                        Text(
                            text = "Das Spiel beginnt gleich...",
                            fontSize = 20.sp,
                            color = PulverfassColors.TextOnDark,
                        )
                        Text(
                            text = countdownValue.toString(),
                            fontFamily = PulverfassFonts.CinzelDecorative,
                            fontWeight = FontWeight.Bold,
                            fontSize = 96.sp,
                            color = PulverfassColors.GoldBright,
                        )
                    }
                }
            }
        } // end blurred game content group

        if (isDisconnectState) {
            DisconnectOverlay(
                message = disconnectMessage,
                onReconnect = onReconnect,
                onNavigateToMain = onNavigateToMain,
            )
        }
    }
}

@Composable
private fun rememberDelayedCatchUpFeedback(isCatchingUp: Boolean): Boolean {
    var showCatchUpFeedback by remember { mutableStateOf(false) }
    LaunchedEffect(isCatchingUp) {
        showCatchUpFeedback = false
        if (isCatchingUp) {
            delay(SYNC_FEEDBACK_DELAY_MILLIS)
            showCatchUpFeedback = true
        }
    }
    return showCatchUpFeedback
}

@Composable
private fun CatchUpProgressOverlay(
    isCatchingUp: Boolean,
    showFeedback: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isCatchingUp && showFeedback) {
        SyncProgressOverlay(modifier = modifier)
    }
}

private fun privateHandPanelState(
    player: GamePlayerUi,
    uiState: GameUiState,
    localPlayerId: PlayerId?,
    isConnected: Boolean,
    isReinforcementCommandPending: Boolean,
    pendingCommandKeys: Set<LobbyCommandKey>,
): PrivateHandPanelState =
    PrivateHandPanelState(
        playerName = player.name,
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
    )

@Composable
private fun BoxScope.ReinforcementPanelHost(
    state: ReinforcementPanelHostState,
    actions: ReinforcementPanelHostActions,
) {
    val regionId = state.selectedRegionId ?: return
    ReinforcementPanel(
        state =
            ReinforcementPanelState(
                reinforcementState = state.uiState.reinforcementState,
                remainingAmount = state.remainingAmount,
                placementAmount = state.uiState.reinforcementPlacementAmount,
                selectedRegionId = regionId,
                canAdjust = state.canManageReinforcements && !state.isCommandPending,
                canPlace =
                    state.uiState.canPlaceReinforcements(
                        state.localPlayerId,
                        state.isConnected,
                    ) && !state.isCommandPending,
            ),
        actions =
            ReinforcementPanelActions(
                onDismiss = { actions.onRegionSelected(regionId) },
                onAdjustPlacementAmount = actions.onAdjustPlacementAmount,
                onPlace = actions.onPlace,
            ),
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BottomBarHeight + 8.dp),
    )
}

@Composable
private fun BoxScope.AttackPanelHost(
    state: AttackPanelHostState,
    actions: AttackPanelHostActions,
) {
    if (state.selection == null) {
        AttackResultHost(result = state.uiState.attackState.latestResult)
        return
    }

    val (fromRegionId, toRegionId) = state.selection
    AttackPanel(
        state =
            AttackPanelState(
                attackState = state.uiState.attackState,
                fromRegionId = fromRegionId,
                toRegionId = toRegionId,
                maximumAttackTroops = maximumAttackTroops(state.uiState, fromRegionId),
                canAdjust = state.canManageAttacks && !state.isCommandPending,
                canAttack =
                    state.uiState.canSubmitAttack(state.localPlayerId, state.isConnected) &&
                        !state.isCommandPending,
            ),
        actions =
            AttackPanelActions(
                onDismiss = { actions.onRegionSelected(fromRegionId) },
                onAdjustAttackTroops = actions.onAdjustAttackTroops,
                onAdjustMoveAfterCapture = actions.onAdjustMoveAfterCapture,
                onAttack = actions.onAttack,
            ),
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BottomBarHeight + 8.dp),
    )
}

@Composable
private fun BoxScope.AttackResultHost(result: AttackResultUiState?) {
    if (result == null) {
        return
    }
    AttackResultPanel(
        result = result,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BottomBarHeight + 8.dp),
    )
}

private fun maximumAttackTroops(
    uiState: GameUiState,
    fromRegionId: String,
): Int =
    uiState.territoryStates[
        GameMapTerritoryMapper.toTerritoryId(fromRegionId),
    ]?.troopCount?.minus(1) ?: uiState.attackState.attackTroops

/**
 * Rendert den Statusbereich nur dann, wenn eine synchronisationsrelevante Meldung vorliegt.
 */
@Composable
private fun OptionalGameStatusBanner(
    message: String?,
    canRefresh: Boolean,
    isRefreshPending: Boolean,
    onRefreshGameState: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message != null) {
        GameStatusBanner(
            message = message,
            canRefresh = canRefresh,
            isRefreshPending = isRefreshPending,
            onRefreshGameState = onRefreshGameState,
            modifier = modifier,
        )
    }
}

/**
 * Liefert `true`, solange ein Request eines vollständigen Refresh-Zyklus aussteht.
 */
private fun Set<LobbyCommandKey>.hasRefreshRequest(): Boolean =
    any {
        it == LobbyCommandKey.MAP_GET ||
            it == LobbyCommandKey.TURN_STATE_GET ||
            it == LobbyCommandKey.CATCH_UP
    }

private fun Set<LobbyCommandKey>.hasReinforcementRequest(): Boolean =
    any {
        it == LobbyCommandKey.PLACE_REINFORCEMENTS ||
            it == LobbyCommandKey.CONFIRM_REINFORCEMENTS_DONE ||
            it == LobbyCommandKey.TRADE_IN_CARDS
    }

private fun Set<LobbyCommandKey>.hasAttackRequest(): Boolean =
    any {
        it == LobbyCommandKey.ATTACK ||
            it == LobbyCommandKey.CONFIRM_ATTACK_DONE
    }

/**
 * Das Platzierungs-Panel erscheint nur mit einem ausgewählten Ziel und Restpool.
 * Andernfalls bleibt die Karte frei; ein leerer Pool wird in der Aktionsleiste beendet.
 */
private fun visibleReinforcementTarget(
    uiState: GameUiState,
    canManageReinforcements: Boolean,
    remainingAmount: Int,
): String? =
    if (
        uiState.turnPhase == TurnPhase.REINFORCEMENTS &&
        canManageReinforcements &&
        remainingAmount > 0
    ) {
        uiState.selectedRegionId
    } else {
        null
    }

/**
 * Ein Angriffs-Panel benötigt eine bereits fachlich validierte Quelle und ein Ziel.
 *
 * Bis dahin bleibt die Karte vollständig frei, damit die beiden Gebiete direkt
 * über die Hitmap gewählt werden können.
 */
private fun visibleAttackSelection(
    uiState: GameUiState,
    canManageAttacks: Boolean,
): Pair<String, String>? {
    if (uiState.turnPhase != TurnPhase.ATTACK || !canManageAttacks) {
        return null
    }
    val fromRegionId = uiState.selectionFromRegionId ?: return null
    val toRegionId = uiState.selectionToRegionId ?: return null
    return fromRegionId to toRegionId
}

private fun canEndCurrentPhase(
    uiState: GameUiState,
    localPlayerId: PlayerId?,
    isConnected: Boolean,
    isReinforcementCommandPending: Boolean,
    isAttackCommandPending: Boolean,
    pendingCommandKeys: Set<LobbyCommandKey>,
): Boolean =
    when (uiState.turnPhase) {
        TurnPhase.REINFORCEMENTS ->
            uiState.canConfirmReinforcementsDone(localPlayerId, isConnected) &&
                !isReinforcementCommandPending
        TurnPhase.ATTACK ->
            uiState.canConfirmAttackDone(localPlayerId, isConnected) &&
                !isAttackCommandPending
        else ->
            uiState.canRequestTurnAdvance(localPlayerId, isConnected) &&
                !pendingCommandKeys.contains(LobbyCommandKey.TURN_ADVANCE)
    }

private fun endCurrentPhaseAction(
    uiState: GameUiState,
    onConfirmReinforcementsDone: () -> Unit,
    onConfirmAttackDone: () -> Unit,
    onAdvanceTurn: () -> Unit,
): () -> Unit =
    when (uiState.turnPhase) {
        TurnPhase.REINFORCEMENTS -> onConfirmReinforcementsDone
        TurnPhase.ATTACK -> onConfirmAttackDone
        else -> onAdvanceTurn
    }

/**
 * Priorisiert Verbindungs- und Synchronisationszustände vor Bedienhinweisen.
 * Sehr kurze Catch-ups bleiben unsichtbar, damit reguläre Antworten nicht flackern.
 */
@Composable
private fun gameStatusMessage(
    uiState: GameUiState,
    isConnected: Boolean,
    showCatchUpFeedback: Boolean,
): String? =
    when {
        !isConnected -> stringResource(id = R.string.game_sync_reconnecting)
        uiState.isDesynced -> null
        uiState.isCatchingUp && showCatchUpFeedback ->
            stringResource(id = R.string.game_sync_catching_up)
        uiState.isCatchingUp -> null
        uiState.lastSyncError != null -> uiState.lastSyncError
        uiState.selectionMessage != null -> uiState.selectionMessage
        else -> null
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
                    .padding(horizontal = 20.dp, vertical = 6.dp),
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
        color = PulverfassColors.SurfaceDark.copy(alpha = 0.92f),
        contentColor = PulverfassColors.TextOnDark,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, PulverfassColors.GoldDark),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TopBarHeight)
                    .displayCutoutPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerAvatar(player = personalPlayer, size = 28.dp)
                Column {
                    Text(
                        text = stringResource(id = R.string.game_personal_player_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = PulverfassColors.TextOnDark,
                    )
                    Text(
                        text = personalPlayer.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PulverfassColors.GoldBright,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(id = R.string.game_phase_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = PulverfassColors.TextOnDark,
                )
                Text(
                    text = stringResource(id = phase.labelRes()),
                    modifier = Modifier.testTag("game_phase_value"),
                    style = MaterialTheme.typography.titleSmall,
                    color = PulverfassColors.GoldBright,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(id = R.string.game_round_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = PulverfassColors.TextOnDark,
                )
                Text(
                    text = stringResource(id = R.string.game_round_value, round),
                    modifier = Modifier.testTag("game_round_value"),
                    style = MaterialTheme.typography.titleSmall,
                    color = PulverfassColors.GoldBright,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CardsSidebar(
    state: PrivateHandPanelState,
    actions: PrivateHandPanelActions,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    musicManager: BackgroundMusicManager? = null,
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
                state = state,
                actions = actions,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                musicManager = musicManager,
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
    state: PrivateHandPanelState,
    actions: PrivateHandPanelActions = PrivateHandPanelActions(),
    modifier: Modifier = Modifier,
    musicManager: BackgroundMusicManager? = null,
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
            state.handCards,
            state.privateHandCards,
            state.selectedTradeInCardIds,
            unknownCardLabel,
            typeLabels,
        ) {
            if (state.privateHandCards.isNotEmpty()) {
                state.privateHandCards.map { card ->
                    HandCardItemUi(
                        stableKey = card.cardId.value,
                        label = typeLabels.getValue(card.type),
                        cardId = card.cardId,
                        isSelected = card.cardId in state.selectedTradeInCardIds,
                    )
                }
            } else {
                buildHandCardItems(
                    handCards = state.handCards,
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
            text = state.playerName,
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
                        selectable = state.showTradeControls && state.canSelectTradeCards,
                        onSelected = { cardId -> actions.onToggleTradeInCard(cardId) },
                    )
                }
            }
            if (state.showTradeControls && state.privateHandCards.isNotEmpty()) {
                FilledTonalButton(
                    onClick = {
                        musicManager?.playSfx(R.raw.sfx_karten)
                        actions.onTradeInCards()
                    },
                    enabled = state.canTradeInCards,
                    modifier = Modifier.fillMaxWidth().testTag("trade_in_cards_button"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.game_cards_trade_in),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (state.isTradePending) {
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
 * Diese Composable wird ausschließlich bei einem positiven Restpool in [state]
 * und einem konkreten ausgewählten Gebiet erzeugt. Ein leerer Restpool erscheint
 * daher nicht als Popup; das Abschließen erfolgt über die untere Aktionsleiste.
 */
@Composable
private fun ReinforcementPanel(
    state: ReinforcementPanelState,
    actions: ReinforcementPanelActions,
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
                            state.remainingAmount.toString(),
                        ),
                    modifier = Modifier.testTag("reinforcement_remaining"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (state.reinforcementState.isBonusBreakdownKnown) {
                    Text(
                        text =
                            stringResource(
                                id = R.string.game_reinforcements_bonus,
                                state.reinforcementState.territoryBonus,
                                state.reinforcementState.continentBonus,
                                state.reinforcementState.cardBonus,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                val closePanelDescription =
                    stringResource(id = R.string.game_reinforcements_close)
                BlockActionButton(
                    label = "X",
                    onClick = actions.onDismiss,
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
                text =
                    stringResource(
                        id = R.string.game_reinforcements_target,
                        state.selectedRegionId,
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TroopAmountSliderRow(
                    label = stringResource(id = R.string.game_reinforcements_amount),
                    amount = state.placementAmount,
                    minAmount = 1,
                    maxAmount = state.remainingAmount,
                    canAdjust = state.canAdjust,
                    onAdjust = actions.onAdjustPlacementAmount,
                    tagPrefix = "reinforcement",
                )
                BlockActionButton(
                    label = stringResource(id = R.string.game_reinforcements_place),
                    onClick = actions.onPlace,
                    selected = true,
                    enabled = state.canPlace,
                    modifier = Modifier.fillMaxWidth().testTag("place_reinforcements_button"),
                )
            }
        }
    }
}

/**
 * Steuerung einer einzelnen Angriffsabsicht zwischen zwei gültig gewählten Gebieten.
 *
 * Das Panel blockiert die Karte nicht: Quelle und Ziel können durch erneutes
 * Tippen gewechselt werden. Der Server löst Würfel und Eroberung erst nach dem
 * Request auf; im Panel stehen ausschließlich die erlaubten Eingaben.
 */
@Composable
private fun AttackPanel(
    state: AttackPanelState,
    actions: AttackPanelActions,
    modifier: Modifier = Modifier,
) {
    val minimumMoveAfterCapture =
        minimumOccupyingTroopsForAttack(state.attackState.attackTroops)
    Surface(
        modifier =
            modifier
                .widthIn(max = 600.dp)
                .testTag("attack_panel"),
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
                            id = R.string.game_attack_route,
                            state.fromRegionId,
                            state.toRegionId,
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                val closePanelDescription = stringResource(id = R.string.game_attack_close)
                BlockActionButton(
                    label = "X",
                    onClick = actions.onDismiss,
                    selected = false,
                    enabled = true,
                    modifier =
                        Modifier
                            .size(34.dp)
                            .semantics { contentDescription = closePanelDescription }
                            .testTag("close_attack_panel"),
                )
            }
            TroopAmountSliderRow(
                label = stringResource(id = R.string.game_attack_troops),
                amount = state.attackState.attackTroops,
                minAmount = MIN_ATTACK_TROOPS,
                maxAmount = state.maximumAttackTroops,
                canAdjust = state.canAdjust,
                onAdjust = actions.onAdjustAttackTroops,
                tagPrefix = "attack_troops",
            )
            TroopAmountSliderRow(
                label = stringResource(id = R.string.game_attack_occupy),
                amount = state.attackState.moveAfterCapture,
                minAmount = minimumMoveAfterCapture,
                maxAmount = state.attackState.attackTroops,
                canAdjust = state.canAdjust,
                onAdjust = actions.onAdjustMoveAfterCapture,
                tagPrefix = "attack_move",
            )
            BlockActionButton(
                label = stringResource(id = R.string.game_attack_submit),
                onClick = actions.onAttack,
                selected = true,
                enabled = state.canAttack,
                modifier = Modifier.fillMaxWidth().testTag("attack_submit_button"),
            )
        }
    }
}

@Composable
private fun TroopAmountSliderRow(
    label: String,
    amount: Int,
    minAmount: Int,
    maxAmount: Int,
    canAdjust: Boolean,
    onAdjust: (Int) -> Unit,
    tagPrefix: String,
) {
    val safeMinAmount = minAmount.coerceAtMost(maxAmount)
    val safeMaxAmount = maxAmount.coerceAtLeast(safeMinAmount)
    val sliderMaxAmount =
        if (safeMaxAmount > safeMinAmount) {
            safeMaxAmount
        } else {
            safeMinAmount + 1
        }
    val sliderValue = amount.coerceIn(safeMinAmount, safeMaxAmount).toFloat()

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = amount.toString(),
                modifier = Modifier.width(32.dp).testTag("${tagPrefix}_amount"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { value ->
                val targetAmount = value.roundToInt().coerceIn(safeMinAmount, safeMaxAmount)
                val delta = targetAmount - amount
                if (delta != 0) {
                    onAdjust(delta)
                }
            },
            valueRange = safeMinAmount.toFloat()..sliderMaxAmount.toFloat(),
            steps = (safeMaxAmount - safeMinAmount - 1).coerceAtLeast(0),
            enabled = canAdjust && safeMaxAmount > safeMinAmount,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("${tagPrefix}_slider"),
        )
    }
}

/** Zeigt das letzte vom Server aufgelöste Kampfergebnis ohne lokale Berechnung. */
@Composable
private fun AttackResultPanel(
    result: AttackResultUiState,
    modifier: Modifier = Modifier,
) {
    val fromRegionId =
        GameMapTerritoryMapper.toAndroidRegionId(result.fromTerritoryId)
            ?: result.fromTerritoryId.value
    val toRegionId =
        GameMapTerritoryMapper.toAndroidRegionId(result.toTerritoryId)
            ?: result.toTerritoryId.value
    Surface(
        modifier =
            modifier
                .widthIn(max = 560.dp)
                .testTag("attack_result_panel"),
        shape = RoundedCornerShape(6.dp),
        color = HudSurfaceColor,
        contentColor = HudContentColor,
        border = BorderStroke(1.dp, HudBorderColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text =
                    stringResource(
                        id = R.string.game_attack_result_title,
                        fromRegionId,
                        toRegionId,
                    ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text =
                    stringResource(
                        id = R.string.game_attack_dice,
                        result.attackerRolls.joinToString(", "),
                        result.defenderRolls.joinToString(", "),
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    stringResource(
                        id = R.string.game_attack_losses,
                        result.attackerLosses,
                        result.defenderLosses,
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text =
                    if (result.captured) {
                        stringResource(
                            id = R.string.game_attack_captured,
                            result.occupyingTroopCount ?: 0,
                        )
                    } else {
                        stringResource(id = R.string.game_attack_held, result.defenderRemaining)
                    },
                modifier = Modifier.testTag("attack_result_outcome"),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Rendert die ständig sichtbare Aktionsleiste am unteren Rand.
 *
 * [onEndPhase] ist bereits vom aufrufenden Screen auf den fachlich korrekten
 * Request abgebildet: `ConfirmReinforcementsDone` nach vollständigem Verbrauch
 * des Restpools, `ConfirmAttackDone` in der Angriffsphase und `TurnAdvance`
 * in den übrigen Phasen.
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
    musicManager: BackgroundMusicManager? = null,
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
                    .padding(horizontal = 20.dp, vertical = 8.dp),
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
                musicManager = musicManager,
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
                    musicManager = musicManager,
                    sfxResId = R.raw.sfx_schlacht_att,
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
    musicManager: BackgroundMusicManager? = null,
    sfxResId: Int = R.raw.sfx_ingame,
) {
    val contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    val wrappedOnClick: () -> Unit =
        if (musicManager != null) {
            {
                musicManager.playSfx(sfxResId)
                onClick()
            }
        } else {
            onClick
        }

    if (selected) {
        Button(
            onClick = wrappedOnClick,
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
        onClick = wrappedOnClick,
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

@Composable
private fun InGameAudioToggleRow(
    label: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = PulverfassColors.TextOnDark,
            fontFamily = PulverfassFonts.CinzelDecorative,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = PulverfassColors.GoldBright,
                    checkedTrackColor = PulverfassColors.GoldDark,
                    uncheckedThumbColor = PulverfassColors.TextMuted,
                    uncheckedTrackColor = PulverfassColors.SurfaceDark,
                ),
        )
    }
}

@Composable
private fun DisconnectOverlay(
    message: String,
    onReconnect: () -> Unit,
    onNavigateToMain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .background(Color(0xFFCC0000).copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "⚠ VERBINDUNG UNTERBROCHEN",
                fontFamily = PulverfassFonts.CinzelDecorative,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = PulverfassColors.GoldBright,
            )
            Text(
                text = message,
                fontSize = 16.sp,
                color = PulverfassColors.TextOnDark,
            )
            Spacer(modifier = Modifier.height(24.dp))
            MainButton(
                text = "ERNEUT VERBINDEN",
                onClick = onReconnect,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MainButton(
                text = "ZURÜCK ZUM HAUPTMENÜ",
                onClick = onNavigateToMain,
            )
        }
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
