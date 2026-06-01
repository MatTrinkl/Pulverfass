package at.aau.pulverfass.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.audio.BackgroundMusicManager
import at.aau.pulverfass.app.lobby.CharacterDef
import at.aau.pulverfass.app.lobby.Characters
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.lobby.LobbyPlayerUi
import at.aau.pulverfass.app.ui.components.GoldenGlowRing
import at.aau.pulverfass.app.ui.components.LobbyVideoBackground
import at.aau.pulverfass.app.ui.components.MainButton
import at.aau.pulverfass.app.ui.components.PulverfassTitleText
import at.aau.pulverfass.app.ui.components.VideoPlayer
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

    LaunchedEffect(Unit) {
        if (controller.state.value.characterId == null) {
            controller.updateCharacter(Characters.all.random().id)
        }
    }

    val players =
        buildWaitingRoomPlayers(
            players = state.players,
            ownPlayerId = ownPlayerId,
            selectedColor = selectedColor,
            selectedCharacterId = state.characterId,
            effectivePlayerName = effectivePlayerName,
            effectiveIsHost = effectiveIsHost,
        )

    var showCharacterPicker by remember { mutableStateOf(false) }
    var coinAnimCharacter by remember { mutableStateOf<CharacterDef?>(null) }

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
            Text(
                text = "SPIELER (${players.size}/6)",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
            )
        }

        // CENTER: Player list on lobbylist.png parchment
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.55f)
                    .fillMaxHeight(0.72f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.lobbylist),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.matchParentSize(),
                )
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                start = 64.dp,
                                end = 64.dp,
                                top = 48.dp,
                                bottom = 48.dp,
                            ),
                ) {
                    items(players) { player ->
                        PlayerRow(player = player)
                    }
                }
            }
        }

        // RIGHT OF CENTER: Character preview + picker button
        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CharacterPreview(characterDef = state.characterId?.let { Characters.byId(it) })
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

        val takenCharacterIds =
            state.players
                .filter { it.playerId != ownPlayerId }
                .mapNotNull { it.characterId }
                .toSet()

        WaitingRoomOverlays(
            overlayState =
                WaitingRoomOverlayState(
                    showCharacterPicker = showCharacterPicker,
                    coinAnimCharacter = coinAnimCharacter,
                    currentCharacterId = state.characterId ?: Characters.all[0].id,
                    playerCount = players.size,
                    takenCharacterIds = takenCharacterIds,
                    characterSelectError = state.characterSelectError,
                ),
            onDismiss = sfx(musicManager) { showCharacterPicker = false },
            onSave = { id ->
                musicManager?.playSfx(R.raw.sfx_karten)
                coinAnimCharacter = Characters.byId(id)
                controller.selectCharacter(id)
            },
            onSelect = { id -> controller.updateCharacter(id) },
            onCoinComplete = {
                coinAnimCharacter?.let { controller.updateCharacter(it.id) }
                showCharacterPicker = false
                coinAnimCharacter = null
            },
            onDismissError = { controller.clearCharacterSelectError() },
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

private fun buildWaitingRoomPlayers(
    players: List<LobbyPlayerUi>,
    ownPlayerId: PlayerId?,
    selectedColor: Color?,
    selectedCharacterId: String?,
    effectivePlayerName: String,
    effectiveIsHost: Boolean,
): List<WaitingRoomPlayerUi> =
    if (players.isEmpty()) {
        listOf(
            WaitingRoomPlayerUi(
                displayName = effectivePlayerName,
                isHost = effectiveIsHost,
                color = selectedColor ?: Characters.byIndex(0).color,
                characterId = selectedCharacterId,
            ),
        )
    } else {
        players.mapIndexed { index, player ->
            val isOwn = player.playerId == ownPlayerId
            val color =
                if (isOwn && selectedColor != null) {
                    selectedColor
                } else {
                    Characters.byIndex(index).color
                }
            WaitingRoomPlayerUi(
                displayName = player.displayName,
                isHost = player.isHost,
                isDisconnected = player.isDisconnected,
                color = color,
                characterId = if (isOwn) selectedCharacterId else player.characterId,
            )
        }
    }

@Composable
private fun CharacterPreview(characterDef: CharacterDef?) {
    Box(modifier = Modifier.size(115.dp), contentAlignment = Alignment.Center) {
        if (characterDef != null) {
            Image(
                painter = painterResource(id = characterDef.drawableRes),
                contentDescription = characterDef.displayName,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(86.dp)
                        .clip(CircleShape),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(115.dp)
                        .background(PulverfassColors.GoldDark, CircleShape),
            )
        }
    }
}

@Composable
private fun PlayerAvatar(
    characterId: String?,
    modifier: Modifier = Modifier,
) {
    val character = characterId?.let { id -> Characters.all.find { it.id == id } }
    Box(
        modifier =
            modifier
                .size(54.dp)
                .border(2.dp, PulverfassColors.GoldCoin, CircleShape)
                .clip(CircleShape)
                .background(PulverfassColors.SurfaceVoid),
        contentAlignment = Alignment.Center,
    ) {
        if (character != null) {
            Image(
                painter = painterResource(id = character.drawableRes),
                contentDescription = character.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
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
    val coinAnimCharacter: CharacterDef?,
    val currentCharacterId: String,
    val playerCount: Int,
    val takenCharacterIds: Set<String> = emptySet(),
    val characterSelectError: String? = null,
)

@Composable
private fun WaitingRoomOverlays(
    overlayState: WaitingRoomOverlayState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSelect: (String) -> Unit = {},
    onCoinComplete: () -> Unit,
    onDismissError: () -> Unit = {},
) {
    if (overlayState.showCharacterPicker && overlayState.coinAnimCharacter == null) {
        CharacterPickerOverlay(
            currentCharacterId = overlayState.currentCharacterId,
            takenCharacterIds = overlayState.takenCharacterIds,
            playerCount = overlayState.playerCount,
            onDismiss = onDismiss,
            onSave = onSave,
            onSelect = onSelect,
        )
    }
    if (overlayState.coinAnimCharacter != null) {
        CharacterCoinAnimation(
            characterDef = overlayState.coinAnimCharacter,
            onComplete = onCoinComplete,
        )
    }
    if (overlayState.characterSelectError != null) {
        CharacterSelectErrorDialog(
            message = overlayState.characterSelectError,
            onDismiss = onDismissError,
        )
    }
}

private data class WaitingRoomPlayerUi(
    val displayName: String,
    val isHost: Boolean,
    val isDisconnected: Boolean = false,
    val color: Color = PulverfassColors.PlayerRed,
    val characterId: String? = null,
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
        PlayerAvatar(characterId = player.characterId)
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
private fun CharacterPickerLabel(
    text: String,
    fontSize: TextUnit,
) {
    Text(
        text = text,
        fontFamily = PulverfassFonts.CinzelDecorative,
        fontSize = fontSize,
        color = PulverfassColors.TextOnDark,
        letterSpacing = 2.sp,
    )
}

@Composable
private fun CharacterCard(
    character: CharacterDef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(129.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = character.drawableRes),
            contentDescription = character.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(89.dp).clip(CircleShape),
        )
    }
}

@Composable
private fun CharacterSelectErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier.fillMaxSize().background(
                PulverfassColors.SurfaceVoid.copy(alpha = 0.75f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(0.75f)
                    .background(
                        PulverfassColors.SurfaceCard,
                        androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    )
                    .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = message,
                color = PulverfassColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            MainButton(text = "OK", onClick = onDismiss)
        }
    }
}

@Composable
private fun CharacterPickerOverlay(
    currentCharacterId: String,
    takenCharacterIds: Set<String> = emptySet(),
    playerCount: Int,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSelect: (String) -> Unit = {},
) {
    val initialIndex = Characters.all.indexOfFirst { it.id == currentCharacterId }.coerceAtLeast(0)
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapBehavior = rememberSnapFlingBehavior(lazyListState)
    val centerIndex by remember {
        derivedStateOf {
            val info = lazyListState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - center) }
                ?.index ?: initialIndex
        }
    }

    LaunchedEffect(centerIndex) {
        onSelect(Characters.byIndex(centerIndex).id)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(PulverfassColors.SurfaceVoid),
        contentAlignment = Alignment.Center,
    ) {
        VideoPlayer(
            videoResId = R.raw.wid,
            loop = true,
            cover = true,
            muted = true,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier.fillMaxSize().background(
                    PulverfassColors.SurfaceVoid.copy(alpha = 0.5f),
                ),
        )
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val itemSize = 129.dp
        val contentWidth = screenWidth - 32.dp
        val hPadding = (contentWidth - itemSize) / 2

        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(
                modifier =
                    Modifier.align(
                        Alignment.Center,
                    ).offset(y = 48.dp - screenHeight * 0.05f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PulverfassTitleText(
                    text = "CHARAKTER WÄHLEN",
                    fontSize = 28.sp,
                    letterSpacing = 3.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CharacterPickerLabel(text = "$playerCount / 6 SPIELER IM RAUM", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(24.dp + screenHeight * 0.03f))
                LazyRow(
                    state = lazyListState,
                    flingBehavior = snapBehavior,
                    contentPadding = PaddingValues(horizontal = hPadding),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.width(contentWidth).height(349.dp),
                ) {
                    itemsIndexed(Characters.all) { index, character ->
                        val distance = kotlin.math.abs(index - centerIndex)
                        val targetScale =
                            when (distance) {
                                0 -> 1.9f
                                1 -> 1.3f
                                2 -> 0.9f
                                3 -> 0.65f
                                else -> 0.5f
                            }
                        val targetAlpha =
                            when (distance) {
                                0 -> 1f
                                1 -> 0.85f
                                2 -> 0.55f
                                3 -> 0.3f
                                else -> 0.12f
                            }
                        val animScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                            label = "scale",
                        )
                        val animAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(200),
                            label = "alpha",
                        )
                        val isTaken = takenCharacterIds.contains(character.id)
                        Box {
                            CharacterCard(
                                character = character,
                                onClick = {},
                                modifier =
                                    Modifier.graphicsLayer {
                                        scaleX = animScale
                                        scaleY = animScale
                                        alpha = if (isTaken) animAlpha * 0.35f else animAlpha
                                    },
                            )
                            if (isTaken && distance == 0) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(89.dp)
                                            .align(Alignment.Center)
                                            .clip(CircleShape)
                                            .background(
                                                PulverfassColors.SurfaceVoid.copy(alpha = 0.55f),
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "✕",
                                        color = PulverfassColors.DangerBright,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = -screenHeight * 0.05f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MainButton(text = "ABBRECHEN", onClick = onDismiss)
                MainButton(
                    text = "SPEICHERN",
                    onClick = { onSave(Characters.byIndex(centerIndex).id) },
                )
            }
        }
    }
}

// Target X offset: screen center minus right-column center (screenWidth/2 - 84dp = 36dp padding + 48dp half-circle).
// Target Y offset: circle sits ~32dp above screen center due to button below it in the column.
@Composable
private fun CharacterCoinAnimation(
    characterDef: CharacterDef,
    onComplete: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
        val dXPx = screenWidthPx / 2f - with(density) { 84.dp.toPx() }
        val dYPx = with(density) { (-32).dp.toPx() }

        val overlayAlpha = remember { Animatable(1f) }
        val coinScale = remember { Animatable(0.25f) }
        val coinRotation = remember { Animatable(0f) }
        val coinOffsetX = remember { Animatable(0f) }
        val coinOffsetY = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            launch { overlayAlpha.animateTo(0f, tween(480)) }
            launch { coinScale.animateTo(2f, tween(400, easing = FastOutSlowInEasing)) }
            launch { coinRotation.animateTo(360f, tween(440)) }
            delay(430)

            launch { coinScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
            launch { coinRotation.animateTo(900f, tween(500)) }
            launch { coinOffsetX.animateTo(dXPx, tween(500, easing = FastOutSlowInEasing)) }
            launch { coinOffsetY.animateTo(dYPx, tween(500, easing = FastOutSlowInEasing)) }
            delay(540)

            onComplete()
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(PulverfassColors.SurfaceVoid.copy(alpha = overlayAlpha.value)),
        )

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
                    ),
            contentAlignment = Alignment.Center,
        ) {
            GoldenGlowRing(size = 96.dp)
            Image(
                painter = painterResource(id = characterDef.drawableRes),
                contentDescription = characterDef.displayName,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(3.dp, PulverfassColors.GoldBright, CircleShape),
            )
        }
    }
}
