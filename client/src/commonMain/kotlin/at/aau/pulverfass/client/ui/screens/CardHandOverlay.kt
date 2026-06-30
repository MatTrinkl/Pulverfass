package at.aau.pulverfass.client.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import at.aau.pulverfass.client.audio.BackgroundMusicManager
import at.aau.pulverfass.client.audio.SfxSound
import at.aau.pulverfass.client.game.PrivateHandCardUi
import at.aau.pulverfass.client.resources.Res
import at.aau.pulverfass.client.resources.game_card_cannon
import at.aau.pulverfass.client.resources.game_card_emperor
import at.aau.pulverfass.client.resources.game_card_galleon
import at.aau.pulverfass.client.resources.game_card_pirate
import at.aau.pulverfass.client.resources.game_card_type_a
import at.aau.pulverfass.client.resources.game_card_type_b
import at.aau.pulverfass.client.resources.game_card_type_c
import at.aau.pulverfass.client.resources.game_card_type_joker
import at.aau.pulverfass.client.resources.game_cards_close
import at.aau.pulverfass.client.resources.game_cards_empty
import at.aau.pulverfass.client.resources.game_cards_hint_locked
import at.aau.pulverfass.client.resources.game_cards_hint_select
import at.aau.pulverfass.client.resources.game_cards_selected_count
import at.aau.pulverfass.client.resources.game_cards_title
import at.aau.pulverfass.client.resources.game_cards_trade_in
import at.aau.pulverfass.client.ui.components.MainButton
import at.aau.pulverfass.client.ui.components.PulverfassTitleText
import at.aau.pulverfass.client.ui.components.VideoAsset
import at.aau.pulverfass.client.ui.components.VideoPlayer
import at.aau.pulverfass.client.ui.theme.PulverfassColors
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.lobby.state.CardType
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private fun cardDrawableFor(type: CardType): DrawableResource =
    when (type) {
        CardType.A -> Res.drawable.game_card_galleon
        CardType.B -> Res.drawable.game_card_cannon
        CardType.C -> Res.drawable.game_card_pirate
        CardType.JOKER -> Res.drawable.game_card_emperor
    }

private const val FAN_PIVOT_Y = 2.4f
private const val FAN_MAX_ANGLE_PER_CARD = 11f
private const val FAN_TOTAL_ANGLE = 60f
private const val SELECTED_SCALE = 1.06f
private const val CARD_OFFSCREEN_FRACTION = 0.06f
private const val CARD_ASPECT_RATIO = 4f / 5f

@Composable
internal fun CardHandOverlay(
    state: PrivateHandPanelState,
    actions: PrivateHandPanelActions,
    isVisible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    musicManager: BackgroundMusicManager? = null,
) {
    if (!isVisible) {
        return
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(PulverfassColors.SurfaceVoid)
                .testTag("card_hand_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        VideoPlayer(
            asset = VideoAsset.CARD_HAND_BACKGROUND,
            loop = true,
            cover = true,
            muted = true,
            modifier = Modifier.fillMaxSize(),
        )
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
                    }
                    .background(PulverfassColors.SurfaceVoid.copy(alpha = 0.46f)),
        )

        CardHandHeader(
            state = state,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        CardHandBody(
            state = state,
            actions = actions,
            musicManager = musicManager,
        )

        CardHandActions(
            state = state,
            actions = actions,
            onClose = onClose,
            musicManager = musicManager,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun CardHandHeader(
    state: PrivateHandPanelState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PulverfassTitleText(
            text = stringResource(Res.string.game_cards_title),
            fontSize = 28.sp,
            letterSpacing = 3.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = state.playerName,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            color = PulverfassColors.TextOnDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
        if (state.privateHandCards.isNotEmpty()) {
            CardHandHint(canSelect = state.showTradeControls && state.canSelectTradeCards)
        }
    }
}

@Composable
private fun CardHandHint(canSelect: Boolean) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text =
            stringResource(
                if (canSelect) {
                    Res.string.game_cards_hint_select
                } else {
                    Res.string.game_cards_hint_locked
                },
            ),
        color = if (canSelect) PulverfassColors.GoldBright else PulverfassColors.TextOnDark,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("card_hand_hint"),
    )
}

@Composable
private fun BoxScope.CardHandBody(
    state: PrivateHandPanelState,
    actions: PrivateHandPanelActions,
    musicManager: BackgroundMusicManager?,
) {
    if (state.privateHandCards.isEmpty()) {
        Text(
            text = stringResource(Res.string.game_cards_empty),
            color = PulverfassColors.TextOnDark,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.Center),
        )
    } else {
        FannedHand(
            cards = state.privateHandCards,
            selectedCardIds = state.selectedTradeInCardIds,
            selectable = state.showTradeControls && state.canSelectTradeCards,
            onToggle = { cardId ->
                musicManager?.playSfx(SfxSound.CARD_SELECT)
                actions.onToggleTradeInCard(cardId)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun CardHandActions(
    state: PrivateHandPanelState,
    actions: PrivateHandPanelActions,
    onClose: () -> Unit,
    musicManager: BackgroundMusicManager?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (state.showTradeControls && state.privateHandCards.isNotEmpty()) {
            Text(
                text =
                    stringResource(
                        Res.string.game_cards_selected_count,
                        state.selectedTradeInCardIds.size.coerceAtMost(3),
                    ),
                modifier = Modifier.width(210.dp),
                color = PulverfassColors.TextOnDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            MainButton(
                text = stringResource(Res.string.game_cards_trade_in),
                enabled = state.canTradeInCards,
                onClick = {
                    musicManager?.playSfx(SfxSound.CARD_SELECT_ALT)
                    actions.onTradeInCards()
                },
                modifier = Modifier.width(210.dp).testTag("trade_in_cards_button"),
            )
        }
        MainButton(
            text = stringResource(Res.string.game_cards_close),
            onClick = {
                musicManager?.playSfx(SfxSound.UI_CLICK)
                onClose()
            },
            modifier = Modifier.width(210.dp).testTag("close_cards_button"),
        )
    }
}

@Composable
private fun FannedHand(
    cards: List<PrivateHandCardUi>,
    selectedCardIds: Set<CardId>,
    selectable: Boolean,
    onToggle: (CardId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val cardHeight = minOf(280.dp, maxHeight * 0.72f).coerceAtLeast(140.dp)
        val cardWidth = cardHeight * CARD_ASPECT_RATIO
        val liftPx = with(density) { 30.dp.toPx() }
        val offscreenOffset = cardHeight * CARD_OFFSCREEN_FRACTION
        val lastIndex = (cards.size - 1).coerceAtLeast(1)
        val anglePerCard = minOf(FAN_MAX_ANGLE_PER_CARD, FAN_TOTAL_ANGLE / lastIndex)
        val centerIndex = (cards.size - 1) / 2f

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(cardHeight * 1.5f)
                    .offset(y = offscreenOffset),
            contentAlignment = Alignment.BottomCenter,
        ) {
            cards.forEachIndexed { index, card ->
                val isSelected = card.cardId in selectedCardIds
                val angle = (index - centerIndex) * anglePerCard
                val cardLabel = cardDisplayLabel(card)
                Box(
                    modifier =
                        Modifier
                            .height(cardHeight)
                            .width(cardWidth)
                            .zIndex(if (isSelected) 1f else 0f)
                            .graphicsLayer {
                                rotationZ = angle
                                transformOrigin = TransformOrigin(0.5f, FAN_PIVOT_Y)
                                translationY = if (isSelected) -liftPx else 0f
                                val scale = if (isSelected) SELECTED_SCALE else 1f
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = PulverfassColors.GoldBright,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(
                                enabled = selectable,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onToggle(card.cardId) }
                            .testTag("card_hand_card_${card.cardId.value}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(cardDrawableFor(card.type)),
                        contentDescription = cardLabel,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun cardDisplayLabel(card: PrivateHandCardUi): String {
    return cardTypeLabel(card.type)
}

@Composable
private fun cardTypeLabel(type: CardType): String =
    when (type) {
        CardType.A -> stringResource(Res.string.game_card_type_a)
        CardType.B -> stringResource(Res.string.game_card_type_b)
        CardType.C -> stringResource(Res.string.game_card_type_c)
        CardType.JOKER -> stringResource(Res.string.game_card_type_joker)
    }

