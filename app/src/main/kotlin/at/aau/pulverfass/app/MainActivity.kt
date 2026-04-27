package at.aau.pulverfass.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import at.aau.pulverfass.app.lobby.LobbyController
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.app.ui.screens.GameScreen
import at.aau.pulverfass.app.ui.screens.LoadGameScreen
import at.aau.pulverfass.app.ui.screens.LoadScreen
import at.aau.pulverfass.app.ui.screens.LobbyScreen
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
        enableEdgeToEdge()
        setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                val lobbyController = remember { LobbyController() }

                DisposableEffect(Unit) {
                    onDispose {
                        lobbyController.close()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        // definiert alle verfügbaren routen & ziele
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Load.route,
                        ) {
                            composable(Screen.Load.route) {
                                LoadScreen(navController)
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
                            // warteraum mit übergabe von parametern wie lobbycode & name
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
}
