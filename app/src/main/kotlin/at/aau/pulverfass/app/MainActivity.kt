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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.lobby.LobbyUiState
import at.aau.pulverfass.app.storage.SharedPreferencesReconnectSessionStore
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.app.ui.navigation.canAutoNavigateToRestoredGame
import at.aau.pulverfass.app.ui.navigation.restoredGameNavigationTarget
import at.aau.pulverfass.app.ui.screens.GameScreen
import at.aau.pulverfass.app.ui.screens.LoadGameScreen
import at.aau.pulverfass.app.ui.screens.LoadScreen
import at.aau.pulverfass.app.ui.screens.LobbyScreen
import at.aau.pulverfass.app.ui.screens.MainMenuScreen
import at.aau.pulverfass.app.ui.screens.StudioIntroScreen
import at.aau.pulverfass.app.ui.screens.WaitingRoomScreen
import at.aau.pulverfass.app.ui.theme.AndroidAppTheme

/**
 * Compose-basierter Einstiegspunkt der Android-App.
 *
 * Die Activity initialisiert aktuell genau eine [LobbyController]-Instanz,
 * verdrahtet die Navigationsziele des Lobby-Flows und gibt den Controller beim
 * Verlassen der Composition wieder frei.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen Immersive: System-Bars verstecken (nutzt hideSystemBars())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        enableEdgeToEdge()
        setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                val reconnectSessionStore =
                    remember {
                        SharedPreferencesReconnectSessionStore(applicationContext)
                    }
                val lobbyController =
                    remember {
                        LobbyController(reconnectSessionStore = reconnectSessionStore)
                    }
                val lobbyState by lobbyController.state.collectAsState()

                // === IMMERSIVE ENFORCEMENT ON EVERY NAV CHANGE ===
                val view = LocalView.current
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                LaunchedEffect(currentBackStackEntry) {
                    val window = (view.context as Activity).window
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    val controller = WindowInsetsControllerCompat(window, window.decorView)
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
                                        // in planung
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
                                GameScreen(controller = lobbyController)
                            }
                        }
                    }
                }
            }
        }
    }

    // Fix für das Problem das Screen Videos nicht vollscreen sind und der Content sich nicht über den ganzen screen anpasst
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
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
