package at.aau.pulverfass.shared.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode

/**
 * Gemeinsamer Basistyp aller lobbybezogenen Domain-Events.
 *
 * Die Hierarchie ist bewusst transportunabhängig und modelliert nur fachnahe
 * Ereignisse des Lobby-Layers. Neue Events sollen unter einer der beiden
 * Unterhierarchien ergänzt werden, damit Consumer auf Root-Ebene stabil bleiben.
 */
sealed interface LobbyEvent {
    /**
     * Fachliche Lobby, auf die sich das Event bezieht.
     */
    val lobbyCode: LobbyCode
}

/**
 * Basistyp für von außen ausgelöste Lobby-Events.
 *
 * Diese Events repräsentieren fachlich relevante, von außen ausgelöste
 * Zustandsänderungen. Die Ableitung aus konkreten
 * [at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload]s erfolgt
 * bewusst in einer höheren Integrationsschicht und nicht im Domainmodell.
 */
sealed interface ExternalLobbyEvent : LobbyEvent

/**
 * Basistyp für interne System- und Lifecycle-Events einer Lobby.
 *
 * Diese Events entstehen innerhalb des Lobby-Layers und hängen nicht direkt von
 * Netzwerk- oder UI-Details ab.
 */
sealed interface InternalLobbyEvent : LobbyEvent
