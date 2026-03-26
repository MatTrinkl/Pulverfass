package at.aau.pulverfass.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import at.aau.pulverfass.app.ui.navigation.Screen
import at.aau.pulverfass.app.ui.screens.GameScreen
import at.aau.pulverfass.app.ui.screens.LoadScreen
import at.aau.pulverfass.app.ui.screens.LobbyScreen
import at.aau.pulverfass.app.ui.screens.WaitingRoomScreen
import at.aau.pulverfass.app.ui.theme.AndroidAppTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Load.route,
                        ) {
                            composable(Screen.Load.route) {
                                LoadScreen(navController)
                            }
                            composable(Screen.Lobby.route) {
                                LobbyScreen(navController)
                            }
                            composable(
                                route = Screen.WaitingRoom.route +
                                    "/{lobbyCode}/{isHost}/{playerName}",
                                arguments = listOf(
                                    navArgument("lobbyCode") { type = NavType.StringType },
                                    navArgument("isHost") { type = NavType.BoolType },
                                    navArgument("playerName") { type = NavType.StringType },
                                ),
                            ) { backStackEntry ->
                                val args = backStackEntry.arguments
                                val lobbyCode = args?.getString("lobbyCode") ?: ""
                                val isHost = args?.getBoolean("isHost") ?: false
                                val playerName = args?.getString("playerName") ?: ""
                                WaitingRoomScreen(navController, lobbyCode, isHost, playerName)
                            }
                            composable(Screen.Game.route) {
                                GameScreen()
                            }
                        }

                        // Language Toggle Button (nur in LobbyScreen)
                        if (currentRoute == Screen.Lobby.route) {
                            Button(
                                onClick = {
                                    val locales = AppCompatDelegate.getApplicationLocales()
                                    val currentLocale = locales[0]?.language
                                    val nextLocale = if (currentLocale == "de") "en" else "de"
                                    val appLocale = LocaleListCompat.forLanguageTags(nextLocale)
                                    AppCompatDelegate.setApplicationLocales(appLocale)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp),
                            ) {
                                Text(text = stringResource(id = R.string.toggle_language))
                            }
                        }
                    }
                }
            }
        }
    }
}
