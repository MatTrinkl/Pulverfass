package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.message.connection.ConnectionStatus

/**
 * UI-nahe Projektion eines Lobby-Mitglieds aus den Server-Events.
 *
 * Die fachlich gültige Identität bleibt die gemeinsame [PlayerId]; Anzeigename
 * und Host-Markierung werden nur für Lobby- und Spieloberfläche genutzt.
 *
 * @property playerId stabile fachliche Spieleridentität
 * @property displayName aktuell anzuzeigender Spielername
 * @property isHost markiert den Lobby-Owner in der Oberfläche
 * @property connectionStatus aktueller Verbindungsstatus aus Server-Events
 */
data class LobbyPlayerUi(
    val playerId: PlayerId,
    val displayName: String,
    val isHost: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,
    val characterId: String? = null,
) {
    val isDisconnected: Boolean
        get() = connectionStatus == ConnectionStatus.DISCONNECTED
}
