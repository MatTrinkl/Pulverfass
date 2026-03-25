package com.example.androidapp.ui

sealed class Screen(val route: String) {
    object Load : Screen("load")
    object Lobby : Screen("lobby")
    object Game : Screen("game")
}
