package at.aau.pulverfass.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.audio.BackgroundMusicManager
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.lobby.LobbyPlayerUi
import at.aau.pulverfass.app.ui.components.LobbyVideoBackground
import at.aau.pulverfass.app.ui.components.MainButton
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.app.ui.theme.PulverfassFonts
import at.aau.pulverfass.shared.ids.PlayerId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WaitingRoomScreen(
    navController: NavController,
    controller: LobbyController,
    lobbyCode: String,
    isHost: Boolean,
    playerName: String,
    musicManager: BackgroundMusicManager? = null,
) {
    val state by controller.state.collectAsState()
    val effectivePlayerName = state.playerName.ifBlank { playerName }
    val effectiveIsHost = state.isHost || isHost
    val ownPlayerId = state.ownPlayerId
    val selectedColor = state.playerColor

    val takenColors = computeTakenColors(state.players, ownPlayerId)

    LaunchedEffect(Unit) {
        if (controller.state.value.playerColor == null) {
            val available = PulverfassColors.playerColors.take(6).filter { it !in takenColors }
            val random = available.randomOrNull() ?: PulverfassColors.playerColors[0]
            controller.updatePlayerColor(random)
        }
    }

    val players =
        buildWaitingRoomPlayers(
            players = state.players,
            ownPlayerId = ownPlayerId,
            selectedColor = selectedColor,
            effectivePlayerName = effectivePlayerName,
            effectiveIsHost = effectiveIsHost,
        )

    var showCharacterPicker by remember { mutableStateOf(false) }
    var coinAnimColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(state.gameStarted) {
        if (state.gameStarted) {
            navController.navigate(Screen.LoadGame.route)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PulverfassColors.SurfaceVoid),
    ) {
        LobbyVideoBackground()

        // TOP-LEFT: Host marker + Lobby code
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 32.dp, top = 28.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            if (effectiveIsHost) {
                Text(
                    text = "DU BIST DER HOST",
                    color = PulverfassColors.TextOnDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = "LOBBY: $lobbyCode",
                color = PulverfassColors.GoldBright,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
        }

        // CENTER: Player list on lobbylist.png parchment
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.55f)
                    .fillMaxHeight(0.72f),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.lobbylist),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize(),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = 64.dp,
                            end = 64.dp,
                            top = 48.dp,
                            bottom = 48.dp,
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "SPIELER (${players.size}/6)",
                    color = PulverfassColors.TextOnParchment,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    items(players) { player ->
                        PlayerRow(player = player)
                    }
                }
            }
        }

        // RIGHT OF CENTER: Color preview circle + character picker button
        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .background(
                            selectedColor ?: PulverfassColors.playerColors[0],
                            CircleShape,
                        )
                        .border(3.dp, PulverfassColors.GoldBright, CircleShape),
            )
            MainButton(
                text = "CHARAKTER\nWÄHLEN",
                onClick = sfx(musicManager) { showCharacterPicker = true },
                modifier = Modifier.testTag("character_picker_button"),
            )
        }

        // BOTTOM: Action buttons
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .fillMaxWidth(0.6f),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (effectiveIsHost) {
                val canStart = players.size >= 3
                MainButton(
                    text = "SPIEL STARTEN",
                    onClick = sfx(musicManager, controller::startGame),
                    enabled = canStart,
                    modifier = Modifier.weight(1f),
                )
            }
            MainButton(
                text = "LOBBY VERLASSEN",
                onClick =
                    sfx(musicManager) {
                        controller.leaveLobby()
                        navController.popBackStack()
                    },
                modifier = Modifier.weight(1f),
            )
        }

        WaitingRoomStatusOverlays(
            errorText = state.errorText,
            isHost = effectiveIsHost,
            playerCount = players.size,
        )

        WaitingRoomOverlays(
            overlayState =
                WaitingRoomOverlayState(
                    showCharacterPicker = showCharacterPicker,
                    coinAnimColor = coinAnimColor,
                    currentColor = selectedColor ?: PulverfassColors.playerColors[0],
                    takenColors = takenColors,
                    playerCount = players.size,
                ),
            onDismiss = sfx(musicManager) { showCharacterPicker = false },
            onSave = { color ->
                musicManager?.playSfx(R.raw.sfx_karten)
                coinAnimColor = color
            },
            onCoinComplete = {
                controller.updatePlayerColor(coinAnimColor!!)
                showCharacterPicker = false
                coinAnimColor = null
            },
        )
    }
}

private fun sfx(
    musicManager: BackgroundMusicManager?,
    action: () -> Unit,
): () -> Unit =
    {
        musicManager?.playSfx(R.raw.sfx_ingame)
        action()
    }

private fun computeTakenColors(
    players: List<LobbyPlayerUi>,
    ownPlayerId: PlayerId?,
): Set<Color> =
    players.mapIndexedNotNull { index, player ->
        if (player.playerId != ownPlayerId) {
            PulverfassColors.playerColors[index % PulverfassColors.playerColors.size]
        } else {
            null
        }
    }.toSet()

private fun buildWaitingRoomPlayers(
    players: List<LobbyPlayerUi>,
    ownPlayerId: PlayerId?,
    selectedColor: Color?,
    effectivePlayerName: String,
    effectiveIsHost: Boolean,
): List<WaitingRoomPlayerUi> =
    if (players.isEmpty()) {
        listOf(
            WaitingRoomPlayerUi(
                displayName = effectivePlayerName,
                isHost = effectiveIsHost,
                color = selectedColor ?: PulverfassColors.playerColors[0],
            ),
        )
    } else {
        players.mapIndexed { index, player ->
            val color =
                if (player.playerId == ownPlayerId && selectedColor != null) {
                    selectedColor
                } else {
                    PulverfassColors.playerColors[index % PulverfassColors.playerColors.size]
                }
            WaitingRoomPlayerUi(
                displayName = player.displayName,
                isHost = player.isHost,
                isDisconnected = player.isDisconnected,
                color = color,
            )
        }
    }

private fun resolveInitialColor(
    currentColor: Color,
    takenColors: Set<Color>,
    colors: List<Color>,
): Color =
    if (currentColor !in takenColors) {
        currentColor
    } else {
        colors.firstOrNull { it !in takenColors } ?: currentColor
    }

@Composable
private fun BoxScope.WaitingRoomStatusOverlays(
    errorText: String?,
    isHost: Boolean,
    playerCount: Int,
) {
    errorText?.let { error ->
        Text(
            text = "ERROR: $error".uppercase(),
            color = PulverfassColors.DangerBright,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 96.dp),
        )
    }
    if (isHost && playerCount < 3) {
        Text(
            text = "MIND. 3 SPIELER",
            color = PulverfassColors.DangerBright,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        )
    }
}

private data class WaitingRoomOverlayState(
    val showCharacterPicker: Boolean,
    val coinAnimColor: Color?,
    val currentColor: Color,
    val takenColors: Set<Color>,
    val playerCount: Int,
)

@Composable
private fun WaitingRoomOverlays(
    overlayState: WaitingRoomOverlayState,
    onDismiss: () -> Unit,
    onSave: (Color) -> Unit,
    onCoinComplete: () -> Unit,
) {
    if (overlayState.showCharacterPicker && overlayState.coinAnimColor == null) {
        CharacterPickerOverlay(
            currentColor = overlayState.currentColor,
            takenColors = overlayState.takenColors,
            playerCount = overlayState.playerCount,
            onDismiss = onDismiss,
            onSave = onSave,
        )
    }
    if (overlayState.coinAnimColor != null) {
        CoinAnimation(color = overlayState.coinAnimColor, onComplete = onCoinComplete)
    }
}

@Composable
private fun PlayerColorCircle(
    color: Color,
    isSelected: Boolean,
    isTaken: Boolean,
    onClick: () -> Unit,
) {
    val borderWidth = if (isSelected) 3.dp else 1.dp
    val borderColor = if (isSelected) PulverfassColors.GoldBright else PulverfassColors.GoldDark
    Box(
        modifier = Modifier.size(64.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(color, CircleShape)
                    .border(borderWidth, borderColor, CircleShape),
        )
        if (isTaken) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✕",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private data class WaitingRoomPlayerUi(
    val displayName: String,
    val isHost: Boolean,
    val isDisconnected: Boolean = false,
    val color: Color = PulverfassColors.PlayerRed,
)

@Composable
private fun PlayerRow(player: WaitingRoomPlayerUi) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(22.dp)
                    .background(player.color, CircleShape)
                    .border(1.dp, PulverfassColors.GoldDark, CircleShape),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = player.displayName.uppercase(),
            color = PulverfassColors.TextOnParchment,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        if (player.isHost) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(HOST)",
                color = PulverfassColors.GoldDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
        }
        if (player.isDisconnected) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(DISCONNECTED)",
                color = PulverfassColors.Danger,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun CharacterPickerOverlay(
    currentColor: Color,
    takenColors: Set<Color>,
    playerCount: Int,
    onDismiss: () -> Unit,
    onSave: (Color) -> Unit,
) {
    val colors = PulverfassColors.playerColors.take(6)
    var tempColor by remember {
        mutableStateOf(
            resolveInitialColor(currentColor, takenColors, colors),
        )
    }
    val isTakenSelected = tempColor in takenColors

    Box(
        modifier = Modifier.fillMaxSize().background(PulverfassColors.SurfaceVoid),
        contentAlignment = Alignment.Center,
    ) {
        LobbyVideoBackground()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "CHARAKTER WÄHLEN",
                fontFamily = PulverfassFonts.CinzelDecorative,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = PulverfassColors.GoldBright,
                letterSpacing = 3.sp,
            )

            Text(
                text = "$playerCount / 6 SPIELER IM RAUM",
                fontFamily = PulverfassFonts.CinzelDecorative,
                fontSize = 13.sp,
                color = PulverfassColors.TextOnDark,
                letterSpacing = 2.sp,
            )

            Text(
                text = "SPIELERFARBE",
                fontFamily = PulverfassFonts.CinzelDecorative,
                fontSize = 12.sp,
                color = PulverfassColors.TextOnDark,
                letterSpacing = 2.sp,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                colors.forEach { color ->
                    PlayerColorCircle(
                        color = color,
                        isSelected = color == tempColor,
                        isTaken = color in takenColors,
                        onClick = { tempColor = color },
                    )
                }
            }

            if (isTakenSelected) {
                Text(
                    text = "DIESE FARBE IST BEREITS VERGEBEN",
                    color = PulverfassColors.DangerBright,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                )
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MainButton(
                    text = "ABBRECHEN",
                    onClick = onDismiss,
                )
                MainButton(
                    text = "SPEICHERN",
                    onClick = { onSave(tempColor) },
                    enabled = !isTakenSelected,
                )
            }
        }
    }
}

// Coin-flip animation: circle flies from screen center to the preview spot (CenterEnd).
// Phase 1 — grows + spins + background fades.
// Phase 2 — flies to preview position and shrinks to final 96 dp size.
@Composable
private fun CoinAnimation(
    color: Color,
    onComplete: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current

        // Target: center of the 96 dp preview circle at CenterEnd, padding end=36 dp.
        // Circle center X  = screenWidth  - 36 dp - 48 dp = screenWidth - 84 dp
        // Column is vertically centred; button below circle adds ~(12+52) dp below it,
        // so the column's geometric centre sits ~32 dp below the circle's centre,
        // meaning the circle's centre is ~32 dp above the screen centre.
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val dXPx = screenWidthPx / 2f - with(density) { 84.dp.toPx() }
        val dYPx = with(density) { (-32).dp.toPx() }

        val overlayAlpha = remember { Animatable(1f) }
        val coinScale = remember { Animatable(0.25f) }
        val coinRotation = remember { Animatable(0f) }
        val coinOffsetX = remember { Animatable(0f) }
        val coinOffsetY = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            // Phase 1: grow big + spin + background fades away
            launch { overlayAlpha.animateTo(0f, tween(480)) }
            launch { coinScale.animateTo(2f, tween(400, easing = FastOutSlowInEasing)) }
            launch { coinRotation.animateTo(360f, tween(440)) }
            delay(430)

            // Phase 2: fly to preview circle, shrink to 96 dp size, keep spinning
            launch { coinScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
            launch { coinRotation.animateTo(900f, tween(500)) }
            launch { coinOffsetX.animateTo(dXPx, tween(500, easing = FastOutSlowInEasing)) }
            launch { coinOffsetY.animateTo(dYPx, tween(500, easing = FastOutSlowInEasing)) }
            delay(540)

            onComplete()
        }

        // Fading dark background (matches overlay colour so the cut is seamless)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        PulverfassColors.SurfaceVoid.copy(alpha = overlayAlpha.value),
                    ),
        )

        // The coin itself — centred on screen, then translated to its target
        Box(
            modifier =
                Modifier
                    .size(96.dp)
                    .align(Alignment.Center)
                    .graphicsLayer(
                        scaleX = coinScale.value,
                        scaleY = coinScale.value,
                        rotationY = coinRotation.value,
                        translationX = coinOffsetX.value,
                        translationY = coinOffsetY.value,
                        cameraDistance = 8f * density.density,
                    )
                    .background(color, CircleShape)
                    .border(3.dp, PulverfassColors.GoldBright, CircleShape),
        )
    }
}
