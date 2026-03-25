package com.example.androidapp

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
import com.example.androidapp.ui.navigation.Screen
import com.example.androidapp.ui.screens.GameScreen
import com.example.androidapp.ui.screens.LoadScreen
import com.example.androidapp.ui.screens.LobbyScreen
import com.example.androidapp.ui.screens.WaitingRoomScreen
import com.example.androidapp.ui.theme.AndroidAppTheme

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
                                route = Screen.WaitingRoom.route + "/{lobbyCode}/{isHost}/{playerName}",
                                arguments = listOf(
                                    navArgument("lobbyCode") { type = NavType.StringType },
                                    navArgument("isHost") { type = NavType.BoolType },
                                    navArgument("playerName") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val lobbyCode = backStackEntry.arguments?.getString("lobbyCode") ?: ""
                                val isHost = backStackEntry.arguments?.getBoolean("isHost") ?: false
                                val playerName = backStackEntry.arguments?.getString("playerName") ?: ""
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
                                    val currentLocale = AppCompatDelegate.getApplicationLocales()[0]?.language
                                    val nextLocale = if (currentLocale == "de") "en" else "de"
                                    val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(nextLocale)
                                    AppCompatDelegate.setApplicationLocales(appLocale)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
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
