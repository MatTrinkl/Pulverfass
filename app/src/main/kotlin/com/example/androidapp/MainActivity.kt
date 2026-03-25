package com.example.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.androidapp.ui.Screen
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.shared.Constants

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidAppTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Load.route,
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable(Screen.Load.route) {
                            LoadScreen()
                        }
                        composable(Screen.Lobby.route) {
                            LobbyScreen()
                        }
                        composable(Screen.Game.route) {
                            GameScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val appName = stringResource(id = R.string.app_name)
        Text(text = "Loading $appName v${Constants.APP_VERSION}")
    }
}

@Composable
fun LobbyScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Game Lobby")
    }
}

@Composable
fun GameScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Game Map")
    }
}
