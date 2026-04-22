package at.aau.pulverfass.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

// testet ob alle bildschirmrouten korrekt definiert sind
class ScreenTest {
    @Test
    fun `load screen has correct route`() {
        // prüft die route für ladebildschirm
        assertEquals("load", Screen.Load.route)
    }

    @Test
    fun `lobby screen has correct route`() {
        // prüft die route für lobby
        assertEquals("lobby", Screen.Lobby.route)
    }

    @Test
    fun `waiting room screen has correct route`() {
        // prüft die route für warteraum
        assertEquals("waiting_room", Screen.WaitingRoom.route)
    }

    @Test
    fun `load game screen has correct route`() {
        // prüft die route für spielvorbereitung
        assertEquals("load_game", Screen.LoadGame.route)
    }

    @Test
    fun `game screen has correct route`() {
        // prüft die route für hauptspiel
        assertEquals("game", Screen.Game.route)
    }
}
