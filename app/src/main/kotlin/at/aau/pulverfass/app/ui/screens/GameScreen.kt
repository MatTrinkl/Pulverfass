package at.aau.pulverfass.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.game.GamePlayerUi
import at.aau.pulverfass.app.game.GameUiState
import at.aau.pulverfass.app.game.lobbyPlayersToGamePlayers
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.ui.map.InteractiveGameMap
import at.aau.pulverfass.app.ui.map.InteractiveGameMapOptions
import at.aau.pulverfass.app.ui.map.PulverfassMapDefaults
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.lobby.state.TurnPhase

private val HudSurfaceColor = Color.White
private val HudSurfaceMutedColor = Color(0xFFF1F1F1)
private val HudBorderColor = Color.Black
private val HudContentColor = Color.Black
private val HudInverseColor = Color.White
private val TopBarHeight = 52.dp
private val BottomBarHeight = 54.dp
private val SidebarWidth = 156.dp
private val CardsSidebarWidth = SidebarWidth

@Composable
fun GameScreen(controller: LobbyController) {
    val lobbyState by controller.state.collectAsState()
    val players = remember(lobbyState.players) { lobbyPlayersToGamePlayers(lobbyState.players) }
    val mapPainter = painterResource(id = R.drawable.map_world)

    GameScreenContent(
        players = players,
        localPlayerId = lobbyState.ownPlayerId,
        uiState = lobbyState.gameState,
        onRegionSelected = controller::selectGameRegion,
        onToggleCards = controller::toggleCards,
        onAdvanceTurn = controller::advanceTurn,
        mapPainter = mapPainter,
    )
}

@Composable
private fun GameScreenContent(
    players: List<GamePlayerUi>,
    localPlayerId: PlayerId?,
    uiState: GameUiState,
    onRegionSelected: (String) -> Unit,
    onToggleCards: () -> Unit,
    onAdvanceTurn: () -> Unit,
    mapPainter: Painter,
) {
    val personalPlayer = players.firstOrNull { it.playerId == localPlayerId } ?: fallbackPlayer()
    val activePlayer =
        players.firstOrNull { it.playerId == uiState.activePlayerId }
            ?: personalPlayer

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
                onRegionSelected(region.id)
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

        CardsSidebar(
            player = personalPlayer,
            handCards = uiState.handCards,
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

        BottomActionClusters(
            currentPhase = uiState.turnPhase,
            canAdvanceTurn = uiState.canRequestTurnAdvance(localPlayerId),
            cardsVisible = uiState.cardsVisible,
            onToggleCards = onToggleCards,
            onAdvanceTurn = onAdvanceTurn,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
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
            CardsOverview(
                player = player,
                handCards = handCards,
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
private fun CardsOverview(
    player: GamePlayerUi,
    handCards: List<String>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("game_cards_panel"),
        shape = RoundedCornerShape(18.dp),
        color = HudSurfaceColor,
        contentColor = HudContentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(id = R.string.game_cards_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = HudContentColor,
            )
            Text(
                text = player.name,
                style = MaterialTheme.typography.labelMedium,
                color = HudContentColor,
            )

            if (handCards.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.game_cards_empty),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                handCards.forEach { card ->
                    Text(
                        text = card,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomActionClusters(
    currentPhase: TurnPhase?,
    canAdvanceTurn: Boolean,
    cardsVisible: Boolean,
    onToggleCards: () -> Unit,
    onAdvanceTurn: () -> Unit,
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
                    onClick = onAdvanceTurn,
                    selected = true,
                    enabled = canAdvanceTurn,
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
