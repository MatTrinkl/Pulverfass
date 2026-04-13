package at.aau.pulverfass.shared.lobby.state

/**
 * Definiert ausschließlich lesenden Zugriff auf den aktuellen Lobbyzustand.
 *
 * Die Rückgabe erfolgt als Snapshot-Kopie, damit aufrufende Schichten den
 * internen State nicht über geteilte Referenzen verändern können.
 */
interface LobbyStateReader {
    fun currentState(): GameState
}
