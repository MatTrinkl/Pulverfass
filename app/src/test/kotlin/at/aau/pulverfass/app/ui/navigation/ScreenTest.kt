package at.aau.pulverfass.app.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `game screen has correct route`() {
        assertEquals("game", Screen.Game.route)
    }
}
