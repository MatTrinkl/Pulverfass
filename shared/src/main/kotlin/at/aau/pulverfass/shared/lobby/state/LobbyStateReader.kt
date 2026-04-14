package at.aau.pulverfass.shared.lobby.state

/**
 * Definiert ausschließlich lesenden Zugriff auf den aktuellen Lobbyzustand.
 *
 * Die Rückgabe erfolgt als Snapshot-Kopie, damit aufrufende Schichten den
 * internen State nicht über geteilte Referenzen verändern können.
 */
fun interface LobbyStateReader {
    /**
     * Liefert den aktuell verfügbaren Lobbyzustand als read-only Snapshot.
     *
     * Aufrufende dürfen das Ergebnis nur lesend verwenden. Mutationen am
     * Runtime-State sind ausschließlich über den Eventpfad vorgesehen.
     */
    fun currentState(): GameState
}
