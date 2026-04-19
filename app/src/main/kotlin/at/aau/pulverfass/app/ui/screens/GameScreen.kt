package at.aau.pulverfass.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.ui.map.GameMapRegion
import at.aau.pulverfass.app.ui.map.GameMapRegionState
import at.aau.pulverfass.app.ui.map.InteractiveGameMap
import at.aau.pulverfass.app.ui.map.PulverfassMapDefaults

private val HudSurfaceColor = Color.White
private val HudSurfaceMutedColor = Color(0xFFF1F1F1)
private val HudBorderColor = Color.Black
private val HudContentColor = Color.Black
private val HudInverseColor = Color.White
private val TopBarHeight = 52.dp
private val BottomBarHeight = 54.dp
private val SidebarWidth = 156.dp

private enum class DemoGamePhase(
    @StringRes val labelRes: Int,
) {
    VERSTAERKEN(R.string.game_action_reinforce),
    ANGRIFF(R.string.game_action_attack),
    VERSCHIEBEN(R.string.game_action_move),
}

private data class DemoPlayer(
    val id: String,
    val name: String,
    val avatarText: String,
    val color: Color,
    val isHost: Boolean,
)

private data class DemoGameUiState(
    val round: Int,
    val currentPhase: DemoGamePhase,
    val activePlayerId: String,
    val personalPlayerId: String,
    val selectedRegionId: String,
    val cardsVisible: Boolean,
    val regionStates: Map<String, GameMapRegionState>,
)

@Composable
fun GameScreen() {
    val players = remember { createDemoPlayers() }
    val mapPainter = painterResource(id = R.drawable.world_map)
    var uiState by remember {
        mutableStateOf(
            createDemoGameUiState(players = players),
        )
    }

    val personalPlayer = players.first { it.id == uiState.personalPlayerId }
    val activePlayer = players.first { it.id == uiState.activePlayerId }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("game_screen_root"),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            GameTopBar(
                personalPlayer = personalPlayer,
                phase = uiState.currentPhase,
                round = uiState.round,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                InteractiveGameMap(
                    regions = PulverfassMapDefaults.regions,
                    regionStates = uiState.regionStates,
                    selectedRegionId = uiState.selectedRegionId,
                    onRegionSelected = { region -> uiState = uiState.copy(selectedRegionId = region.id) },
                    backgroundPainter = mapPainter,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                )

                PlayerSidebar(
                    players = players,
                    activePlayerId = uiState.activePlayerId,
                    modifier =
                        Modifier
                            .width(SidebarWidth)
                            .fillMaxHeight(),
                )
            }

            BottomActionClusters(
                currentPhase = uiState.currentPhase,
                onPhaseSelected = { phase ->
                    uiState = applyPhaseSelection(uiState = uiState, phase = phase)
                },
                cardsVisible = uiState.cardsVisible,
                onToggleCards = {
                    uiState = uiState.copy(cardsVisible = !uiState.cardsVisible)
                },
                onEndRound = {
                    uiState =
                        advanceRound(
                            uiState = uiState,
                            players = players,
                            regions = PulverfassMapDefaults.regions,
                        )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
            )
        }

        AnimatedVisibility(
            visible = uiState.cardsVisible,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = SidebarWidth + 12.dp, bottom = BottomBarHeight + 12.dp),
        ) {
            CardsOverview(
                activePlayer = activePlayer,
                modifier = Modifier.width(220.dp),
            )
        }
    }
}

@Composable
private fun GameTopBar(
    personalPlayer: DemoPlayer,
    phase: DemoGamePhase,
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
                    text = stringResource(id = phase.labelRes),
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
private fun PlayerSidebar(
    players: List<DemoPlayer>,
    activePlayerId: String,
    modifier: Modifier = Modifier,
) {
    val playerListScrollState = rememberScrollState()

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
                Text(
                    text = stringResource(id = R.string.players),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = HudContentColor,
                )

                players.forEach { player ->
                    PlayerSidebarRow(
                        player = player,
                        isActive = player.id == activePlayerId,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PlayerSidebarRow(
    player: DemoPlayer,
    isActive: Boolean,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(isActive) {
        if (isActive) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column {
        Row(
            modifier =
                Modifier
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .fillMaxWidth()
                    .background(if (isActive) HudSurfaceMutedColor else Color.Transparent, RoundedCornerShape(14.dp))
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
private fun ActiveTurnIndicator(
    isVisible: Boolean,
) {
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
    player: DemoPlayer,
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
    activePlayer: DemoPlayer,
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
                text = activePlayer.name,
                style = MaterialTheme.typography.labelMedium,
                color = HudContentColor,
            )

            Text(text = stringResource(id = R.string.game_cards_demo_infantry), style = MaterialTheme.typography.bodySmall)
            Text(text = stringResource(id = R.string.game_cards_demo_cavalry), style = MaterialTheme.typography.bodySmall)
            Text(text = stringResource(id = R.string.game_cards_demo_artillery), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BottomActionClusters(
    currentPhase: DemoGamePhase,
    onPhaseSelected: (DemoGamePhase) -> Unit,
    cardsVisible: Boolean,
    onToggleCards: () -> Unit,
    onEndRound: () -> Unit,
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
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhaseButton(
                    label = stringResource(id = R.string.game_action_reinforce),
                    selected = currentPhase == DemoGamePhase.VERSTAERKEN,
                    onClick = { onPhaseSelected(DemoGamePhase.VERSTAERKEN) },
                    modifier = Modifier.weight(1f),
                )
                PhaseButton(
                    label = stringResource(id = R.string.game_action_attack),
                    selected = currentPhase == DemoGamePhase.ANGRIFF,
                    onClick = { onPhaseSelected(DemoGamePhase.ANGRIFF) },
                    modifier = Modifier.weight(1f),
                )
                PhaseButton(
                    label = stringResource(id = R.string.game_action_move),
                    selected = currentPhase == DemoGamePhase.VERSCHIEBEN,
                    onClick = { onPhaseSelected(DemoGamePhase.VERSCHIEBEN) },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    modifier = Modifier.weight(1f),
                )
                BlockActionButton(
                    label = stringResource(id = R.string.game_end_round_button),
                    onClick = onEndRound,
                    selected = true,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag("end_round_button"),
                )
            }
        }
    }
}

@Composable
private fun PhaseButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BlockActionButton(
        label = label,
        onClick = onClick,
        selected = selected,
        modifier = modifier,
    )
}

@Composable
private fun BlockActionButton(
    label: String,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
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

private fun createDemoPlayers(): List<DemoPlayer> =
    listOf(
        DemoPlayer(
            id = "robin",
            name = "Robin",
            avatarText = "RO",
            color = Color(0xFF6FD4C5),
            isHost = false,
        ),
        DemoPlayer(
            id = "matthias",
            name = "Matthias",
            avatarText = "MT",
            color = Color(0xFFE0B35C),
            isHost = true,
        ),
        DemoPlayer(
            id = "marco",
            name = "Marco",
            avatarText = "MR",
            color = Color(0xFFE78D91),
            isHost = false,
        ),
        DemoPlayer(
            id = "aldin",
            name = "Aldin",
            avatarText = "AL",
            color = Color(0xFF79A8E8),
            isHost = false,
        ),
    )

private fun createDemoGameUiState(
    players: List<DemoPlayer>,
): DemoGameUiState {
    val playersById = players.associateBy { it.id }

    fun regionState(
        ownerId: String,
        troops: Int,
    ): GameMapRegionState {
        val owner = requireNotNull(playersById[ownerId])
        return GameMapRegionState(
            ownerPlayerId = owner.id,
            ownerName = owner.name,
            troopCount = troops,
            accentColor = owner.color,
        )
    }

    return DemoGameUiState(
        round = 7,
        currentPhase = DemoGamePhase.VERSTAERKEN,
        activePlayerId = "marco",
        personalPlayerId = "robin",
        selectedRegionId = "europe",
        cardsVisible = false,
        regionStates =
            linkedMapOf(
                "north_america" to regionState(ownerId = "matthias", troops = 5),
                "south_america" to regionState(ownerId = "aldin", troops = 3),
                "greenland" to regionState(ownerId = "robin", troops = 2),
                "europe" to regionState(ownerId = "robin", troops = 4),
                "africa" to regionState(ownerId = "marco", troops = 6),
                "asia" to regionState(ownerId = "marco", troops = 7),
                "australia" to regionState(ownerId = "matthias", troops = 4),
            ),
    )
}

// Hält die Demo interaktiv, bis fachlicher State aus den anderen Modulen kommt.
private fun applyPhaseSelection(
    uiState: DemoGameUiState,
    phase: DemoGamePhase,
): DemoGameUiState {
    if (phase != DemoGamePhase.VERSTAERKEN) {
        return uiState.copy(currentPhase = phase)
    }

    val regionState =
        uiState.regionStates[uiState.selectedRegionId]
            ?: return uiState.copy(currentPhase = phase)
    if (regionState.ownerPlayerId != uiState.activePlayerId) {
        return uiState.copy(currentPhase = phase)
    }

    return uiState.copy(
        currentPhase = phase,
        regionStates =
            uiState.regionStates.toMutableMap().apply {
                put(
                    uiState.selectedRegionId,
                    regionState.copy(troopCount = regionState.troopCount + 1),
                )
            },
    )
}

// Wechselt zum nächsten Spieler und erhöht nach einer vollen Runde den Zähler.
private fun advanceRound(
    uiState: DemoGameUiState,
    players: List<DemoPlayer>,
    regions: List<GameMapRegion>,
): DemoGameUiState {
    val currentIndex = players.indexOfFirst { it.id == uiState.activePlayerId }.coerceAtLeast(0)
    val nextIndex = (currentIndex + 1) % players.size
    val nextPlayer = players[nextIndex]
    val nextRound =
        if (nextIndex == 0) {
            uiState.round + 1
        } else {
            uiState.round
        }
    val nextSelectedRegionId =
        regions.firstOrNull { region ->
            uiState.regionStates[region.id]?.ownerPlayerId == nextPlayer.id
        }?.id ?: uiState.selectedRegionId

    return uiState.copy(
        round = nextRound,
        currentPhase = DemoGamePhase.VERSTAERKEN,
        activePlayerId = nextPlayer.id,
        selectedRegionId = nextSelectedRegionId,
        cardsVisible = false,
    )
}
