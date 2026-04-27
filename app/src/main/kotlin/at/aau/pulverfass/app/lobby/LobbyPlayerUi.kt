package at.aau.pulverfass.app.lobby

import at.aau.pulverfass.shared.ids.PlayerId

/**
 * UI-nahe Projektion eines Lobby-Mitglieds aus den Server-Events.
 *
 * Die fachlich gültige Identität bleibt die gemeinsame [PlayerId]; Anzeigename
 * und Host-Markierung werden nur für Lobby- und Spieloberfläche genutzt.
 */
data class LobbyPlayerUi(
    val playerId: PlayerId,
    val displayName: String,
    val isHost: Boolean = false,
)
