package at.aau.pulverfass.shared.network.message

/**
 * Marker-Interface für alle fachlichen Payloads, die über das Netzwerkprotokoll
 * übertragen werden dürfen.
 *
 * Jede konkrete Nachricht wird als eigene Implementierung dieses Interfaces
 * modelliert und zusätzlich in der Payload-Registry registriert.
 */
interface NetworkMessagePayload
