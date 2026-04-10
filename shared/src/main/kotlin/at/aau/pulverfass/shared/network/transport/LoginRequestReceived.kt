package at.aau.pulverfass.shared.network.transport

import at.aau.pulverfass.shared.ids.ConnectionId
import at.aau.pulverfass.shared.network.message.LoginRequest
import at.aau.pulverfass.shared.network.message.MessageType

/**
 * Erstes konkretes, bereits dekodiertes Nachrichtenevent als Vorlage für
 * weitere `MessageType`-spezifische Ereignisse.
 *
 * Jede künftig dekodierte Nachricht kann nach demselben Muster in eine eigene
 * Datei ausgelagert werden.
 */
data class LoginRequestReceived(
    override val connectionId: ConnectionId,
    override val payload: LoginRequest,
) : DecodedTransportEvent<LoginRequest> {
    override val messageType: MessageType = MessageType.LOGIN_REQUEST
}
