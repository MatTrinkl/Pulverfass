package at.aau.pulverfass.client.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sichert die stabilen Routen der App-Navigation ab.
 *
 * Diese Strings sind Teil der internen Navigation zwischen Ladebildschirm,
 * Lobby, Warteraum, Spielstart und Hauptspiel.
 */
class ScreenTest {
    @Test
    fun `load screen has correct route`() {
        assertEquals("load", Screen.Load.route)
    }

    @Test
    fun `lobby screen has correct route`() {
        assertEquals("lobby", Screen.Lobby.route)
    }

    @Test
    fun `waiting room screen has correct route`() {
        assertEquals("waiting_room", Screen.WaitingRoom.route)
    }

    @Test
    fun `load game screen has correct route`() {
        assertEquals("load_game", Screen.LoadGame.route)
    }

    @Test
    fun `game screen has correct route`() {
        assertEquals("game", Screen.Game.route)
    }
}
