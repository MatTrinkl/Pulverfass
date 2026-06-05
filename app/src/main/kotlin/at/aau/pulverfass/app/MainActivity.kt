package at.aau.pulverfass.app

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import at.aau.pulverfass.app.audio.BackgroundMusicManager
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.lobby.LobbyUiState
import at.aau.pulverfass.app.storage.SharedPreferencesPlayerNameStore
import at.aau.pulverfass.app.storage.SharedPreferencesReconnectSessionStore
import at.aau.pulverfass.app.ui.components.ServerStatusIndicator
import at.aau.pulverfass.app.ui.components.rememberServerHealthStatus
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.app.ui.navigation.canAutoNavigateToRestoredGame
import at.aau.pulverfass.app.ui.navigation.restoredGameNavigationTarget
import at.aau.pulverfass.app.ui.screens.GameScreen
import at.aau.pulverfass.app.ui.screens.LoadGameScreen
import at.aau.pulverfass.app.ui.screens.LoadScreen
import at.aau.pulverfass.app.ui.screens.LobbyScreen
import at.aau.pulverfass.app.ui.screens.MainMenuScreen
import at.aau.pulverfass.app.ui.screens.OptionsScreen
import at.aau.pulverfass.app.ui.screens.StudioIntroScreen
import at.aau.pulverfass.app.ui.screens.WaitingRoomScreen
import at.aau.pulverfass.app.ui.theme.AndroidAppTheme

/**
 * Compose-basierter Einstiegspunkt der Android-App.
 *
 * Die Activity initialisiert genau eine [LobbyController]-Instanz und
 * verwaltet den [BackgroundMusicManager] als Activity-Field, damit
 * Lifecycle-Callbacks (onPause/onResume/onDestroy) auf die selbe Instanz
 * zugreifen können wie der Compose-Tree.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var musicManager: BackgroundMusicManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Background-Music-Manager als Field, damit onPause/onResume drauf zugreifen können
        musicManager = BackgroundMusicManager(applicationContext)

        /*
         * Vollbild ist eine Eigenschaft der gesamten Activity und nicht eines
         * einzelnen Screens. So bleibt der Modus auch bei Navigation zwischen
         * Studio-Intro, Loading-Screen und Spiel erhalten.
         */
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                val reconnectSessionStore =
                    remember {
                        SharedPreferencesReconnectSessionStore(applicationContext)
                    }
                val playerNameStore =
                    remember {
                        SharedPreferencesPlayerNameStore(applicationContext)
                    }
                val lobbyController =
                    remember {
                        LobbyController(
                            reconnectSessionStore = reconnectSessionStore,
                            playerNameStore = playerNameStore,
                        )
                    }
                val lobbyState by lobbyController.state.collectAsState()
                val serverHealthStatus by rememberServerHealthStatus()

                /*
                 * Jede Navigation kann neue Window-Inset-Berechnungen auslösen.
                 * Systembars werden daher nach Routenwechsel erneut verborgen;
                 * einzelne Screens dürfen den globalen Modus nicht zurücksetzen.
                 */
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                LaunchedEffect(currentBackStackEntry) {
                    hideSystemBars()
                }

                // Audio: route-based playback
                // - Menu-Flow (MainMenu / Lobby / WaitingRoom / LoadGame / Settings): looped menu theme
                // - Game: stop menu music
                // - Settings: change Music to "settings"-music
                // - StudioIntro / Load: video plays its own audio (or silence)
                LaunchedEffect(currentBackStackEntry) {
                    val route = currentBackStackEntry?.destination?.route
                    when {
                        route == Screen.MainMenu.route ||
                            route == Screen.LoadGame.route ->
                            musicManager.play(R.raw.music_main_menu)
                        route == Screen.Lobby.route ->
                            musicManager.play(R.raw.music_lobby_menu)
                        route?.startsWith(Screen.WaitingRoom.route) == true ->
                            musicManager.play(R.raw.music_lobby_waiting)
                        route == Screen.Options.route ->
                            musicManager.play(R.raw.music_options_menu)
                        route == Screen.Game.route ->
                            musicManager.play(R.raw.music_gameplay_loop, loop = true)
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

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        /*
                         * Definiert alle aktuell verfügbaren Routen und Ziele.
                         * Der LobbyController bleibt absichtlich oberhalb des
                         * NavHost, damit Lobby, Warteraum und Spielbildschirm
                         * dieselbe WebSocket-Verbindung teilen.
                         */
                        NavHost(
                            navController = navController,
                            startDestination = Screen.StudioIntro.route,
                        ) {
                            composable(Screen.StudioIntro.route) {
                                StudioIntroScreen(navController)
                            }
                            composable(Screen.Load.route) {
                                LoadScreen(navController)
                            }
                            composable(Screen.MainMenu.route) {
                                val activity = LocalContext.current as? Activity
                                MainMenuScreen(
                                    onStartClick = {
                                        navController.navigate(Screen.Lobby.route)
                                    },
                                    onOptionsClick = {
                                        navController.navigate(Screen.Options.route)
                                    },
                                    onExitClick = {
                                        activity?.finish()
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
                                val lobbyCode = args?.getString("lobbyCode") ?: ""
                                val isHost = args?.getBoolean("isHost") ?: false
                                val playerName = Uri.decode(args?.getString("playerName") ?: "")
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

                        ServerStatusIndicator(
                            status = serverHealthStatus,
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 12.dp, end = 12.dp),
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        musicManager.pause()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        musicManager.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        musicManager.release()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * Stellt den immersiven App-Modus für den Activity-Window wieder her.
     *
     * Android darf Bars temporär per Wischgeste oder bei Lifecycle-/Fokuswechsel
     * anzeigen. Der Helper wird beim Start, nach Navigation, bei Resume und nach
     * erneutem Window-Fokus aufgerufen, damit kein einzelner Screen die
     * Vollbildgarantie besitzen oder zurücksetzen muss.
     */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

/**
 * Führt die UI nach einem erfolgreichen App-Start-Reconnect zurück in den
 * Spielpfad.
 *
 * Der Server liefert nach dem Reconnect den fachlichen Kontext und die
 * Catch-up-Daten. Diese Activity übersetzt danach nur den fertigen UI-State in
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
