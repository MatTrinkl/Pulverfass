package at.aau.pulverfass.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import at.aau.pulverfass.client.audio.BackgroundMusicManager
import at.aau.pulverfass.client.audio.MusicTrack
import at.aau.pulverfass.client.lobby.LobbyController
import at.aau.pulverfass.client.lobby.LobbyUiState
import at.aau.pulverfass.client.storage.createPlayerNameStore
import at.aau.pulverfass.client.storage.createReconnectSessionStore
import at.aau.pulverfass.client.ui.navigation.Screen
import at.aau.pulverfass.client.ui.navigation.canAutoNavigateToRestoredGame
import at.aau.pulverfass.client.ui.navigation.restoredGameNavigationTarget
import at.aau.pulverfass.client.ui.screens.GameScreen
import at.aau.pulverfass.client.ui.screens.LoadGameScreen
import at.aau.pulverfass.client.ui.screens.LoadScreen
import at.aau.pulverfass.client.ui.screens.LobbyScreen
import at.aau.pulverfass.client.ui.screens.MainMenuScreen
import at.aau.pulverfass.client.ui.screens.OptionsScreen
import at.aau.pulverfass.client.ui.screens.StudioIntroScreen
import at.aau.pulverfass.client.ui.screens.WaitingRoomScreen
import at.aau.pulverfass.client.ui.theme.PulverfassTheme
import io.ktor.http.decodeURLPart

/**
 * Gemeinsamer Einstiegspunkt der Client-UI für Android und iOS.
 *
 * Portiert aus der MainActivity des app-Moduls: Der [LobbyController] bleibt
 * absichtlich oberhalb des NavHost, damit Lobby, Warteraum und Spielbildschirm
 * dieselbe WebSocket-Verbindung teilen. Musik ist routenbasiert, damit einzelne
 * Screens keine eigenen globalen Audio-Entscheidungen treffen müssen.
 *
 * @param musicManager plattformspezifischer Audio-Manager; die Plattform-Shell
 *   (MainActivity bzw. MainViewController) hält die Instanz, damit
 *   Lifecycle-Callbacks dieselbe Instanz pausieren und freigeben können.
 */
@Composable
fun App(musicManager: BackgroundMusicManager) {
    PulverfassTheme {
        val navController = rememberNavController()
        val reconnectSessionStore =
            remember {
                createReconnectSessionStore()
            }
        val playerNameStore =
            remember {
                createPlayerNameStore()
            }
        val lobbyController =
            remember {
                LobbyController(
                    reconnectSessionStore = reconnectSessionStore,
                    playerNameStore = playerNameStore,
                )
            }
        val lobbyState by lobbyController.state.collectAsState()

        /*
         * Musik ist routenbasiert, damit einzelne Screens keine eigenen
         * globalen Audio-Entscheidungen treffen müssen. Der Warteraum
         * darf diese Route später temporär überschreiben, wenn der
         * Character-Picker offen ist.
         */
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        LaunchedEffect(currentBackStackEntry) {
            val route = currentBackStackEntry?.destination?.route
            when {
                route == Screen.MainMenu.route ||
                    route == Screen.LoadGame.route ->
                    musicManager.play(MusicTrack.MAIN_MENU)
                route == Screen.Lobby.route ->
                    musicManager.play(MusicTrack.LOBBY_MENU)
                route?.startsWith(Screen.WaitingRoom.route) == true ->
                    musicManager.play(MusicTrack.LOBBY_WAITING)
                route == Screen.Options.route ->
                    musicManager.play(MusicTrack.OPTIONS_MENU)
                route == Screen.Game.route ->
                    musicManager.play(MusicTrack.GAMEPLAY_LOOP, loop = true)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                lobbyController.close()
            }
        }

        RestoredGameNavigationEffect(
            navController = navController,
            lobbyState = lobbyState,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            /*
             * Definiert alle aktuell verfügbaren Routen und Ziele.
             */
            NavHost(
                navController = navController,
                startDestination = Screen.StudioIntro.route,
            ) {
                composable(Screen.StudioIntro.route) {
                    StudioIntroScreen(
                        navController = navController,
                        musicManager = musicManager,
                    )
                }
                composable(Screen.Load.route) {
                    LoadScreen(navController)
                }
                composable(Screen.MainMenu.route) {
                    MainMenuScreen(
                        onStartClick = {
                            navController.navigate(Screen.Lobby.route)
                        },
                        onOptionsClick = {
                            navController.navigate(Screen.Options.route)
                        },
                        onExitClick =
                            if (isExitSupported) {
                                { exitApplication() }
                            } else {
                                null
                            },
                    )
                }
                composable(Screen.Lobby.route) {
                    LobbyScreen(
                        navController = navController,
                        controller = lobbyController,
                    )
                }

                composable(Screen.LoadGame.route) {
                    LoadGameScreen(navController = navController)
                }
                /*
                 * Warteraum mit Parametern aus der Navigation.
                 * Der Controller ist trotzdem die Quelle der Wahrheit;
                 * die Argumente sind nur ein Fallback für direkte
                 * Navigation und UI-Rekonstruktion.
                 */
                composable(
                    route =
                        Screen.WaitingRoom.route + "/{lobbyCode}/{isHost}/{playerName}",
                    arguments =
                        listOf(
                            navArgument("lobbyCode") { type = NavType.StringType },
                            navArgument("isHost") { type = NavType.BoolType },
                            navArgument("playerName") { type = NavType.StringType },
                        ),
                ) { backStackEntry ->
                    val args = backStackEntry.arguments
                    val lobbyCode = args?.read { getStringOrNull("lobbyCode") } ?: ""
                    val isHost = args?.read { getBooleanOrNull("isHost") } ?: false
                    val playerName =
                        (args?.read { getStringOrNull("playerName") } ?: "").decodeURLPart()
                    WaitingRoomScreen(
                        navController = navController,
                        controller = lobbyController,
                        lobbyCode = lobbyCode,
                        isHost = isHost,
                        playerName = playerName,
                    )
                }
                composable(Screen.Game.route) {
                    GameScreen(
                        controller = lobbyController,
                        musicManager = musicManager,
                        onNavigateToMain = {
                            navController.navigate(Screen.MainMenu.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Screen.Options.route) {
                    val optionsState by lobbyController.state.collectAsState()
                    OptionsScreen(
                        navController = navController,
                        playerName = optionsState.playerName,
                        onPlayerNameChange = lobbyController::updatePlayerName,
                        musicManager = musicManager,
                    )
                }
            }
        }
    }
}

/**
 * Führt die UI nach einem erfolgreichen App-Start-Reconnect zurück in den
 * Spielpfad.
 *
 * Der Server liefert nach dem Reconnect den fachlichen Kontext und die
 * Catch-up-Daten. Diese Funktion übersetzt danach nur den fertigen UI-State in
 * Navigation. Dadurch bleibt klar getrennt, dass die App keine Spieler oder
 * Spielphasen selbst rekonstruiert.
 */
@Composable
private fun RestoredGameNavigationEffect(
    navController: NavController,
    lobbyState: LobbyUiState,
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val targetRoute = restoredGameNavigationTarget(lobbyState)

    LaunchedEffect(currentRoute, targetRoute) {
        if (
            targetRoute != null &&
            canAutoNavigateToRestoredGame(currentRoute)
        ) {
            /*
             * Der Zielscreen lädt die Kartenassets wie beim normalen Spielstart.
             * launchSingleTop verhindert doppelte Einträge, falls Load- und
             * Lobby-Screen sehr kurz hintereinander denselben Reconnect-Zustand
             * sehen.
             */
            navController.navigate(targetRoute) {
                launchSingleTop = true
            }
        }
    }
}
