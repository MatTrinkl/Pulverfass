package com.example.androidapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.androidapp.R
import com.example.androidapp.ui.navigation.Screen
import com.example.shared.Constants
import kotlinx.coroutines.delay

@Composable
fun LoadScreen(navController: NavController) {
    LaunchedEffect(Unit) {
        delay(2000) // Simulierter Delay
        navController.navigate(Screen.Lobby.route) {
            popUpTo(Screen.Load.route) { inclusive = true }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val appName = stringResource(id = R.string.app_name)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "v${Constants.APP_VERSION}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "${stringResource(id = R.string.loading)}…")
        }
    }
}
