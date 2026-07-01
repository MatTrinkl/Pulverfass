package at.aau.pulverfass.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.aau.pulverfass.app.game.AttackResultUiState
import at.aau.pulverfass.app.game.AttackUiState
import at.aau.pulverfass.app.game.AutoAttackIntent
import at.aau.pulverfass.app.game.AutoAttackUiState
import at.aau.pulverfass.app.game.FortifyUiState
import at.aau.pulverfass.app.game.GamePlayerUi
import at.aau.pulverfass.app.game.GameTerritoryUiState
import at.aau.pulverfass.app.game.GameUiState
import at.aau.pulverfass.app.game.PrivateHandCardUi
import at.aau.pulverfass.app.game.ReinforcementUiState
import at.aau.pulverfass.app.lobby.LobbyCommandKey
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.app.ui.theme.AndroidAppTheme
import at.aau.pulverfass.shared.ids.CardId
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.ids.TerritoryId
import at.aau.pulverfass.shared.lobby.state.CardType
import at.aau.pulverfass.shared.lobby.state.TurnPhase
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deckt die wichtigsten Compose-Screens als UI-Smoke- und Interaktionstests ab.
 *
 * Die Tests rendern echte Composables mit ersetzten Netzwerk-/Assetpfaden. Damit
 * wird geprüft, ob Navigation, Panels, Buttons und sichtbare Texte gemeinsam
 * funktionieren, ohne einen echten Server oder Emulator-Sensoren zu benötigen.
 */
@RunWith(AndroidJUnit4::class)
class ScreenComposableTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun load_screen_navigates_to_main_menu() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.Load.route,
                ) {
                    composable(Screen.Load.route) {
                        LoadScreen(
                            navController = navController,
                            preloadAssets = { _, onProgressChanged ->
                                onProgressChanged(0, 1)
                                delay(1_000)
                                onProgressChanged(1, 1)
                            },
                            minDisplayTimeMs = 0L,
                        )
                    }
                    composable(Screen.MainMenu.route) {
                        Text("MainMenu destination")
                    }
                }
            }
        }

        // Simuliert den kompletten Fake-Preload, ohne eine reale Sekunde zu warten.
        composeTestRule.mainClock.advanceTimeBy(1_100)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("MainMenu destination").assertIsDisplayed()
    }

    @Test
    fun lobby_screen_shows_primary_lobby_actions() {
        composeTestRule.setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                val controller = LobbyController()
                LobbyScreen(
                    navController = navController,
                    controller = controller,
                )
            }
        }

        composeTestRule.onNodeWithText("SPIEL-LOBBY").assertExists()
        composeTestRule.onNodeWithText("SPIELERNAME").assertExists()
        composeTestRule.onNodeWithText("LOBBY ERSTELLEN").assertExists()
        composeTestRule.onNodeWithText("LOBBY BEITRETEN").assertExists()
        composeTestRule.onAllNodesWithText("MAP-TEST").assertCountEquals(0)
    }

    @Test
    fun load_game_screen_prepares_game_and_navigates_to_map() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.LoadGame.route,
                ) {
                    composable(Screen.LoadGame.route) {
                        LoadGameScreen(
                            navController = navController,
                            preloadGame = { _, onProgressChanged ->
                                onProgressChanged(0, 1)
                                delay(1_000)
                                onProgressChanged(1, 1)
                            },
                            minDisplayTimeMillis = 0L,
                        )
                    }
                    composable(Screen.Game.route) {
                        Text("Game destination")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Spiel wird vorbereitet").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Karte und Spielzustand werden geladen.",
        ).assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(1_100)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Game destination").assertIsDisplayed()
    }

    @Test
    fun waiting_room_shows_host_state_and_player_name() {
        composeTestRule.setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                val controller = LobbyController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.WaitingRoom.route + "/1003/true/Carol",
                ) {
                    composable(
                        route = Screen.WaitingRoom.route + "/{lobbyCode}/{isHost}/{playerName}",
                        arguments =
                            listOf(
                                navArgument("lobbyCode") { type = NavType.StringType },
                                navArgument("isHost") { type = NavType.BoolType },
                                navArgument("playerName") { type = NavType.StringType },
                            ),
                    ) {
                        val lobbyCode = it.arguments?.getString("lobbyCode").orEmpty()
                        val isHost = it.arguments?.getBoolean("isHost") ?: false
                        val playerName = it.arguments?.getString("playerName").orEmpty()
                        WaitingRoomScreen(
                            navController = navController,
                            controller = controller,
                            lobbyCode = lobbyCode,
                            isHost = isHost,
                            playerName = playerName,
                        )
                    }
                }
            }
        }
        /**
         * Der Warteraum ist auf Landscape-Breite ausgelegt. Im Test-Viewport
         * reicht Semantik-Sichtbarkeit, weil funktional zählt, dass die Daten im
         * Baum vorhanden sind und nicht, ob jedes Element im aktuellen Ausschnitt
         * liegt.
         */
        composeTestRule.onNodeWithText("LOBBY: 1003").assertExists()
        composeTestRule.onNodeWithText("DU BIST DER HOST").assertExists()
        composeTestRule.onNodeWithText("CAROL").assertExists()
        composeTestRule.onNodeWithText("(HOST)").assertExists()
    }

    @Test
    fun waiting_room_character_picker_opens_on_button_click() {
        composeTestRule.setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                val controller = LobbyController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.WaitingRoom.route + "/4242/false/Dave",
                ) {
                    composable(
                        route = Screen.WaitingRoom.route + "/{lobbyCode}/{isHost}/{playerName}",
                        arguments =
                            listOf(
                                navArgument("lobbyCode") { type = NavType.StringType },
                                navArgument("isHost") { type = NavType.BoolType },
                                navArgument("playerName") { type = NavType.StringType },
                            ),
                    ) {
                        val lobbyCode = it.arguments?.getString("lobbyCode").orEmpty()
                        val isHost = it.arguments?.getBoolean("isHost") ?: false
                        val playerName = it.arguments?.getString("playerName").orEmpty()
                        WaitingRoomScreen(
                            navController = navController,
                            controller = controller,
                            lobbyCode = lobbyCode,
                            isHost = isHost,
                            playerName = playerName,
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("character_picker_button").performClick()
        composeTestRule.onNodeWithText("CHARAKTER WÄHLEN").assertExists()
        composeTestRule.onNodeWithText("SPEICHERN").assertExists()
        composeTestRule.onNodeWithText("ABBRECHEN").assertExists()
    }

    @Test
    fun waiting_room_character_picker_closes_on_abbrechen() {
        composeTestRule.setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                val controller = LobbyController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.WaitingRoom.route + "/1441/false/Eve",
                ) {
                    composable(
                        route = Screen.WaitingRoom.route + "/{lobbyCode}/{isHost}/{playerName}",
                        arguments =
                            listOf(
                                navArgument("lobbyCode") { type = NavType.StringType },
                                navArgument("isHost") { type = NavType.BoolType },
                                navArgument("playerName") { type = NavType.StringType },
                            ),
                    ) {
                        val lobbyCode = it.arguments?.getString("lobbyCode").orEmpty()
                        val isHost = it.arguments?.getBoolean("isHost") ?: false
                        val playerName = it.arguments?.getString("playerName").orEmpty()
                        WaitingRoomScreen(
                            navController = navController,
                            controller = controller,
                            lobbyCode = lobbyCode,
                            isHost = isHost,
                            playerName = playerName,
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("character_picker_button").performClick()
        composeTestRule.onNodeWithText("CHARAKTER WÄHLEN").assertExists()
        composeTestRule.onNodeWithText("ABBRECHEN").performClick()
        composeTestRule.onAllNodesWithText("CHARAKTER WÄHLEN").assertCountEquals(0)
    }

    @Test
    fun game_screen_shows_dynamic_map_ui_and_reacts_to_actions() {
        val controller = LobbyController()
        try {
            composeTestRule.setContent {
                AndroidAppTheme {
                    GameScreen(controller = controller)
                }
            }

            composeTestRule.onNodeWithTag("game_map_canvas").assertIsDisplayed()
            composeTestRule.onNodeWithTag("game_top_bar").assertIsDisplayed()
            composeTestRule.onNodeWithTag("game_player_panel").assertIsDisplayed()
            composeTestRule.onNodeWithTag("game_options_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("game_phase_value").assertTextEquals("Warten")
            composeTestRule.onNodeWithTag("game_round_value").assertTextEquals("Runde 1")
            composeTestRule.onNodeWithTag("game_sync_banner").assertIsDisplayed()
            composeTestRule.onNodeWithText("Verbindung getrennt. Aktionen sind gesperrt.")
                .assertIsDisplayed()
        } finally {
            controller.close()
        }
    }

    @Test
    fun game_screen_shows_auto_phase_notice_temporarily() {
        composeTestRule.mainClock.autoAdvance = false
        var dismissed = false
        val noticeText =
            "Keine Angriffe mehr möglich. Die Angriffsphase wird " +
                "automatisch beendet."

        try {
            composeTestRule.setContent {
                AndroidAppTheme {
                    GameScreenContent(
                        contentState =
                            GameScreenContentState(
                                players = emptyList(),
                                localPlayerId = PlayerId(1),
                                uiState = GameUiState(),
                                isConnected = true,
                                pendingCommandKeys = emptySet(),
                                mapPainter = ColorPainter(Color.White),
                                autoPhaseNoticeText = noticeText,
                            ),
                        actions =
                            GameScreenActions(
                                onRegionSelected = {},
                                onToggleCards = {},
                                onAdvanceTurn = {},
                                onRefreshGameState = {},
                                onClearAutoPhaseNotice = { dismissed = true },
                            ),
                        countdownState = false to 0,
                    )
                }
            }

            composeTestRule.onNodeWithTag("auto_phase_notice_popup").assertIsDisplayed()
            composeTestRule.onNodeWithText("PHASE GEWECHSELT").assertIsDisplayed()
            composeTestRule.onNodeWithText(noticeText).assertIsDisplayed()
            assertTrue(!dismissed)
            composeTestRule.mainClock.advanceTimeBy(2_100)
            composeTestRule.waitForIdle()

            assertTrue(dismissed)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun game_screen_shows_cheat_report_notice_for_four_seconds() {
        /**
         * Das Overlay soll nicht sofort verschwinden: Die Meldung erklärt eine
         * spätere Konsequenz im Spiel. Mit deaktivierter Auto-Advance-Clock kann
         * der Test exakt prüfen, dass es nach ca. vier Sekunden geschlossen wird.
         */
        composeTestRule.mainClock.autoAdvance = false
        var dismissed = false
        val noticeText =
            "Deine Meldung war korrekt. Du erhältst in deiner nächsten " +
                "Verstärkungsphase +3 Truppen."

        try {
            composeTestRule.setContent {
                AndroidAppTheme {
                    GameScreenContent(
                        contentState =
                            GameScreenContentState(
                                players = emptyList(),
                                localPlayerId = PlayerId(1),
                                uiState = GameUiState(),
                                isConnected = true,
                                pendingCommandKeys = emptySet(),
                                mapPainter = ColorPainter(Color.White),
                                cheatReportNoticeText = noticeText,
                            ),
                        actions =
                            GameScreenActions(
                                onRegionSelected = {},
                                onToggleCards = {},
                                onAdvanceTurn = {},
                                onRefreshGameState = {},
                                onClearCheatReportNotice = { dismissed = true },
                            ),
                        countdownState = false to 0,
                    )
                }
            }

            composeTestRule.onNodeWithTag("cheat_report_notice_popup").assertIsDisplayed()
            composeTestRule.onNodeWithText("CHEAT-MELDUNG").assertIsDisplayed()
            composeTestRule.onNodeWithText(noticeText).assertIsDisplayed()
            composeTestRule.mainClock.advanceTimeBy(3_100)
            composeTestRule.waitForIdle()
            assertTrue(!dismissed)

            composeTestRule.mainClock.advanceTimeBy(1_000)
            composeTestRule.waitForIdle()

            assertTrue(dismissed)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun game_screen_options_reports_selected_cheat_player() {
        /**
         * Dieser UI-Test prüft nicht die Serverlogik, sondern nur die Bedienung:
         * Optionsmenü öffnen, "CHEAT MELDEN" anklicken, Gegner auswählen und
         * sicherstellen, dass dessen PlayerId an den Callback geht.
         */
        val localPlayerId = PlayerId(1)
        val accusedPlayerId = PlayerId(2)
        var reportedPlayerId: PlayerId? = null

        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreenContent(
                    contentState =
                        GameScreenContentState(
                            players =
                                listOf(
                                    GamePlayerUi(
                                        playerId = localPlayerId,
                                        name = "Alice",
                                        avatarText = "A",
                                        color = Color(0xFF6FD4C5),
                                    ),
                                    GamePlayerUi(
                                        playerId = accusedPlayerId,
                                        name = "Bob",
                                        avatarText = "B",
                                        color = Color(0xFFA6342B),
                                    ),
                                ),
                            localPlayerId = localPlayerId,
                            uiState = GameUiState(),
                            isConnected = true,
                            pendingCommandKeys = emptySet(),
                            mapPainter = ColorPainter(Color.White),
                        ),
                    actions =
                        GameScreenActions(
                            onRegionSelected = {},
                            onToggleCards = {},
                            onAdvanceTurn = {},
                            onReportCheat = { reportedPlayerId = it },
                            onRefreshGameState = {},
                        ),
                    countdownState = false to 0,
                )
            }
        }

        composeTestRule.onNodeWithTag("game_options_button").performClick()
        composeTestRule.onNodeWithText("CHEAT MELDEN").assertIsEnabled().performClick()
        composeTestRule.onNodeWithText("SPIELER AUSWÄHLEN").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cheat_report_player_${accusedPlayerId.value}")
            .performClick()

        assertEquals(accusedPlayerId, reportedPlayerId)
        composeTestRule.onAllNodesWithText("SPIELER AUSWÄHLEN").assertCountEquals(0)
    }

    @Test
    fun private_hand_panel_shows_own_cards_with_duplicate_labels() {
        composeTestRule.setContent {
            AndroidAppTheme {
                PrivateHandPanel(
                    state =
                        PrivateHandPanelState(
                            playerName = "Alice",
                            handCards = listOf("Infanterie", "Infanterie", "Kavallerie"),
                        ),
                )
            }
        }

        composeTestRule.onNodeWithTag("game_cards_panel").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Infanterie").assertCountEquals(2)
        composeTestRule.onNodeWithText("Kavallerie").assertIsDisplayed()
    }

    @Test
    fun game_screen_keeps_private_hand_hidden_when_cards_panel_is_closed() {
        val playerId = PlayerId(1)

        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreenContent(
                    contentState =
                        GameScreenContentState(
                            players =
                                listOf(
                                    GamePlayerUi(
                                        playerId = playerId,
                                        name = "Alice",
                                        avatarText = "A",
                                        color = Color(0xFF6FD4C5),
                                    ),
                                ),
                            localPlayerId = playerId,
                            uiState =
                                GameUiState(
                                    handCards = listOf("Geheime Karte"),
                                    cardsVisible = false,
                                ),
                            isConnected = true,
                            pendingCommandKeys = emptySet(),
                            mapPainter = ColorPainter(Color.White),
                        ),
                    actions =
                        GameScreenActions(
                            onRegionSelected = {},
                            onToggleCards = {},
                            onAdvanceTurn = {},
                            onRefreshGameState = {},
                        ),
                    countdownState = false to 0,
                )
            }
        }

        composeTestRule.onAllNodesWithTag("game_cards_panel").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Geheime Karte").assertCountEquals(0)
    }

    @Test
    fun reinforcement_panel_stays_hidden_until_an_owned_target_is_selected() {
        val playerId = PlayerId(1)

        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreenContent(
                    contentState =
                        GameScreenContentState(
                            players = emptyList(),
                            localPlayerId = playerId,
                            uiState =
                                GameUiState(
                                    activePlayerId = playerId,
                                    turnPhase = TurnPhase.REINFORCEMENTS,
                                    reinforcementState =
                                        ReinforcementUiState(
                                            playerId = playerId,
                                            pendingAmount = 2,
                                        ),
                                ),
                            isConnected = true,
                            pendingCommandKeys = emptySet(),
                            mapPainter = ColorPainter(Color.White),
                        ),
                    actions =
                        GameScreenActions(
                            onRegionSelected = {},
                            onToggleCards = {},
                            onAdvanceTurn = {},
                            onRefreshGameState = {},
                        ),
                    countdownState = false to 0,
                )
            }
        }

        composeTestRule.onAllNodesWithTag("reinforcement_panel").assertCountEquals(0)
        composeTestRule.onNodeWithTag("end_round_button").assertIsNotEnabled()
    }

    @Test
    fun reinforcement_panel_places_troops() {
        val playerId = PlayerId(1)
        var placementDelta = 0
        var placed = false
        var closedRegion: String? = null

        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreenContent(
                    contentState =
                        GameScreenContentState(
                            players =
                                listOf(
                                    GamePlayerUi(
                                        playerId = playerId,
                                        name = "Alice",
                                        avatarText = "A",
                                        color = Color(0xFF6FD4C5),
                                    ),
                                ),
                            localPlayerId = playerId,
                            uiState =
                                GameUiState(
                                    activePlayerId = playerId,
                                    turnPhase = TurnPhase.REINFORCEMENTS,
                                    selectedRegionId = "brazil",
                                    reinforcementState =
                                        ReinforcementUiState(
                                            playerId = playerId,
                                            pendingAmount = 2,
                                            territoryBonus = 2,
                                            isBonusBreakdownKnown = true,
                                        ),
                                ),
                            isConnected = true,
                            pendingCommandKeys = emptySet(),
                            mapPainter = ColorPainter(Color.White),
                        ),
                    actions =
                        GameScreenActions(
                            onRegionSelected = { closedRegion = it },
                            onToggleCards = {},
                            onAdvanceTurn = {},
                            onAdjustReinforcementPlacementAmount = { placementDelta = it },
                            onPlaceReinforcements = { placed = true },
                            onRefreshGameState = {},
                        ),
                    countdownState = false to 0,
                )
            }
        }

        composeTestRule.onNodeWithTag("reinforcement_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reinforcement_remaining").assertTextEquals("Verfügbar: 2")
        composeTestRule.onNodeWithText("Gebiet 2 · Kontinent 0 · Karten 0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reinforcement_slider").performSemanticsAction(
            SemanticsActions.SetProgress,
        ) { setProgress ->
            setProgress(2f)
        }
        composeTestRule
            .onNodeWithTag("place_reinforcements_button")
            .assertIsEnabled()
            .performClick()
        composeTestRule.onNodeWithTag("close_reinforcement_panel").performClick()

        assertEquals(1, placementDelta)
        assertTrue(placed)
        assertEquals("brazil", closedRegion)
    }

    @Test
    fun card_hand_overlay_shows_typed_cards_and_submits_trade_in() {
        val playerId = PlayerId(1)
        val cardIds =
            listOf(
                CardId("territory:sahara:a"),
                CardId("territory:brasilien:b"),
                CardId("territory:japan:c"),
            )
        var selectedCardId: CardId? = null
        var traded = false
        var closed = false

        composeTestRule.setContent {
            var selectedTradeInCardIds by remember {
                mutableStateOf(setOf(cardIds[1], cardIds[2]))
            }
            AndroidAppTheme {
                GameScreenContent(
                    contentState =
                        GameScreenContentState(
                            players =
                                listOf(
                                    GamePlayerUi(
                                        playerId = playerId,
                                        name = "Alice",
                                        avatarText = "A",
                                        color = Color(0xFF6FD4C5),
                                    ),
                                ),
                            localPlayerId = playerId,
                            uiState =
                                GameUiState(
                                    activePlayerId = playerId,
                                    turnPhase = TurnPhase.REINFORCEMENTS,
                                    cardsVisible = true,
                                    privateHandCards =
                                        listOf(
                                            PrivateHandCardUi(cardIds[0], CardType.A),
                                            PrivateHandCardUi(cardIds[1], CardType.B),
                                            PrivateHandCardUi(cardIds[2], CardType.C),
                                            PrivateHandCardUi(CardId("joker:1"), CardType.JOKER),
                                        ),
                                    selectedTradeInCardIds = selectedTradeInCardIds,
                                ),
                            isConnected = true,
                            pendingCommandKeys = emptySet(),
                            mapPainter = ColorPainter(Color.White),
                        ),
                    actions =
                        GameScreenActions(
                            onRegionSelected = {},
                            onToggleCards = { closed = true },
                            onAdvanceTurn = {},
                            onToggleTradeInCard = { cardId ->
                                selectedCardId = cardId
                                selectedTradeInCardIds =
                                    if (cardId in selectedTradeInCardIds) {
                                        selectedTradeInCardIds - cardId
                                    } else {
                                        selectedTradeInCardIds + cardId
                                    }
                            },
                            onTradeInCards = { traded = true },
                            onRefreshGameState = {},
                        ),
                    countdownState = false to 0,
                )
            }
        }

        composeTestRule.onNodeWithTag("card_hand_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Galeone").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Kanone").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Pirat").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Galeone Sahara").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Kanone Brasilien").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Pirat Japan").assertCountEquals(0)
        composeTestRule.onNodeWithTag("card_hand_card_territory:sahara:a")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("trade_in_cards_button").assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithTag("close_cards_button").performClick()

        assertEquals(cardIds[0], selectedCardId)
        assertTrue(traded)
        assertTrue(closed)
    }

    @Test
    fun cards_screen_names_joker_cards_as_kaiser() {
        val playerId = PlayerId(1)

        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreenContent(
                    contentState =
                        GameScreenContentState(
                            players =
                                listOf(
                                    GamePlayerUi(
                                        playerId = playerId,
                                        name = "Alice",
                                        avatarText = "A",
                                        color = Color(0xFF6FD4C5),
                                    ),
                                ),
                            localPlayerId = playerId,
                            uiState =
                                GameUiState(
                                    activePlayerId = playerId,
                                    turnPhase = TurnPhase.DRAW_CARD,
                                    cardsVisible = true,
                                    privateHandCards =
                                        listOf(
                                            PrivateHandCardUi(CardId("joker:1"), CardType.JOKER),
                                        ),
                                ),
                            isConnected = true,
                            pendingCommandKeys = emptySet(),
                            mapPainter = ColorPainter(Color.White),
                        ),
                    actions =
                        GameScreenActions(
                            onRegionSelected = {},
                            onToggleCards = {},
                            onAdvanceTurn = {},
                            onRefreshGameState = {},
                        ),
                    countdownState = false to 0,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Kaiser").assertIsDisplayed()
    }

    @Test
    fun draw_card_phase_does_not_offer_manual_phase_end() {
        val playerId = PlayerId(1)
        var advanced = false

        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreenContent(
                    contentState =
                        GameScreenContentState(
                            players =
                                listOf(
                                    GamePlayerUi(
                                        playerId = playerId,
                                        name = "Alice",
                                        avatarText = "A",
                                        color = Color(0xFF6FD4C5),
                                    ),
                                ),
                            localPlayerId = playerId,
                            uiState =
                                GameUiState(
                                    activePlayerId = playerId,
                                    turnPhase = TurnPhase.DRAW_CARD,
                                ),
                            isConnected = true,
                            pendingCommandKeys = emptySet(),
                            mapPainter = ColorPainter(Color.White),
                        ),
                    actions =
                        GameScreenActions(
                            onRegionSelected = {},
                            onToggleCards = {},
                            onAdvanceTurn = { advanced = true },
                            onRefreshGameState = {},
                        ),
                    countdownState = false to 0,
                )
            }
        }

        composeTestRule.onNodeWithTag("end_round_button").assertIsNotEnabled()
        assertFalse(advanced)
    }

    @Test
    fun phase_end_button_confirms_reinforcements_after_pool_is_empty_without_panel() {
        val playerId = PlayerId(1)
        var finished = false

        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreenContent(
                    contentState =
                        GameScreenContentState(
                            players = emptyList(),
                            localPlayerId = playerId,
                            uiState =
                                GameUiState(
                                    activePlayerId = playerId,
                                    turnPhase = TurnPhase.REINFORCEMENTS,
                                    selectedRegionId = "brazil",
                                    reinforcementState =
                                        ReinforcementUiState(
                                            playerId = playerId,
                                            pendingAmount = 0,
                                        ),
                                ),
                            isConnected = true,
                            pendingCommandKeys = emptySet(),
                            mapPainter = ColorPainter(Color.White),
                        ),
                    actions =
                        GameScreenActions(
                            onRegionSelected = {},
                            onToggleCards = {},
                            onAdvanceTurn = {},
                            onConfirmReinforcementsDone = { finished = true },
                            onRefreshGameState = {},
                        ),
                    countdownState = false to 0,
                )
            }
        }

        composeTestRule.onAllNodesWithTag("reinforcement_panel").assertCountEquals(0)
        composeTestRule.onNodeWithTag("end_round_button")
            .assertIsEnabled()
            .performClick()
        assertTrue(finished)
    }

    @Test
    fun reinforcement_panel_blocks_placement_while_trade_in_is_pending() {
        val playerId = PlayerId(1)

        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreenContent(
                    contentState =
                        GameScreenContentState(
                            players = emptyList(),
                            localPlayerId = playerId,
                            uiState =
                                GameUiState(
                                    activePlayerId = playerId,
                                    turnPhase = TurnPhase.REINFORCEMENTS,
                                    selectedRegionId = "brazil",
                                    reinforcementState =
                                        ReinforcementUiState(
                                            playerId = playerId,
                                            pendingAmount = 2,
                                        ),
                                ),
                            isConnected = true,
                            pendingCommandKeys = setOf(LobbyCommandKey.TRADE_IN_CARDS),
                            mapPainter = ColorPainter(Color.White),
                        ),
                    actions =
                        GameScreenActions(
                            onRegionSelected = {},
                            onToggleCards = {},
                            onAdvanceTurn = {},
                            onRefreshGameState = {},
                        ),
                    countdownState = false to 0,
                )
            }
        }

        composeTestRule.onNodeWithTag("reinforcement_slider").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("place_reinforcements_button").assertIsNotEnabled()
    }

    @Test
    fun attack_panel_submits_intent_and_phase_button_confirms_attack_done() {
        val playerId = PlayerId(1)
        var attackAdjustment = 0
        var moveAdjustment = 0
        var attacked = false
        var finished = false

        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreenContent(
                    contentState =
                        GameScreenContentState(
                            players = emptyList(),
                            localPlayerId = playerId,
                            uiState =
                                GameUiState(
                                    activePlayerId = playerId,
                                    turnPhase = TurnPhase.ATTACK,
                                    selectionFromRegionId = "brazil",
                                    selectionToRegionId = "argentina",
                                    attackState =
                                        AttackUiState(
                                            attackTroops = 4,
                                            moveAfterCapture = 3,
                                        ),
                                    adjacentTerritoryIds =
                                        mapOf(
                                            TerritoryId("brasilien") to
                                                setOf(TerritoryId("argentinien")),
                                        ),
                                    territoryStates =
                                        mapOf(
                                            TerritoryId("brasilien") to
                                                at.aau.pulverfass.app.game.GameTerritoryUiState(
                                                    TerritoryId("brasilien"),
                                                    playerId,
                                                    6,
                                                ),
                                            TerritoryId("argentinien") to
                                                at.aau.pulverfass.app.game.GameTerritoryUiState(
                                                    TerritoryId("argentinien"),
                                                    PlayerId(2),
                                                    3,
                                                ),
                                        ),
                                ),
                            isConnected = true,
                            pendingCommandKeys = emptySet(),
                            mapPainter = ColorPainter(Color.White),
                        ),
                    actions =
                        GameScreenActions(
                            onRegionSelected = {},
                            onToggleCards = {},
                            onAdvanceTurn = {},
                            onAdjustAttackTroops = { attackAdjustment = it },
                            onAdjustMoveAfterCapture = { moveAdjustment = it },
                            onAttack = { attacked = true },
                            onConfirmAttackDone = { finished = true },
                            onRefreshGameState = {},
                        ),
                    countdownState = false to 0,
                )
            }
        }

        composeTestRule.onNodeWithTag("attack_panel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Angriff: Brasilien → Argentinien").assertIsDisplayed()
        composeTestRule.onNodeWithTag("attack_troops_slider").performSemanticsAction(
            SemanticsActions.SetProgress,
        ) { setProgress ->
            setProgress(5f)
        }
        composeTestRule.onNodeWithTag("attack_move_slider").performSemanticsAction(
            SemanticsActions.SetProgress,
        ) { setProgress ->
            setProgress(4f)
        }
        composeTestRule.onNodeWithTag("attack_submit_button").assertIsEnabled().performScrollTo().performClick()
        composeTestRule.onNodeWithTag("end_round_button").assertIsEnabled().performClick()

        assertEquals(1, attackAdjustment)
        assertEquals(1, moveAdjustment)
        assertTrue(attacked)
        assertTrue(finished)
    }

    @Test
    fun auto_attack_hides_manual_attack_panel() {
        val playerId = PlayerId(1)

        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreenContent(
                    contentState =
                        GameScreenContentState(
                            players = emptyList(),
                            localPlayerId = playerId,
                            uiState =
                                GameUiState(
                                    activePlayerId = playerId,
                                    turnPhase = TurnPhase.ATTACK,
                                    selectionFromRegionId = "brazil",
                                    selectionToRegionId = "argentina",
                                    attackState =
                                        AttackUiState(
                                            attackTroops = 3,
                                            moveAfterCapture = 3,
                                            autoAttack =
                                                AutoAttackUiState(
                                                    intent =
                                                        AutoAttackIntent(
                                                            fromTerritoryId =
                                                                TerritoryId("brasilien"),
                                                            toTerritoryId =
                                                                TerritoryId("argentinien"),
                                                            attackTroops = 3,
                                                            moveAfterCapture = 3,
                                                        ),
                                                    isEnabled = true,
                                                    isAwaitingResult = true,
                                                ),
                                        ),
                                ),
                            isConnected = true,
                            pendingCommandKeys = emptySet(),
                            mapPainter = ColorPainter(Color.White),
                        ),
                    actions =
                        GameScreenActions(
                            onRegionSelected = {},
                            onToggleCards = {},
                            onAdvanceTurn = {},
                            onRefreshGameState = {},
                        ),
                    countdownState = false to 0,
                )
            }
        }

        composeTestRule.onAllNodesWithTag("attack_panel").assertCountEquals(0)
    }

    @Test
    fun fortify_panel_submits_move_and_phase_button_advances() {
        val playerId = PlayerId(1)
        var fortifyAdjustment = 0
        var moved = false
        var advanced = false
        var closedRegion: String? = null

        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreenContent(
                    contentState =
                        GameScreenContentState(
                            players = emptyList(),
                            localPlayerId = playerId,
                            uiState =
                                GameUiState(
                                    activePlayerId = playerId,
                                    turnPhase = TurnPhase.FORTIFY,
                                    selectionFromRegionId = "brazil",
                                    selectionToRegionId = "argentina",
                                    fortifyState = FortifyUiState(troopCount = 2),
                                    adjacentTerritoryIds =
                                        mapOf(
                                            TerritoryId("brasilien") to
                                                setOf(TerritoryId("argentinien")),
                                            TerritoryId("argentinien") to
                                                setOf(TerritoryId("brasilien")),
                                        ),
                                    territoryStates =
                                        mapOf(
                                            TerritoryId("brasilien") to
                                                GameTerritoryUiState(
                                                    TerritoryId("brasilien"),
                                                    playerId,
                                                    5,
                                                ),
                                            TerritoryId("argentinien") to
                                                GameTerritoryUiState(
                                                    TerritoryId("argentinien"),
                                                    playerId,
                                                    1,
                                                ),
                                        ),
                                ),
                            isConnected = true,
                            pendingCommandKeys = emptySet(),
                            mapPainter = ColorPainter(Color.White),
                        ),
                    actions =
                        GameScreenActions(
                            onRegionSelected = { closedRegion = it },
                            onToggleCards = {},
                            onAdvanceTurn = { advanced = true },
                            onAdjustFortifyTroops = { fortifyAdjustment = it },
                            onFortifyMove = { moved = true },
                            onRefreshGameState = {},
                        ),
                    countdownState = false to 0,
                )
            }
        }

        composeTestRule.onNodeWithTag("fortify_panel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Verschieben: Brasilien → Argentinien").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fortify_troops_slider").performSemanticsAction(
            SemanticsActions.SetProgress,
        ) { setProgress ->
            setProgress(3f)
        }
        composeTestRule.onNodeWithTag("fortify_submit_button").assertIsEnabled().performClick()
        composeTestRule.onNodeWithTag("end_round_button").assertIsEnabled().performClick()
        composeTestRule.onNodeWithTag("close_fortify_panel").performClick()

        assertEquals(1, fortifyAdjustment)
        assertTrue(moved)
        assertTrue(advanced)
        assertEquals("brazil", closedRegion)
    }

    @Test
    fun attack_result_panel_shows_server_resolved_capture() {
        composeTestRule.setContent {
            AndroidAppTheme {
                GameScreenContent(
                    contentState =
                        GameScreenContentState(
                            players = emptyList(),
                            localPlayerId = PlayerId(2),
                            uiState =
                                GameUiState(
                                    turnPhase = TurnPhase.ATTACK,
                                    attackState =
                                        AttackUiState(
                                            latestResult =
                                                AttackResultUiState(
                                                    fromTerritoryId = TerritoryId("brasilien"),
                                                    toTerritoryId = TerritoryId("argentinien"),
                                                    attackerRolls = listOf(6, 4),
                                                    defenderRolls = listOf(2),
                                                    attackerLosses = 0,
                                                    defenderLosses = 1,
                                                    attackerRemaining = 2,
                                                    defenderRemaining = 0,
                                                    occupyingTroopCount = 2,
                                                ),
                                        ),
                                ),
                            isConnected = true,
                            pendingCommandKeys = emptySet(),
                            mapPainter = ColorPainter(Color.White),
                        ),
                    actions =
                        GameScreenActions(
                            onRegionSelected = {},
                            onToggleCards = {},
                            onAdvanceTurn = {},
                            onRefreshGameState = {},
                        ),
                    countdownState = false to 0,
                )
            }
        }

        composeTestRule.onNodeWithTag("attack_result_panel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kampfergebnis: Brasilien → Argentinien").assertIsDisplayed()
        composeTestRule.onNodeWithTag(
            "attack_result_outcome",
        ).assertTextEquals("Erobert · Besetzung: 2")
    }
}
