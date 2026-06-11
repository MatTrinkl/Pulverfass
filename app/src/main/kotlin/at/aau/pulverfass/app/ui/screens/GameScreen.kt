package at.aau.pulverfass.app.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.audio.BackgroundMusicManager
import at.aau.pulverfass.app.game.AttackResultUiState
import at.aau.pulverfass.app.game.AttackUiState
import at.aau.pulverfass.app.game.FortifyUiState
import at.aau.pulverfass.app.game.GameMapTerritoryMapper
import at.aau.pulverfass.app.game.GamePlayerUi
import at.aau.pulverfass.app.game.GameUiState
import at.aau.pulverfass.app.game.MIN_ATTACK_TROOPS
import at.aau.pulverfass.app.game.MIN_FORTIFY_TROOPS
import at.aau.pulverfass.app.game.PrivateHandCardUi
import at.aau.pulverfass.app.game.ReinforcementUiState
import at.aau.pulverfass.app.game.lobbyPlayersToGamePlayers
import at.aau.pulverfass.app.game.minimumOccupyingTroopsForAttack
import at.aau.pulverfass.app.lobby.CharacterDefinition
import at.aau.pulverfass.app.lobby.Characters
import at.aau.pulverfass.app.lobby.LobbyCommandKey
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.ui.components.CharacterCoin
import at.aau.pulverfass.app.ui.components.MainButton
import at.aau.pulverfass.app.ui.components.PulverfassTitleText
import at.aau.pulverfass.app.ui.components.VideoPlayer
import at.aau.pulverfass.app.ui.map.InteractiveGameMap
import at.aau.pulverfass.app.ui.map.InteractiveGameMapOptions
import at.aau.pulverfass.app.ui.map.PulverfassMapDefaults
import at.aau.pulverfass.app.ui.theme.CormorantGaramond
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.app.ui.theme.PulverfassFonts
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import at.aau.pulverfass.shared.message.connection.ConnectionStatus
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * Gemeinsame HUD-Palette für alle Ingame-Flächen (Topbar, Sidebars, Bottombar,
 * Phasen-Panels). Alle Flächen teilen sich denselben dunklen Look mit Goldrahmen,
 * damit das HUD über der Karte als eine zusammenhängende Ebene wirkt.
 */
private val HudSurfaceColor = PulverfassColors.SurfaceDark.copy(alpha = 0.92f)
private val HudSurfaceMutedColor = PulverfassColors.SurfaceVoid.copy(alpha = 0.55f)
private val HudBorderColor = PulverfassColors.GoldDark
private val HudContentColor = PulverfassColors.TextOnDark
private val HudInverseColor = PulverfassColors.SurfaceDark
private val HudAccentColor = PulverfassColors.GoldBright
private val HudActiveHighlightColor = PulverfassColors.GoldDark.copy(alpha = 0.35f)
private val HudSelectedCardColor = PulverfassColors.Gold.copy(alpha = 0.30f)
private val TopBarHeight = 52.dp
private val BottomBarHeight = 54.dp
private val SidebarWidth = 156.dp
private val CardsSidebarWidth = SidebarWidth
private const val SYNC_FEEDBACK_DELAY_MILLIS = 500L
private const val DISCONNECT_FEEDBACK_DELAY_MILLIS = 900L
private const val AUTO_PHASE_NOTICE_DURATION_MILLIS = 2_000L
private const val COUNTDOWN_STEP_MILLIS = 1_000L
private const val COUNTDOWN_ZERO_MILLIS = 450L
private const val CHEAT_LIGHT_BASELINE_LUX = 8f
private const val CHEAT_LIGHT_COVERED_LUX = 2f

/**
 * Einstiegspunkt des Spielbildschirms.
 *
 * @param controller gemeinsamer LobbyController, der Lobby-, Netzwerk- und
 * GameState verwaltet
 * @param musicManager optionaler Audio-Manager für SFX, Musik und Cheat-Sound.
 * @param onNavigateToMain Navigation zurück ins Hauptmenü nach "Spiel verlassen".
 */
@Composable
fun GameScreen(
    controller: LobbyController,
    musicManager: BackgroundMusicManager? = null,
    onNavigateToMain: () -> Unit = {},
) {
    val lobbyState by controller.state.collectAsState()
    val character = lobbyState.characterId?.let { Characters.byId(it) }
    val players =
        remember(lobbyState.players, lobbyState.ownPlayerId, lobbyState.characterId) {
            lobbyPlayersToGamePlayers(
                players = lobbyState.players,
                ownPlayerId = lobbyState.ownPlayerId,
                ownCharacterId = lobbyState.characterId,
            )
        }
    val mapPainter = painterResource(id = R.drawable.map_world)
    val onLeaveGame: () -> Unit = {
        controller.leaveLobby()
        onNavigateToMain()
    }

    GameScreenContent(
        contentState =
            GameScreenContentState(
                players = players,
                localPlayerId = lobbyState.ownPlayerId,
                uiState = lobbyState.gameState,
                isConnected = lobbyState.isConnected,
                pendingCommandKeys = lobbyState.pendingCommandKeys,
                mapPainter = mapPainter,
                character = character,
                playerName = lobbyState.playerName,
                autoPhaseNoticeText = lobbyState.autoPhaseNoticeText,
            ),
        actions =
            GameScreenActions(
                onRegionSelected = controller::selectGameRegion,
                onToggleCards = controller::toggleCards,
                onAdvanceTurn = controller::advanceTurn,
                onAdjustReinforcementPlacementAmount =
                    controller::adjustReinforcementPlacementAmount,
                onPlaceReinforcements = controller::placeReinforcements,
                onClaimCheatReinforcementBonus = controller::claimCheatReinforcementBonus,
                onConfirmReinforcementsDone = controller::confirmReinforcementsDone,
                onToggleTradeInCard = controller::toggleTradeInCard,
                onTradeInCards = controller::tradeInCards,
                onAdjustAttackTroops = controller::adjustAttackTroops,
                onAdjustMoveAfterCapture = controller::adjustMoveAfterCapture,
                onAttack = controller::attack,
                onConfirmAttackDone = controller::confirmAttackDone,
                onAdjustFortifyTroops = controller::adjustFortifyTroops,
                onFortifyMove = controller::fortifyMove,
                onRefreshGameState = controller::refreshGameState,
                onClearAutoPhaseNotice = controller::clearAutoPhaseNotice,
            ),
        musicManager = musicManager,
        onNavigateToMain = onLeaveGame,
        onReconnect = controller::connect,
    )
}

/**
 * Gebündelter Anzeigezustand des Spielscreens.
 *
 * Die Bündelung trennt Compose-Rendering vom [LobbyController] und hält Tests
 * stabil: Screens bekommen nur bereits berechnete Werte, keine direkte
 * Controller-Instanz.
 *
 * @param players Spielerleiste im bereits UI-fertigen Format.
 * @param localPlayerId eigene PlayerId für aktionsabhängige Freigaben.
 * @param uiState aktueller serverbasierter Spielzustand.
 * @param isConnected aktueller WebSocket-Status.
 * @param pendingCommandKeys ausstehende Commands, die Buttons sperren.
 * @param mapPainter sichtbare Weltkarte.
 * @param character eigener Charakter für den Header.
 * @param playerName lokaler Anzeigename.
 * @param autoPhaseNoticeText aktuell sichtbare Auto-Phasenmeldung.
 */
internal data class GameScreenContentState(
    val players: List<GamePlayerUi>,
    val localPlayerId: PlayerId?,
    val uiState: GameUiState,
    val isConnected: Boolean,
    val pendingCommandKeys: Set<LobbyCommandKey>,
    val mapPainter: Painter,
    val character: CharacterDefinition? = null,
    val playerName: String = "",
    val autoPhaseNoticeText: String? = null,
)

/**
 * Controller-Callbacks, die vom Spielscreen ausgelöst werden dürfen.
 *
 * Alle Callbacks bleiben hier zusammen, damit UI-Tests einzelne Aktionen
 * ersetzen können und der Screen selbst keine Netzwerkdetails kennt.
 *
 * @param onRegionSelected Kartenregion aus der technischen Hitdetection.
 * @param onToggleCards blendet die private Kartenhand ein oder aus.
 * @param onAdvanceTurn generischer Phasenwechsel für nicht spezialisierten Flow.
 * @param onAdjustReinforcementPlacementAmount ändert den Verstärkungs-Slider.
 * @param onPlaceReinforcements sendet eine Verstärkungsplatzierung.
 * @param onClaimCheatReinforcementBonus fordert den Lichtsensor-Cheatbonus an.
 * @param onConfirmReinforcementsDone bestätigt das Ende der Verstärkungsphase.
 * @param onToggleTradeInCard markiert oder demarkiert eine private Karte.
 * @param onTradeInCards sendet den Kartentausch.
 * @param onAdjustAttackTroops ändert die Angriffstruppen.
 * @param onAdjustMoveAfterCapture ändert die Besetzung nach Capture.
 * @param onAttack sendet den Angriff.
 * @param onConfirmAttackDone bestätigt das Ende der Angriffsphase.
 * @param onAdjustFortifyTroops ändert die Fortify-Truppen.
 * @param onFortifyMove sendet die einmalige Truppenverschiebung.
 * @param onRefreshGameState fordert einen Catch-up-Snapshot an.
 * @param onClearAutoPhaseNotice schließt die sichtbare Auto-Phasenmeldung.
 */
internal data class GameScreenActions(
    val onRegionSelected: (String) -> Unit,
    val onToggleCards: () -> Unit,
    val onAdvanceTurn: () -> Unit,
    val onAdjustReinforcementPlacementAmount: (Int) -> Unit = {},
    val onPlaceReinforcements: () -> Unit = {},
    val onClaimCheatReinforcementBonus: () -> Unit = {},
    val onConfirmReinforcementsDone: () -> Unit = {},
    val onToggleTradeInCard: (CardId) -> Unit = {},
    val onTradeInCards: () -> Unit = {},
    val onAdjustAttackTroops: (Int) -> Unit = {},
    val onAdjustMoveAfterCapture: (Int) -> Unit = {},
    val onAttack: () -> Unit = {},
    val onConfirmAttackDone: () -> Unit = {},
    val onAdjustFortifyTroops: (Int) -> Unit = {},
    val onFortifyMove: () -> Unit = {},
    val onRefreshGameState: () -> Unit,
    val onClearAutoPhaseNotice: () -> Unit = {},
)

/**
 * Kompakter Anzeige- und Interaktionszustand der privaten Kartenhand.
 *
 * Die Gruppierung hält die Panel-Schnittstelle stabil, wenn weitere
 * kartenspezifische Eigenschaften aus dem privaten Snapshot hinzukommen.
 *
 * @param playerName eigener Anzeigename für die Kartenüberschrift.
 * @param handCards ältere reine Textkarten aus Legacy-Antworten.
 * @param privateHandCards aktuelle Karten mit ID und Typ.
 * @param selectedTradeInCardIds lokal markierte Karten für einen Trade-in.
 * @param showTradeControls ob Trade-in-Steuerung in der aktuellen Phase sichtbar ist.
 * @param canSelectTradeCards ob einzelne Karten auswählbar sind.
 * @param canTradeInCards ob genau drei gültige Karten gesendet werden dürfen.
 * @param isTradePending `true`, solange der Serverrequest aussteht.
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
    val showResult: Boolean = true,
)

private data class AttackPanelHostActions(
    val onRegionSelected: (String) -> Unit,
    val onAdjustAttackTroops: (Int) -> Unit,
    val onAdjustMoveAfterCapture: (Int) -> Unit,
    val onAttack: () -> Unit,
)

internal data class AttackResolutionOverlayState(
    val attackerName: String,
    val fromRegionId: String,
    val toRegionId: String,
    val troopCount: Int,
)

private data class FortifyPanelState(
    val fortifyState: FortifyUiState,
    val fromRegionId: String,
    val toRegionId: String,
    val maximumFortifyTroops: Int,
    val canAdjust: Boolean,
    val canMove: Boolean,
)

private data class FortifyPanelActions(
    val onDismiss: () -> Unit,
    val onAdjustFortifyTroops: (Int) -> Unit,
    val onMove: () -> Unit,
)

private data class FortifyPanelHostState(
    val selection: Pair<String, String>?,
    val uiState: GameUiState,
    val canManageFortify: Boolean,
    val isCommandPending: Boolean,
    val localPlayerId: PlayerId?,
    val isConnected: Boolean,
)

private data class FortifyPanelHostActions(
    val onRegionSelected: (String) -> Unit,
    val onAdjustFortifyTroops: (Int) -> Unit,
    val onMove: () -> Unit,
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
    countdownState: Pair<Boolean, Int>? = null,
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
    val onClaimCheatReinforcementBonus = actions.onClaimCheatReinforcementBonus
    val onConfirmReinforcementsDone = actions.onConfirmReinforcementsDone
    val onToggleTradeInCard = actions.onToggleTradeInCard
    val onTradeInCards = actions.onTradeInCards
    val onAdjustAttackTroops = actions.onAdjustAttackTroops
    val onAdjustMoveAfterCapture = actions.onAdjustMoveAfterCapture
    val onAttack = actions.onAttack
    val onConfirmAttackDone = actions.onConfirmAttackDone
    val onAdjustFortifyTroops = actions.onAdjustFortifyTroops
    val onFortifyMove = actions.onFortifyMove
    val onRefreshGameState = actions.onRefreshGameState
    val personalPlayer = players.firstOrNull { it.playerId == localPlayerId } ?: fallbackPlayer()
    val canUseGameActions = uiState.canUseGameActions(localPlayerId, isConnected)

    val isRefreshPending = pendingCommandKeys.hasRefreshRequest()
    val isReinforcementCommandPending = pendingCommandKeys.hasReinforcementRequest()
    val isAttackRequestPending = LobbyCommandKey.ATTACK in pendingCommandKeys
    val isAttackCommandPending = pendingCommandKeys.hasAttackRequest()
    val isFortifyCommandPending = pendingCommandKeys.hasFortifyRequest()
    val canManageReinforcements = uiState.canManageReinforcements(localPlayerId, isConnected)
    val canManageAttacks = uiState.canManageAttacks(localPlayerId, isConnected)
    val canManageFortify = uiState.canManageFortify(localPlayerId, isConnected)
    val remainingReinforcementAmount = uiState.reinforcementState.pendingAmount ?: 0
    val canClaimCheatReinforcementBonus =
        uiState.canManageReinforcements(localPlayerId, isConnected) &&
            !isReinforcementCommandPending

    val reinforcementPanelRegionId =
        visibleReinforcementTarget(uiState, canManageReinforcements, remainingReinforcementAmount)
    val attackPanelSelection = visibleAttackSelection(uiState, canManageAttacks)
    val attackResolutionState =
        createAttackResolutionOverlayState(
            selection = attackPanelSelection,
            uiState = uiState,
            players = players,
            fallbackPlayerName = personalPlayer.name,
            isAttackRequestPending = isAttackRequestPending,
        )
    val isActionResolutionPending = attackResolutionState != null
    val fortifyPanelSelection = visibleFortifySelection(uiState, canManageFortify)
    val canEndCurrentPhase =
        canEndCurrentPhase(
            uiState = uiState,
            localPlayerId = localPlayerId,
            isConnected = isConnected,
            isReinforcementCommandPending = isReinforcementCommandPending,
            isAttackCommandPending = isAttackCommandPending,
            isFortifyCommandPending = isFortifyCommandPending,
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

    val (showCountdown, countdownValue) = countdownState ?: rememberCountdownState(musicManager)
    val statusMessage =
        gameStatusMessage(uiState, isConnected, showCatchUpFeedback)

    val desyncedText = stringResource(id = R.string.game_sync_desynced)
    val isDisconnectState = !isConnected || uiState.isDesynced
    val showDisconnectOverlay = rememberDelayedDisconnectFeedback(isDisconnectState)
    val disconnectMessage = buildDisconnectMessage(isConnected, uiState, desyncedText)

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
        /*
         * Der eigentliche Spielinhalt bleibt zunächst sichtbar, auch wenn ein
         * sehr kurzer Reconnect- oder Catch-up-Zustand auftritt. Erst nach der
         * Verzögerung wird weich geblurt, damit Attack-Responses nicht als
         * flackernder Disconnect-Screen wahrgenommen werden.
         */
        Box(
            modifier =
                if (showDisconnectOverlay) {
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
                    if (canUseGameActions && !isActionResolutionPending) {
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
                onOptionsClick = { showOptionsOverlay = true },
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
            )

            LightSensorCheatTrigger(
                enabled = canClaimCheatReinforcementBonus,
                onTriggered = onClaimCheatReinforcementBonus,
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
                        showResult = fortifyPanelSelection == null,
                    ),
                actions =
                    AttackPanelHostActions(
                        onRegionSelected = onRegionSelected,
                        onAdjustAttackTroops = onAdjustAttackTroops,
                        onAdjustMoveAfterCapture = onAdjustMoveAfterCapture,
                        onAttack = onAttack,
                    ),
            )

            FortifyPanelHost(
                state =
                    FortifyPanelHostState(
                        selection = fortifyPanelSelection,
                        uiState = uiState,
                        canManageFortify = canManageFortify,
                        isCommandPending = isFortifyCommandPending,
                        localPlayerId = localPlayerId,
                        isConnected = isConnected,
                    ),
                actions =
                    FortifyPanelHostActions(
                        onRegionSelected = onRegionSelected,
                        onAdjustFortifyTroops = onAdjustFortifyTroops,
                        onMove = onFortifyMove,
                    ),
            )

            BottomActionClusters(
                state =
                    BottomBarState(
                        currentPhase = uiState.turnPhase,
                        canEndPhase = canEndCurrentPhase,
                        cardsVisible = uiState.cardsVisible,
                    ),
                onToggleCards = onToggleCards,
                onEndPhase = onEndCurrentPhase,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                musicManager = musicManager,
            )

            OptionsOverlay(
                show = showOptionsOverlay,
                isMusicEnabled = isMusicEnabled,
                isSfxEnabled = isSfxEnabled,
                onMusicToggle = { enabled ->
                    isMusicEnabled = enabled
                    musicManager?.setMusicMuted(!enabled)
                },
                onSfxToggle = { enabled ->
                    isSfxEnabled = enabled
                    musicManager?.setSfxMuted(!enabled)
                },
                onNavigateToMain = onNavigateToMain,
                onClose = { showOptionsOverlay = false },
            )

            AttackResolutionOverlay(state = attackResolutionState)

            AutoPhaseNoticeOverlay(
                message = contentState.autoPhaseNoticeText,
                onDismiss = actions.onClearAutoPhaseNotice,
            )

            CountdownOverlay(
                show = showCountdown,
                value = countdownValue,
                character = contentState.character,
                playerName = contentState.playerName,
                musicManager = musicManager,
                onCountdownComplete = {},
            )
        }

        if (showDisconnectOverlay) {
            DisconnectOverlay(
                message = disconnectMessage,
                onReconnect = onReconnect,
                onNavigateToMain = onNavigateToMain,
            )
        }
    }
}

@Composable
private fun GameScreenOverlayContainer(
    overlayAlpha: Float = 0.88f,
    arrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    modifier: Modifier = Modifier,
    columnModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(PulverfassColors.SurfaceVoid.copy(alpha = overlayAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes
                                    .forEach { it.consume() }
                            }
                        }
                    },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = arrangement,
            modifier = columnModifier,
            content = content,
        )
    }
}

/**
 * Zeigt eine automatische Phasenmeldung ohne Bestätigung durch den Spieler.
 *
 * Der Controller queued diese Meldungen, wenn eine Phase serverseitig oder
 * lokal automatisch beendet wurde. Das Overlay blockiert in dieser kurzen Zeit
 * Eingaben, damit keine Karte bewegt oder ein zweiter Request ausgelöst wird.
 *
 * @param message sichtbarer Meldungstext oder `null`, wenn kein Overlay aktiv ist.
 * @param onDismiss Callback nach Ablauf der Anzeigezeit.
 */
@Composable
private fun AutoPhaseNoticeOverlay(
    message: String?,
    onDismiss: () -> Unit,
) {
    if (message == null) return

    LaunchedEffect(message) {
        delay(AUTO_PHASE_NOTICE_DURATION_MILLIS)
        onDismiss()
    }

    GameScreenOverlayContainer(
        overlayAlpha = 0.72f,
        arrangement = Arrangement.spacedBy(14.dp),
        columnModifier =
            Modifier
                .widthIn(max = 440.dp)
                .background(
                    PulverfassColors.SurfaceDark.copy(alpha = 0.82f),
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .testTag("auto_phase_notice_popup"),
    ) {
        PulverfassTitleText(text = "PHASE GEWECHSELT", fontSize = 28.sp, letterSpacing = 2.sp)
        Text(
            text = message,
            color = PulverfassColors.TextOnDark,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * Ingame-Optionsmenü über der Karte.
 *
 * Der gemeinsame [GameScreenOverlayContainer] konsumiert Pointer-Events im
 * Hintergrund. Dadurch kann während des Menüs nur im Menü gescrollt und auf
 * Buttons geklickt werden, nicht versehentlich die Karte darunter gepannt werden.
 *
 * @param show `true`, wenn das Menü sichtbar sein soll.
 * @param isMusicEnabled aktueller Musik-Schalterzustand.
 * @param isSfxEnabled aktueller SFX-Schalterzustand.
 * @param onMusicToggle setzt Musik sofort an oder aus.
 * @param onSfxToggle setzt SFX sofort an oder aus.
 * @param onNavigateToMain verlässt Spiel und Lobby zurück ins Hauptmenü.
 * @param onClose schließt nur das Optionsmenü.
 */
@Composable
private fun OptionsOverlay(
    show: Boolean,
    isMusicEnabled: Boolean,
    isSfxEnabled: Boolean,
    onMusicToggle: (Boolean) -> Unit,
    onSfxToggle: (Boolean) -> Unit,
    onNavigateToMain: () -> Unit,
    onClose: () -> Unit,
) {
    if (!show) return
    val scrollState = rememberScrollState()
    GameScreenOverlayContainer(
        overlayAlpha = 0.85f,
        arrangement = Arrangement.spacedBy(12.dp),
        columnModifier =
            Modifier
                .fillMaxWidth(0.45f)
                .background(
                    PulverfassColors.SurfaceDark.copy(alpha = 0.75f),
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .verticalScroll(scrollState),
    ) {
        PulverfassTitleText(text = "OPTIONEN", fontSize = 32.sp, letterSpacing = 3.sp)
        Spacer(modifier = Modifier.height(8.dp))
        InGameAudioToggleRow(label = "MUSIK", isEnabled = isMusicEnabled, onToggle = onMusicToggle)
        InGameAudioToggleRow(
            label = "SOUND-EFFEKTE",
            isEnabled = isSfxEnabled,
            onToggle = onSfxToggle,
        )
        Spacer(modifier = Modifier.height(16.dp))
        MainButton(
            text = "SCHLIESSEN",
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        )
        MainButton(
            text = "SPIEL VERLASSEN",
            onClick = onNavigateToMain,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Zählt den Spielstart sichtbar herunter.
 *
 * Die Null ist absichtlich kürzer sichtbar als die übrigen Zahlen, weil sie
 * nur als Abschluss-Frame für den Fade dient und nicht wie eine weitere volle
 * Sekunde wirken soll.
 *
 * @param musicManager optionaler Audio-Manager für Countdown-SFX.
 * @return Paar aus Sichtbarkeit und aktuellem Zahlenwert.
 */
@Composable
private fun rememberCountdownState(musicManager: BackgroundMusicManager?): Pair<Boolean, Int> {
    var show by remember { mutableStateOf(true) }
    var value by remember { mutableStateOf(3) }
    LaunchedEffect(Unit) {
        for (i in 3 downTo 1) {
            value = i
            musicManager?.playSfx(R.raw.sfx_ui_click)
            delay(COUNTDOWN_STEP_MILLIS)
        }
        value = 0
        musicManager?.playSfx(R.raw.sfx_ui_click)
        delay(COUNTDOWN_ZERO_MILLIS)
        show = false
    }
    return show to value
}

/**
 * Blockierendes Start-Overlay vor der ersten Spielinteraktion.
 *
 * @param show `true`, solange der Countdown sichtbar ist.
 * @param value aktuelle Countdown-Zahl.
 * @param character eigener Charakter für die filmische Variante.
 * @param playerName Anzeigename unter der Charakter-Coin.
 * @param musicManager optionaler Audio-Manager für SFX.
 * @param onCountdownComplete Callback nach dem finalen Fade.
 */
@Composable
private fun CountdownOverlay(
    show: Boolean,
    value: Int,
    character: CharacterDefinition?,
    playerName: String = "",
    musicManager: BackgroundMusicManager? = null,
    onCountdownComplete: () -> Unit = {},
) {
    if (!show) return
    val inputBlocker =
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    if (character != null) {
        CinematicCountdown(
            value = value,
            character = character,
            playerName = playerName,
            musicManager = musicManager,
            modifier = inputBlocker,
            onCountdownComplete = onCountdownComplete,
        )
    } else {
        GameScreenOverlayContainer(modifier = inputBlocker) {
            PulverfassTitleText(text = "MACH DICH BEREIT!", fontSize = 48.sp)
            Text(
                text = "Das Spiel beginnt gleich...",
                fontSize = 20.sp,
                color = PulverfassColors.TextOnDark,
            )
            PulverfassTitleText(text = value.toString(), fontSize = 96.sp)
        }
    }
}

@Composable
private fun CinematicCountdown(
    value: Int,
    character: CharacterDefinition,
    playerName: String = "",
    musicManager: BackgroundMusicManager? = null,
    modifier: Modifier = Modifier,
    onCountdownComplete: () -> Unit = {},
) {
    val isFinishing = value <= 0
    val blackOverlayAlpha by animateFloatAsState(
        targetValue = if (isFinishing) 1f else 0f,
        animationSpec = tween(900, delayMillis = 600),
        label = "blackOverlay",
    )
    LaunchedEffect(isFinishing) {
        if (isFinishing) {
            delay(1500)
            onCountdownComplete()
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        if (character.isVideoWallpaper) {
            VideoPlayer(
                videoResId = character.wallpaperResId,
                loop = true,
                cover = true,
                muted = true,
                modifier = Modifier.fillMaxSize().blur(radius = 6.dp),
            )
        } else {
            val imageRes = character.fallbackImageResId ?: character.wallpaperResId
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(radius = 6.dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(PulverfassColors.SurfaceVoid.copy(alpha = 0.05f)),
        )
        val headerAlpha = remember { Animatable(0f) }
        LaunchedEffect(musicManager) {
            headerAlpha.animateTo(1f, tween(600))
            musicManager?.playSfx(R.raw.sfx_attack_confirm)
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "MACH DICH BEREIT!!!",
                fontFamily = PulverfassFonts.CinzelDecorative,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = PulverfassColors.GoldCoin,
                letterSpacing = 6.sp,
                style =
                    androidx.compose.ui.text.TextStyle(
                        shadow =
                            androidx.compose.ui.graphics.Shadow(
                                color = PulverfassColors.GoldCoin,
                                blurRadius = 24f,
                            ),
                    ),
                modifier = Modifier.graphicsLayer { alpha = headerAlpha.value },
            )
            Text(
                text = "Das Spiel beginnt",
                fontFamily = CormorantGaramond,
                fontStyle = FontStyle.Italic,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.85f),
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Box(
                contentAlignment = Alignment.Center,
            ) {
                CharacterCoin(character = character, size = 130.dp)
            }
            Text(
                text = playerName.uppercase(),
                fontFamily = CormorantGaramond,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
                letterSpacing = 4.sp,
                color = Color.White.copy(alpha = 0.85f),
            )
            val countdownScale = remember { Animatable(1f) }
            LaunchedEffect(value) {
                countdownScale.snapTo(1.4f)
                countdownScale.animateTo(
                    1f,
                    animationSpec = spring(dampingRatio = 0.4f, stiffness = 200f),
                )
            }
            Text(
                text = value.toString(),
                fontFamily = PulverfassFonts.CinzelDecorative,
                fontWeight = FontWeight.Bold,
                fontSize = 64.sp,
                color = PulverfassColors.GoldCoin,
                style =
                    androidx.compose.ui.text.TextStyle(
                        shadow =
                            androidx.compose.ui.graphics.Shadow(
                                color = PulverfassColors.GoldCoin,
                                blurRadius = 40f,
                            ),
                    ),
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = countdownScale.value
                        scaleY = countdownScale.value
                    },
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = blackOverlayAlpha)),
        )
    }
}

private fun buildDisconnectMessage(
    isConnected: Boolean,
    uiState: GameUiState,
    desyncedText: String,
): String =
    when {
        !isConnected -> ""
        uiState.isDesynced -> uiState.lastSyncError ?: desyncedText
        else -> ""
    }

/**
 * Hört auf den Lichtsensor und löst den Debug-/Cheatbonus einmalig aus.
 *
 * Die Geste verlangt einen hellen Ausgangswert und danach ein deutliches
 * Abdunkeln, damit normale Raumlichtschwankungen keinen Bonus triggern.
 *
 * @param enabled `true`, wenn die Verstärkungsphase den Cheat aktuell zulässt.
 * @param onTriggered Callback für den Bonusrequest.
 */
@Composable
private fun LightSensorCheatTrigger(
    enabled: Boolean,
    onTriggered: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnTriggered = rememberUpdatedState(onTriggered)
    var previousLux by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context, enabled) {
        if (!enabled) {
            return@DisposableEffect onDispose {}
        }

        previousLux = null
        var triggered = false

        val sensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor == null) {
            return@DisposableEffect onDispose {}
        }

        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val lux = event.values.firstOrNull() ?: return
                    val wasBright = previousLux?.let { it >= CHEAT_LIGHT_BASELINE_LUX } ?: false
                    val isCovered = lux <= CHEAT_LIGHT_COVERED_LUX

                    if (wasBright && isCovered && !triggered) {
                        triggered = true
                        currentOnTriggered.value()
                    }

                    previousLux = lux
                }

                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int,
                ) = Unit
            }

        sensorManager.registerListener(
            listener,
            lightSensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        )

        onDispose {
            sensorManager.unregisterListener(listener)
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

/**
 * Verzögert harte Disconnect-/Desync-Overlays um kurze Netzwerkwackler.
 *
 * Besonders nach Angriffen können Serverantworten und Folgephasen sehr dicht
 * eintreffen. Ohne diese Verzögerung würde die Oberfläche kurz den
 * Disconnect-Zustand zeigen, obwohl die Verbindung weiterhin nutzbar ist.
 *
 * @param isDisconnectState `true`, wenn Verbindung oder Sync gerade problematisch sind.
 * @return `true`, wenn der Zustand lange genug anhält und sichtbar werden soll.
 */
@Composable
private fun rememberDelayedDisconnectFeedback(isDisconnectState: Boolean): Boolean {
    var showDisconnectFeedback by remember { mutableStateOf(false) }
    LaunchedEffect(isDisconnectState) {
        showDisconnectFeedback = false
        if (isDisconnectState) {
            delay(DISCONNECT_FEEDBACK_DELAY_MILLIS)
            showDisconnectFeedback = true
        }
    }
    return showDisconnectFeedback
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

/**
 * Blockiert die Karte, solange ein Angriff auf die Serverauflösung wartet.
 *
 * Das Overlay ist absichtlich leichtgewichtig: es überbrückt nur die Latenz
 * zwischen AttackRequest und AttackResolvedBroadcastEvent. Die spätere
 * Ergebnisanzeige kommt weiterhin aus dem autoritativen Serverevent.
 *
 * @param state sichtbarer Angriffsstatus oder `null`, wenn kein Angriff aussteht.
 */
@Composable
private fun BoxScope.AttackResolutionOverlay(state: AttackResolutionOverlayState?) {
    if (state == null) {
        return
    }

    val inputBlocker =
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    val pulseTransition = rememberInfiniteTransition(label = "attack_resolution_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(520, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "attack_resolution_pulse_alpha",
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(inputBlocker)
                .background(Color.Black.copy(alpha = 0.18f))
                .testTag("attack_resolution_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 460.dp),
            shape = RoundedCornerShape(6.dp),
            color = HudSurfaceColor,
            contentColor = HudContentColor,
            border = BorderStroke(1.dp, HudBorderColor),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(16.dp)
                            .background(
                                PulverfassColors.DangerBright.copy(alpha = pulseAlpha),
                                CircleShape,
                            ),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.game_attack_resolving_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = HudContentColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text =
                            stringResource(
                                id = R.string.game_attack_resolving_route,
                                state.attackerName,
                                regionDisplayName(state.fromRegionId),
                                regionDisplayName(state.toRegionId),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = HudContentColor,
                    )
                    Text(
                        text =
                            stringResource(
                                id = R.string.game_attack_resolving_troops,
                                state.troopCount,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = HudContentColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Übersetzt den privaten Kartenstate in die Props der Kartenhand.
 *
 * @param player eigener UI-Spieler für Überschrift und Avatar-Kontext.
 * @param uiState aktueller GameState mit privaten Karten.
 * @param localPlayerId eigene PlayerId für Zugberechtigungen.
 * @param isConnected aktueller Verbindungsstatus.
 * @param isReinforcementCommandPending `true`, wenn ein Verstärkungsrequest läuft.
 * @param pendingCommandKeys alle aktuell offenen Commands.
 * @return UI-fertiger Panel-State für die Kartenhand.
 */
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

/**
 * Rendert das Verstärkungspanel nur, wenn ein gültiges Zielgebiet sichtbar ist.
 *
 * @param state lokale und serverbasierte Verstärkungsdaten.
 * @param actions Callbacks für Slider, Platzierung und Abwahl.
 */
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

/**
 * Rendert Auswahlpanel oder letztes Ergebnis der Angriffsphase.
 *
 * @param state lokaler Angriffsstatus inklusive Auswahl und Pending-Status.
 * @param actions Callbacks für Slider, Angriff und Abwahl.
 */
@Composable
private fun BoxScope.AttackPanelHost(
    state: AttackPanelHostState,
    actions: AttackPanelHostActions,
) {
    if (state.selection == null) {
        if (state.showResult) {
            AttackResultHost(result = state.uiState.attackState.latestResult)
        }
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

/**
 * Rendert das Fortify-Panel für genau eine verbundene Truppenverschiebung.
 *
 * @param state lokaler Fortify-Status inklusive Source-/Target-Auswahl.
 * @param actions Callbacks für Slider, Move und Abwahl.
 */
@Composable
private fun BoxScope.FortifyPanelHost(
    state: FortifyPanelHostState,
    actions: FortifyPanelHostActions,
) {
    val selection = state.selection ?: return
    val (fromRegionId, toRegionId) = selection
    FortifyPanel(
        state =
            FortifyPanelState(
                fortifyState = state.uiState.fortifyState,
                fromRegionId = fromRegionId,
                toRegionId = toRegionId,
                maximumFortifyTroops = maximumFortifyTroops(state.uiState, fromRegionId),
                canAdjust = state.canManageFortify && !state.isCommandPending,
                canMove =
                    state.uiState.canSubmitFortifyMove(
                        state.localPlayerId,
                        state.isConnected,
                    ) && !state.isCommandPending,
            ),
        actions =
            FortifyPanelActions(
                onDismiss = { actions.onRegionSelected(fromRegionId) },
                onAdjustFortifyTroops = actions.onAdjustFortifyTroops,
                onMove = actions.onMove,
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

private fun maximumFortifyTroops(
    uiState: GameUiState,
    fromRegionId: String,
): Int =
    uiState.territoryStates[
        GameMapTerritoryMapper.toTerritoryId(fromRegionId),
    ]?.troopCount?.minus(1) ?: uiState.fortifyState.troopCount

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

private fun Set<LobbyCommandKey>.hasFortifyRequest(): Boolean =
    any { it == LobbyCommandKey.FORTIFY_MOVE }

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

/**
 * Erstellt die kurze "Angriff wird ausgewertet"-Meldung während eines Pending Requests.
 *
 * @param selection aktuelle Quelle-Ziel-Auswahl.
 * @param uiState GameState mit ausgewählter Truppenanzahl.
 * @param players Spielerleiste zur Auflösung des Angreifernamens.
 * @param fallbackPlayerName Name, falls der lokale Spieler noch nicht in [players] steht.
 * @param isAttackRequestPending `true`, solange der AttackRequest offen ist.
 * @return Overlay-State oder `null`, wenn keine Pending-Anzeige nötig ist.
 */
internal fun createAttackResolutionOverlayState(
    selection: Pair<String, String>?,
    uiState: GameUiState,
    players: List<GamePlayerUi>,
    fallbackPlayerName: String,
    isAttackRequestPending: Boolean,
): AttackResolutionOverlayState? {
    if (!isAttackRequestPending) {
        return null
    }
    val (fromRegionId, toRegionId) = selection ?: return null
    val attackerName =
        players.firstOrNull { it.playerId == uiState.activePlayerId }?.name
            ?: fallbackPlayerName

    return AttackResolutionOverlayState(
        attackerName = attackerName,
        fromRegionId = fromRegionId,
        toRegionId = toRegionId,
        troopCount = uiState.attackState.attackTroops,
    )
}

/**
 * Ein Fortify-Panel benötigt zwei bereits validierte eigene verbundene Gebiete.
 *
 * Bis zur vollständigen Auswahl bleibt die Karte bedienbar, damit Quelle und
 * Ziel direkt über die Hitmap gewählt oder gewechselt werden können.
 */
private fun visibleFortifySelection(
    uiState: GameUiState,
    canManageFortify: Boolean,
): Pair<String, String>? {
    if (uiState.turnPhase != TurnPhase.FORTIFY || !canManageFortify) {
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
    isFortifyCommandPending: Boolean,
    pendingCommandKeys: Set<LobbyCommandKey>,
): Boolean =
    when (uiState.turnPhase) {
        TurnPhase.REINFORCEMENTS ->
            uiState.canConfirmReinforcementsDone(localPlayerId, isConnected) &&
                !isReinforcementCommandPending
        TurnPhase.ATTACK ->
            uiState.canConfirmAttackDone(localPlayerId, isConnected) &&
                !isAttackCommandPending
        TurnPhase.FORTIFY ->
            uiState.canRequestTurnAdvance(localPlayerId, isConnected) &&
                !isFortifyCommandPending &&
                !pendingCommandKeys.contains(LobbyCommandKey.TURN_ADVANCE)
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

/*
 * Nachschlagetabelle Region-ID -> lesbarer Gebietsname. Die technischen IDs wie
 * "central_europe" tauchen nur intern auf; in den Auswahl-Panels und im
 * Kampfergebnis sollen Spieler den Namen ("Mitteleuropa") sehen.
 */
private val regionDisplayNamesById: Map<String, String> =
    PulverfassMapDefaults.regions.associate { region -> region.id to region.name }

private fun regionDisplayName(regionId: String): String =
    regionDisplayNamesById[regionId] ?: regionId

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
        color = HudSurfaceColor,
        contentColor = HudContentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, HudBorderColor),
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
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = HudAccentColor,
                        contentColor = HudInverseColor,
                    ),
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
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("game_top_bar"),
        shape = RoundedCornerShape(0.dp),
        color = HudSurfaceColor,
        contentColor = HudContentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, HudBorderColor),
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
                        .padding(start = 12.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val optionsDescription = "Optionen"
                FilledTonalButton(
                    onClick = onOptionsClick,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .semantics { contentDescription = optionsDescription }
                            .testTag("game_options_button"),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = HudSurfaceMutedColor,
                            contentColor = HudContentColor,
                        ),
                ) {
                    Text(
                        text = "⚙",
                        style = MaterialTheme.typography.titleMedium,
                        color = HudContentColor,
                    )
                }
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
                        color = HudAccentColor,
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
                    color = HudContentColor,
                )
                Text(
                    text = stringResource(id = phase.labelRes()),
                    modifier = Modifier.testTag("game_phase_value"),
                    style = MaterialTheme.typography.titleSmall,
                    color = HudAccentColor,
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
                    color = HudContentColor,
                )
                Text(
                    text = stringResource(id = R.string.game_round_value, round),
                    modifier = Modifier.testTag("game_round_value"),
                    style = MaterialTheme.typography.titleSmall,
                    color = HudAccentColor,
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
) {
    if (isVisible) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(0.dp),
            color = HudSurfaceColor,
            contentColor = HudContentColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, HudBorderColor),
        ) {
            PrivateHandPanel(
                state = state,
                actions = actions,
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
        border = BorderStroke(1.dp, HudBorderColor),
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
                        if (isActive) HudActiveHighlightColor else Color.Transparent,
                        RoundedCornerShape(6.dp),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ConnectionStatusIndicator(
                        status = player.connectionStatus,
                        modifier =
                            Modifier.testTag(
                                "player_connection_status_${player.playerId.value}",
                            ),
                    )
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isActive) HudAccentColor else HudContentColor,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                if (player.isHost) {
                    HostIndicator()
                }
            }
        }
    }
}

/**
 * Zeigt den aktuellen Verbindungsstatus eines Spielers als kleinen Farbpunkt.
 */
@Composable
internal fun ConnectionStatusIndicator(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
) {
    val description =
        stringResource(
            id =
                when (status) {
                    ConnectionStatus.CONNECTED -> R.string.game_connection_connected
                    ConnectionStatus.DISCONNECTED -> R.string.game_connection_disconnected
                },
        )

    Canvas(
        modifier =
            modifier
                .size(10.dp)
                .semantics { contentDescription = description },
    ) {
        drawCircle(color = connectionStatusIndicatorColor(status))
    }
}

internal fun connectionStatusIndicatorColor(status: ConnectionStatus): Color =
    when (status) {
        ConnectionStatus.CONNECTED -> PulverfassColors.Success
        ConnectionStatus.DISCONNECTED -> PulverfassColors.DangerBright
    }

@Composable
private fun HostIndicator() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = HudAccentColor,
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
            color = HudAccentColor,
        )
    }
}

@Composable
private fun PlayerAvatar(
    player: GamePlayerUi,
    size: Dp,
) {
    val character = player.characterId?.let(Characters::byId)
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = player.color.copy(alpha = 0.18f),
        border = BorderStroke(2.dp, player.color),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (character != null) {
                Image(
                    painter = painterResource(id = character.drawableRes),
                    contentDescription = character.displayName,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                )
            } else {
                Text(
                    text = player.avatarText,
                    style = MaterialTheme.typography.labelMedium,
                    color = HudContentColor,
                    fontWeight = FontWeight.Bold,
                )
            }
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
                        musicManager?.playSfx(R.raw.sfx_card_select)
                        actions.onTradeInCards()
                    },
                    enabled = state.canTradeInCards,
                    modifier = Modifier.fillMaxWidth().testTag("trade_in_cards_button"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = HudAccentColor,
                            contentColor = HudInverseColor,
                        ),
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
        color = if (item.isSelected) HudSelectedCardColor else HudSurfaceMutedColor,
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
                        regionDisplayName(state.selectedRegionId),
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
                            regionDisplayName(state.fromRegionId),
                            regionDisplayName(state.toRegionId),
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

/**
 * Steuerung der einmaligen Truppenverschiebung in der Fortify-Phase.
 *
 * Das Panel verwendet denselben Slider wie Angriff und Verstärkung. Die Karte
 * bleibt darunter frei, damit Quelle und Ziel durch erneute Gebietsauswahl
 * schnell korrigiert werden können.
 */
@Composable
private fun FortifyPanel(
    state: FortifyPanelState,
    actions: FortifyPanelActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .widthIn(max = 560.dp)
                .testTag("fortify_panel"),
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
                            id = R.string.game_fortify_route,
                            regionDisplayName(state.fromRegionId),
                            regionDisplayName(state.toRegionId),
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                val closePanelDescription = stringResource(id = R.string.game_fortify_close)
                BlockActionButton(
                    label = "X",
                    onClick = actions.onDismiss,
                    selected = false,
                    enabled = true,
                    modifier =
                        Modifier
                            .size(34.dp)
                            .semantics { contentDescription = closePanelDescription }
                            .testTag("close_fortify_panel"),
                )
            }
            TroopAmountSliderRow(
                label = stringResource(id = R.string.game_fortify_troops),
                amount = state.fortifyState.troopCount,
                minAmount = MIN_FORTIFY_TROOPS,
                maxAmount = state.maximumFortifyTroops,
                canAdjust = state.canAdjust,
                onAdjust = actions.onAdjustFortifyTroops,
                tagPrefix = "fortify_troops",
            )
            BlockActionButton(
                label = stringResource(id = R.string.game_fortify_submit),
                onClick = actions.onMove,
                selected = true,
                enabled = state.canMove,
                modifier = Modifier.fillMaxWidth().testTag("fortify_submit_button"),
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
                        regionDisplayName(fromRegionId),
                        regionDisplayName(toRegionId),
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
private data class BottomBarState(
    val currentPhase: TurnPhase?,
    val canEndPhase: Boolean,
    val cardsVisible: Boolean,
)

@Composable
private fun BottomActionClusters(
    state: BottomBarState,
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
        border = BorderStroke(1.dp, HudBorderColor),
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
                    if (state.cardsVisible) {
                        stringResource(id = R.string.game_cards_hide)
                    } else {
                        stringResource(id = R.string.game_cards_button)
                    },
                onClick = onToggleCards,
                selected = true,
                enabled = true,
                modifier = Modifier.width(CardsSidebarWidth - 20.dp),
                musicManager = musicManager,
            )

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhaseChip(
                    label = stringResource(id = R.string.game_action_reinforce),
                    selected = state.currentPhase == TurnPhase.REINFORCEMENTS,
                    modifier = Modifier.weight(1f),
                )
                PhaseChip(
                    label = stringResource(id = R.string.game_action_attack),
                    selected = state.currentPhase == TurnPhase.ATTACK,
                    modifier = Modifier.weight(1f),
                )
                PhaseChip(
                    label = stringResource(id = R.string.game_action_move),
                    selected = state.currentPhase == TurnPhase.FORTIFY,
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
                    enabled = state.canEndPhase,
                    modifier = Modifier.fillMaxWidth().testTag("end_round_button"),
                    musicManager = musicManager,
                    sfxResId = R.raw.sfx_attack_confirm,
                )
            }
        }
    }
}

/**
 * Reiner Phasenindikator ohne Klickverhalten.
 *
 * Die aktuelle Phase wird wie die Topbar-Werte in Gold hervorgehoben; die
 * übrigen Phasen bleiben als gedämpfte Chips sichtbar.
 */
@Composable
private fun PhaseChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) HudActiveHighlightColor else HudSurfaceMutedColor,
        contentColor = if (selected) HudAccentColor else HudContentColor,
        border = if (selected) BorderStroke(1.dp, HudBorderColor) else null,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun BlockActionButton(
    label: String,
    onClick: () -> Unit,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    musicManager: BackgroundMusicManager? = null,
    sfxResId: Int = R.raw.sfx_ui_click,
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
                    containerColor = HudAccentColor,
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
            PulverfassTitleText(text = "⚠ VERBINDUNG UNTERBROCHEN", fontSize = 28.sp)
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
