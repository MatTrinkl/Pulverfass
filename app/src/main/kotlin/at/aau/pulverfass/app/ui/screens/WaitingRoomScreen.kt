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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.audio.BackgroundMusicManager
import at.aau.pulverfass.app.lobby.CharacterDefinition
import at.aau.pulverfass.app.lobby.Characters
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.lobby.LobbyPlayerUi
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

private const val CHARACTER_COIN_TARGET_SCALE = 1f

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
    var submittedInitialCharacterId by remember(
        lobbyCode,
        ownPlayerId,
    ) {
        mutableStateOf<String?>(null)
    }

    SubmitInitialCharacterEffect(
        activeLobbyCode = state.activeLobbyCode,
        players = state.players,
        ownPlayerId = ownPlayerId,
        currentCharacterId = state.characterId,
        submittedInitialCharacterId = submittedInitialCharacterId,
        onSubmittedInitialCharacterIdChange = { submittedInitialCharacterId = it },
        onSyncCharacter = { id ->
            controller.updateCharacter(id)
            controller.selectCharacter(id)
        },
    )

    val players =
        buildWaitingRoomPlayers(
            players = state.players,
            ownPlayerId = ownPlayerId,
            selectedCharacterId = state.characterId,
            effectivePlayerName = effectivePlayerName,
            effectiveIsHost = effectiveIsHost,
        )

    var showCharacterPicker by remember { mutableStateOf(false) }
    var coinAnimCharacter by remember { mutableStateOf<CharacterDefinition?>(null) }
    var screenSizePx by remember { mutableStateOf(IntSize.Zero) }
    var characterPreviewCenterPx by remember { mutableStateOf<Offset?>(null) }
    val characterPreviewTargetOffsetPx =
        rememberCharacterPreviewTargetOffset(
            screenSizePx = screenSizePx,
            characterPreviewCenterPx = characterPreviewCenterPx,
        )

    WaitingRoomMusicEffect(showCharacterPicker = showCharacterPicker, musicManager = musicManager)
    NavigateToStartedGameEffect(gameStarted = state.gameStarted, navController = navController)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { screenSizePx = it }
                .background(PulverfassColors.SurfaceVoid),
    ) {
        LobbyVideoBackground()

        WaitingRoomHeader(
            lobbyCode = lobbyCode,
            isHost = effectiveIsHost,
            playerCount = players.size,
        )

        WaitingRoomPlayerList(players = players)

        WaitingRoomCharacterEntry(
            characterId = state.characterId,
            hideCharacterIcon = coinAnimCharacter != null,
            onPreviewCenterChange = { characterPreviewCenterPx = it },
            onOpenPicker = sfx(musicManager) { showCharacterPicker = true },
        )

        WaitingRoomActions(
            isHost = effectiveIsHost,
            playerCount = players.size,
            onStartGame = sfx(musicManager, controller::startGame),
            onLeaveLobby =
                sfx(musicManager) {
                    controller.leaveLobby()
                    navController.popBackStack()
                },
        )

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
                    characterPreviewTargetOffsetPx = characterPreviewTargetOffsetPx,
                ),
            onDismiss = sfx(musicManager) { showCharacterPicker = false },
            onSave = { id ->
                musicManager?.playSfx(R.raw.sfx_card_select)
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

@Composable
private fun SubmitInitialCharacterEffect(
    activeLobbyCode: String?,
    players: List<LobbyPlayerUi>,
    ownPlayerId: PlayerId?,
    currentCharacterId: String?,
    submittedInitialCharacterId: String?,
    onSubmittedInitialCharacterIdChange: (String) -> Unit,
    onSyncCharacter: (String) -> Unit,
) {
    LaunchedEffect(
        activeLobbyCode,
        ownPlayerId,
        currentCharacterId,
        players,
        submittedInitialCharacterId,
    ) {
        val initialCharacterId =
            initialCharacterIdForLobby(
                players = players,
                ownPlayerId = ownPlayerId,
                currentCharacterId = currentCharacterId,
                submittedInitialCharacterId = submittedInitialCharacterId,
            )
        if (initialCharacterId != null) {
            onSubmittedInitialCharacterIdChange(initialCharacterId)
            onSyncCharacter(initialCharacterId)
        }
    }
}

@Composable
private fun rememberCharacterPreviewTargetOffset(
    screenSizePx: IntSize,
    characterPreviewCenterPx: Offset?,
): Offset? =
    remember(screenSizePx, characterPreviewCenterPx) {
        characterPreviewTargetOffset(
            screenSizePx = screenSizePx,
            characterPreviewCenterPx = characterPreviewCenterPx,
        )
    }

private fun characterPreviewTargetOffset(
    screenSizePx: IntSize,
    characterPreviewCenterPx: Offset?,
): Offset? {
    if (screenSizePx.width == 0 || screenSizePx.height == 0) {
        return null
    }
    val previewCenter = characterPreviewCenterPx ?: return null
    val screenCenter =
        Offset(
            x = screenSizePx.width / 2f,
            y = screenSizePx.height / 2f,
        )
    return previewCenter - screenCenter
}

@Composable
private fun WaitingRoomMusicEffect(
    showCharacterPicker: Boolean,
    musicManager: BackgroundMusicManager?,
) {
    LaunchedEffect(showCharacterPicker) {
        val track =
            if (showCharacterPicker) {
                R.raw.music_character_picker
            } else {
                R.raw.music_lobby_waiting
            }
        musicManager?.play(track)
    }
}

@Composable
private fun NavigateToStartedGameEffect(
    gameStarted: Boolean,
    navController: NavController,
) {
    LaunchedEffect(gameStarted) {
        if (gameStarted) {
            navController.navigate(Screen.LoadGame.route)
        }
    }
}

@Composable
private fun BoxScope.WaitingRoomHeader(
    lobbyCode: String,
    isHost: Boolean,
    playerCount: Int,
) {
    Column(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 32.dp, top = 28.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        if (isHost) {
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
            text = "SPIELER ($playerCount/6)",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
private fun BoxScope.WaitingRoomPlayerList(players: List<WaitingRoomPlayerUi>) {
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
                painter = painterResource(id = R.drawable.ui_lobby_roster_panel),
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
}

@Composable
private fun BoxScope.WaitingRoomCharacterEntry(
    characterId: String?,
    hideCharacterIcon: Boolean,
    onPreviewCenterChange: (Offset) -> Unit,
    onOpenPicker: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CharacterPreview(
            characterDef = characterId?.let { Characters.byId(it) },
            hideCharacterIcon = hideCharacterIcon,
            modifier =
                Modifier.onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    onPreviewCenterChange(
                        Offset(
                            x = position.x + coordinates.size.width / 2f,
                            y = position.y + coordinates.size.height / 2f,
                        ),
                    )
                },
        )
        MainButton(
            text = "CHARAKTER\nWÄHLEN",
            onClick = onOpenPicker,
            modifier = Modifier.testTag("character_picker_button"),
        )
    }
}

@Composable
private fun BoxScope.WaitingRoomActions(
    isHost: Boolean,
    playerCount: Int,
    onStartGame: () -> Unit,
    onLeaveLobby: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 32.dp)
                .width(220.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (isHost) {
            MainButton(
                text = "SPIEL STARTEN",
                onClick = onStartGame,
                enabled = playerCount >= 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MainButton(
            text = "LOBBY VERLASSEN",
            onClick = onLeaveLobby,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun sfx(
    musicManager: BackgroundMusicManager?,
    action: () -> Unit,
): () -> Unit =
    {
        musicManager?.playSfx(R.raw.sfx_ui_click)
        action()
    }

private fun buildWaitingRoomPlayers(
    players: List<LobbyPlayerUi>,
    ownPlayerId: PlayerId?,
    selectedCharacterId: String?,
    effectivePlayerName: String,
    effectiveIsHost: Boolean,
): List<WaitingRoomPlayerUi> =
    if (players.isEmpty()) {
        listOf(
            WaitingRoomPlayerUi(
                displayName = effectivePlayerName,
                isHost = effectiveIsHost,
                color = playerColorAt(0),
                characterId = selectedCharacterId,
            ),
        )
    } else {
        val colorsByPlayerId = playerColorsById(players)
        players.map { player ->
            val isOwn = player.playerId == ownPlayerId
            WaitingRoomPlayerUi(
                displayName = player.displayName,
                isHost = player.isHost,
                isDisconnected = player.isDisconnected,
                color = colorsByPlayerId.getValue(player.playerId),
                characterId = if (isOwn) selectedCharacterId else player.characterId,
            )
        }
    }

internal fun initialCharacterIdForLobby(
    players: List<LobbyPlayerUi>,
    ownPlayerId: PlayerId?,
    currentCharacterId: String?,
    submittedInitialCharacterId: String?,
): String? {
    if (ownPlayerId == null || submittedInitialCharacterId != null) {
        return null
    }

    val ownSyncedCharacterId = players.firstOrNull { it.playerId == ownPlayerId }?.characterId
    if (ownSyncedCharacterId != null) {
        return null
    }

    val takenCharacterIds =
        players
            .filter { it.playerId != ownPlayerId }
            .mapNotNull { it.characterId }
            .toSet()
    val currentCharacter =
        currentCharacterId?.takeIf { characterId ->
            Characters.byId(characterId) != null && characterId !in takenCharacterIds
        }

    return currentCharacter ?: Characters.all.firstOrNull { it.id !in takenCharacterIds }?.id
}

internal fun availableCharacterIndexNear(
    centerIndex: Int,
    takenCharacterIds: Set<String>,
): Int? {
    val lastIndex = Characters.all.lastIndex
    if (lastIndex < 0) {
        return null
    }
    val safeCenterIndex = centerIndex.coerceIn(0, lastIndex)
    if (Characters.all[safeCenterIndex].id !in takenCharacterIds) {
        return safeCenterIndex
    }

    for (offset in 1..lastIndex) {
        val nextIndex = safeCenterIndex + offset
        if (nextIndex <= lastIndex && Characters.all[nextIndex].id !in takenCharacterIds) {
            return nextIndex
        }

        val previousIndex = safeCenterIndex - offset
        if (previousIndex >= 0 && Characters.all[previousIndex].id !in takenCharacterIds) {
            return previousIndex
        }
    }

    return null
}

private fun playerColorAt(index: Int): Color =
    PulverfassColors.playerColors[index % PulverfassColors.playerColors.size]

private fun playerColorsById(players: List<LobbyPlayerUi>): Map<PlayerId, Color> =
    players
        .sortedBy { it.playerId.value }
        .mapIndexed { index, player -> player.playerId to playerColorAt(index) }
        .toMap()

@Composable
private fun CharacterPreview(
    characterDef: CharacterDefinition?,
    modifier: Modifier = Modifier,
    hideCharacterIcon: Boolean = false,
) {
    Box(modifier = modifier.size(115.dp), contentAlignment = Alignment.Center) {
        if (characterDef != null) {
            Box(
                modifier =
                    Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(PulverfassColors.SurfaceVoid.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                if (!hideCharacterIcon) {
                    Image(
                        painter = painterResource(id = characterDef.drawableRes),
                        contentDescription = characterDef.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(100.dp),
                    )
                }
            }
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
    color: Color = PulverfassColors.GoldCoin,
    modifier: Modifier = Modifier,
) {
    val character = characterId?.let { id -> Characters.all.find { it.id == id } }
    Box(
        modifier =
            modifier
                .size(54.dp)
                .border(2.dp, color, CircleShape)
                .clip(CircleShape)
                .background(PulverfassColors.SurfaceVoid),
        contentAlignment = Alignment.Center,
    ) {
        if (character != null) {
            Image(
                painter = painterResource(id = character.drawableRes),
                contentDescription = character.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(62.dp),
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
    val coinAnimCharacter: CharacterDefinition?,
    val currentCharacterId: String,
    val playerCount: Int,
    val takenCharacterIds: Set<String> = emptySet(),
    val characterSelectError: String? = null,
    val characterPreviewTargetOffsetPx: Offset? = null,
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
            targetOffsetPx = overlayState.characterPreviewTargetOffsetPx,
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
        PlayerAvatar(characterId = player.characterId, color = player.color)
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
    character: CharacterDefinition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .size(129.dp)
                .then(
                    if (enabled) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(89.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = character.drawableRes),
                contentDescription = character.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(103.dp),
            )
        }
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

    val selectedCharacterId = Characters.byIndex(centerIndex).id
    val selectedCharacterTaken = selectedCharacterId in takenCharacterIds

    LaunchedEffect(centerIndex, takenCharacterIds) {
        val availableIndex = availableCharacterIndexNear(centerIndex, takenCharacterIds)
        when {
            availableIndex == null -> Unit
            availableIndex != centerIndex -> lazyListState.animateScrollToItem(availableIndex)
            else -> onSelect(Characters.all[availableIndex].id)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(PulverfassColors.SurfaceVoid),
        contentAlignment = Alignment.Center,
    ) {
        VideoPlayer(
            videoResId = R.raw.video_character_picker_background,
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

        val lazyRowHeight = minOf(349.dp, screenHeight * 0.50f)
        val buttonRowHeight = 56.dp
        val bottomSafeOffset = screenHeight * 0.05f
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(bottom = buttonRowHeight + bottomSafeOffset)
                        .offset(
                            y = -(buttonRowHeight + bottomSafeOffset) / 2 + screenHeight * 0.2f,
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PulverfassTitleText(
                    text = "CHARAKTER WÄHLEN",
                    fontSize = 28.sp,
                    letterSpacing = 3.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CharacterPickerLabel(text = "$playerCount / 6 SPIELER IM RAUM", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(16.dp + screenHeight * 0.02f))
                LazyRow(
                    state = lazyListState,
                    flingBehavior = snapBehavior,
                    contentPadding = PaddingValues(horizontal = hPadding),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.width(contentWidth).height(lazyRowHeight),
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
                        CharacterCard(
                            character = character,
                            onClick = {},
                            enabled = !isTaken,
                            modifier =
                                Modifier.graphicsLayer {
                                    scaleX = animScale
                                    scaleY = animScale
                                    alpha = if (isTaken) animAlpha * 0.28f else animAlpha
                                },
                        )
                    }
                }
            }

            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -bottomSafeOffset),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MainButton(text = "ABBRECHEN", onClick = onDismiss)
                MainButton(
                    text = "SPEICHERN",
                    onClick = { onSave(selectedCharacterId) },
                    enabled = !selectedCharacterTaken,
                )
            }
        }
    }
}

@Composable
private fun CharacterCoinAnimation(
    characterDef: CharacterDefinition,
    targetOffsetPx: Offset?,
    onComplete: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
        val targetOffset =
            targetOffsetPx
                ?: Offset(
                    x = screenWidthPx / 2f - with(density) { 84.dp.toPx() },
                    y = with(density) { (-32).dp.toPx() },
                )

        val overlayAlpha = remember { Animatable(1f) }
        val coinScale = remember { Animatable(0.25f) }
        val coinRotation = remember { Animatable(0f) }
        val coinOffsetX = remember { Animatable(0f) }
        val coinOffsetY = remember { Animatable(0f) }
        val coinAlpha = remember { Animatable(1f) }

        LaunchedEffect(Unit) {
            launch { overlayAlpha.animateTo(0f, tween(480)) }
            launch { coinScale.animateTo(2f, tween(400, easing = FastOutSlowInEasing)) }
            launch { coinRotation.animateTo(360f, tween(440)) }
            delay(430)

            launch {
                coinScale.animateTo(
                    CHARACTER_COIN_TARGET_SCALE,
                    tween(520, easing = FastOutSlowInEasing),
                )
            }
            launch { coinRotation.animateTo(720f, tween(520)) }
            launch {
                coinOffsetX.animateTo(
                    targetOffset.x,
                    tween(520, easing = FastOutSlowInEasing),
                )
            }
            launch {
                coinOffsetY.animateTo(
                    targetOffset.y,
                    tween(520, easing = FastOutSlowInEasing),
                )
            }
            delay(520)
            launch { coinAlpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing)) }
            delay(240)

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
                    .align(Alignment.Center)
                    .graphicsLayer(
                        scaleX = coinScale.value,
                        scaleY = coinScale.value,
                        rotationY = coinRotation.value,
                        translationX = coinOffsetX.value,
                        translationY = coinOffsetY.value,
                        cameraDistance = 8f * density.density,
                        alpha = coinAlpha.value,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(PulverfassColors.SurfaceVoid.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = characterDef.drawableRes),
                    contentDescription = characterDef.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(100.dp),
                )
            }
        }
    }
}
