package at.aau.pulverfass.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import at.aau.pulverfass.app.R
import at.aau.pulverfass.app.audio.BackgroundMusicManager
import at.aau.pulverfass.app.game.PrivateHandCardUi
import at.aau.pulverfass.app.ui.components.MainButton
import at.aau.pulverfass.app.ui.components.PulverfassTitleText
import at.aau.pulverfass.app.ui.components.VideoPlayer
import at.aau.pulverfass.app.ui.theme.PulverfassColors
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.lobby.state.CardType

/**
 * Bildet einen Kartentyp auf das zugehörige Karten-Asset ab.
 */
@DrawableRes
private fun cardDrawableFor(type: CardType): Int =
    when (type) {
        CardType.A -> R.drawable.game_card_galleon
        CardType.B -> R.drawable.game_card_cannon
        CardType.C -> R.drawable.game_card_pirate
        CardType.JOKER -> R.drawable.game_card_emperor
    }

/**
 * Fächer-Geometrie: Alle Karten liegen am unteren Rand zentriert übereinander und
 * werden um einen gemeinsamen, weit unter den Karten liegenden Drehpunkt rotiert.
 * Dadurch spreizen sie sich wie eine echte Spielhand auf. Der Drehpunkt wird über
 * eine TransformOrigin-Y-Fraktion deutlich unterhalb der Kartenhöhe gelegt.
 */
private const val FAN_PIVOT_Y = 2.4f
private const val FAN_MAX_ANGLE_PER_CARD = 11f
private const val FAN_TOTAL_ANGLE = 60f
private const val SELECTED_SCALE = 1.06f

/** Anteil der Kartenhöhe, um den der Fächer unter den Screenrand rutscht. */
private const val CARD_OFFSCREEN_FRACTION = 0.22f

/**
 * Vollbild-Kartenscreen im Stil der Charakterauswahl.
 *
 * Statt eines kleinen Seiten-Popups zeigt das Overlay die eigene Hand als
 * aufgefächerte Kartenbilder über einem Video-Hintergrund. Angetippte Karten
 * heben sich heraus und können in der Verstärkungsphase als Dreierset
 * eingetauscht werden. Auswahl- und Trade-in-Logik bleiben identisch zur
 * bisherigen Seitenleiste -- nur die Darstellung ist neu.
 *
 * @param state Anzeige- und Interaktionszustand der privaten Kartenhand.
 * @param actions Callbacks für Kartenauswahl und Trade-in.
 * @param isVisible steuert, ob der Screen überhaupt gerendert wird.
 * @param onClose schließt den Kartenscreen (entspricht dem Karten-Toggle).
 * @param musicManager optionaler Audio-Manager für Klick-Sounds.
 */
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
            videoResId = R.raw.video_card_hand_background,
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

        // Titel oben mittig.
        CardHandHeader(
            state = state,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        CardHandBody(
            state = state,
            actions = actions,
            musicManager = musicManager,
        )

        // Aktions-Buttons rechts, vertikal gestapelt -- abseits des Karten-Fächers.
        CardHandActions(
            state = state,
            actions = actions,
            onClose = onClose,
            musicManager = musicManager,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/** Titel, Spielername und kontextueller Auswahl-Hinweis am oberen Rand. */
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
            text = stringResource(id = R.string.game_cards_title),
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

/** Hinweistext, der Auswahl/Trade-in als erlaubt (Gold) oder gesperrt anzeigt. */
@Composable
private fun CardHandHint(canSelect: Boolean) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text =
            stringResource(
                id =
                    if (canSelect) {
                        R.string.game_cards_hint_select
                    } else {
                        R.string.game_cards_hint_locked
                    },
            ),
        color = if (canSelect) PulverfassColors.GoldBright else PulverfassColors.TextOnDark,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("card_hand_hint"),
    )
}

/**
 * Kartenhand als POV-Fächer: am unteren Rand verankert und bewusst ein
 * Stück aus dem Screen geschoben, damit es wirkt, als hielte man die
 * Karten selbst in der Hand. Die Typ-Labels sitzen oben auf der Karte,
 * damit sie auch bei abgeschnittener Unterkante lesbar bleiben.
 */
@Composable
private fun BoxScope.CardHandBody(
    state: PrivateHandPanelState,
    actions: PrivateHandPanelActions,
    musicManager: BackgroundMusicManager?,
) {
    if (state.privateHandCards.isEmpty()) {
        Text(
            text = stringResource(id = R.string.game_cards_empty),
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
                musicManager?.playSfx(R.raw.sfx_card_select)
                actions.onToggleTradeInCard(cardId)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Trade-in- und Schließen-Buttons rechts, vertikal gestapelt. */
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
                        id = R.string.game_cards_selected_count,
                        state.selectedTradeInCardIds.size.coerceAtMost(3),
                    ),
                modifier = Modifier.width(210.dp),
                color = PulverfassColors.TextOnDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            MainButton(
                text = stringResource(id = R.string.game_cards_trade_in),
                enabled = state.canTradeInCards,
                onClick = {
                    musicManager?.playSfx(R.raw.sfx_card_select)
                    actions.onTradeInCards()
                },
                modifier = Modifier.width(210.dp).testTag("trade_in_cards_button"),
            )
        }
        MainButton(
            text = stringResource(id = R.string.game_cards_close),
            onClick = {
                musicManager?.playSfx(R.raw.sfx_ui_click)
                onClose()
            },
            modifier = Modifier.width(210.dp).testTag("close_cards_button"),
        )
    }
}

/**
 * Rendert die Karten als überlappenden Fächer.
 *
 * Jede Karte wird um einen gemeinsamen Drehpunkt unterhalb der Hand rotiert, was
 * die typische aufgefächerte Anordnung erzeugt. Die ausgewählte Karte wird nach
 * oben gehoben, leicht vergrößert und mit einem Goldrand markiert.
 */
@Composable
private fun FannedHand(
    cards: List<PrivateHandCardUi>,
    selectedCardIds: Set<CardId>,
    selectable: Boolean,
    onToggle: (CardId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val cardHeight =
        minOf(280.dp, (configuration.screenHeightDp * 0.72f).dp).coerceAtLeast(140.dp)
    val cardWidth = cardHeight * CARD_ASPECT_RATIO
    val liftPx = with(density) { 30.dp.toPx() }
    // Karten ein Stück unter den Screenrand schieben (POV-Hand-Gefühl).
    val offscreenOffset = cardHeight * CARD_OFFSCREEN_FRACTION

    val lastIndex = (cards.size - 1).coerceAtLeast(1)
    val anglePerCard = minOf(FAN_MAX_ANGLE_PER_CARD, FAN_TOTAL_ANGLE / lastIndex)
    val centerIndex = (cards.size - 1) / 2f

    Box(
        modifier =
            modifier
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
                contentAlignment = Alignment.TopCenter,
            ) {
                Image(
                    painter = painterResource(id = cardDrawableFor(card.type)),
                    contentDescription = cardLabel,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = cardLabel,
                    color = PulverfassColors.TextOnDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(PulverfassColors.SurfaceVoid.copy(alpha = 0.42f))
                            .padding(vertical = 3.dp),
                )
            }
        }
    }
}

/**
 * Baut das sichtbare Kartenlabel aus Kartentyp und optionalem Gebietsnamen.
 *
 * @param card Private Karteninformation aus dem Server-State.
 * @return Piratisch benanntes Kartenlabel, bei Gebietskarten inklusive Gebiet.
 */
@Composable
private fun cardDisplayLabel(card: PrivateHandCardUi): String {
    val typeLabel = cardTypeLabel(card.type)
    val territoryName = card.cardId.territoryDisplayName()
    return if (territoryName == null || card.type == CardType.JOKER) {
        typeLabel
    } else {
        "$typeLabel $territoryName"
    }
}

@Composable
private fun cardTypeLabel(type: CardType): String =
    when (type) {
        CardType.A -> stringResource(id = R.string.game_card_type_a)
        CardType.B -> stringResource(id = R.string.game_card_type_b)
        CardType.C -> stringResource(id = R.string.game_card_type_c)
        CardType.JOKER -> stringResource(id = R.string.game_card_type_joker)
    }

private fun CardId.territoryDisplayName(): String? {
    val parts = value.split(":")
    if (parts.size != 3 || parts[0] != "territory") {
        return null
    }
    return territoryDisplayNames[parts[1]] ?: parts[1].fallbackTerritoryDisplayName()
}

private fun String.fallbackTerritoryDisplayName(): String =
    split("_")
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { char ->
                if (char.isLowerCase()) {
                    char.titlecase()
                } else {
                    char.toString()
                }
            }
        }

private val territoryDisplayNames =
    mapOf(
        "argentinien" to "Argentinien",
        "brasilien" to "Brasilien",
        "mittelamerika" to "Mittelamerika",
        "usa" to "USA",
        "andengemeinschaft" to "Andengemeinschaft",
        "alaska" to "Alaska",
        "kanada" to "Kanada",
        "groenland" to "Grönland",
        "grossbritannien" to "Großbritannien",
        "westeuropa" to "Westeuropa",
        "skandinavien" to "Skandinavien",
        "mitteleuropa" to "Mitteleuropa",
        "russland" to "Russland",
        "naher_osten" to "Naher Osten",
        "sibirien" to "Sibirien",
        "china" to "China",
        "japan" to "Japan",
        "ferner_osten" to "Ferner Osten",
        "australien" to "Australien",
        "ozeanien" to "Ozeanien",
        "aegypten" to "Ägypten",
        "sahara" to "Sahara",
        "zentral_afrika" to "Zentralafrika",
        "sued_afrika" to "Südafrika",
    )

private const val CARD_ASPECT_RATIO = 2f / 3f
