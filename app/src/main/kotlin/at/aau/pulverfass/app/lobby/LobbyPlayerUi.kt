package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.shared.ids.PlayerId

/**
 * UI-nahe Projektion eines Lobby-Mitglieds aus den Server-Events.
 *
 * Die fachlich gültige Identität bleibt die gemeinsame [PlayerId]; Anzeigename
 * und Host-Markierung werden nur für Lobby- und Spieloberfläche genutzt.
 *
 * @property playerId stabile fachliche Spieleridentität
 * @property displayName aktuell anzuzeigender Spielername
 * @property isHost markiert den Lobby-Owner in der Oberfläche
 * @property isDisconnected markiert bekannte Verbindungsverluste im Warteraum
 */
data class LobbyPlayerUi(
    val playerId: PlayerId,
    val displayName: String,
    val isHost: Boolean = false,
    val isDisconnected: Boolean = false,
    val characterId: String? = null,
)
