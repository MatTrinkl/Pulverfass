package at.aau.pulverfass.server.lobby.mapping

import at.aau.pulverfass.shared.event.EventContext
import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.lobby.event.LobbyEvent

/**
 * Übersetzt technisch dekodierte Netzwerkrequests in Lobby-Domain-Events.
 */
fun interface NetworkToLobbyEventMapper {
    /**
     * Übersetzt einen dekodierten Netzwerkrequest in Lobby-Domain-Events.
     *
     * @throws NetworkToLobbyEventMappingException wenn die Nachricht nicht
     * konsistent oder nicht unterstützbar gemappt werden kann
     */
    fun map(request: DecodedNetworkRequest): MappedLobbyEvents
}

/**
 * Ergebnis eines erfolgreichen Netzwerk-zu-Domain-Mappings.
 */
data class MappedLobbyEvents(
    /** Ziel-Lobby für die erzeugten Domain-Events. */
    val lobbyCode: LobbyCode,
    /** Ergebnis-Events für den Lobby-Layer. */
    val events: List<LobbyEvent>,
    /** Durchzureichender technischer Kontext aus dem Request. */
    val context: EventContext,
)
