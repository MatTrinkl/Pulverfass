package at.aau.pulverfass.shared.message.lobby.event

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Öffentliches Transport-Event über neu gewährte Verstärkungen zu Beginn oder während eines Zuges.
 *
 * @property lobbyCode betroffene Lobby
 * @property playerId Spieler, der die Verstärkungen erhält
 * @property amount Gesamtanzahl der gewährten Verstärkungen
 * @property territoryBonus Anteil aus Gebietsanzahl
 * @property continentBonus Anteil aus Kontinentboni
 * @property cardBonus Anteil aus Kartentausch
 */
@Serializable
data class ReinforcementsGrantedEvent(
    val lobbyCode: LobbyCode,
    val playerId: PlayerId,
    val amount: Int,
    val territoryBonus: Int,
    val continentBonus: Int,
    val cardBonus: Int,
) : PublicGameEvent {
    init {
        require(amount >= 0) {
            "ReinforcementsGrantedEvent.amount darf nicht negativ sein, war aber $amount."
        }
        require(territoryBonus >= 0) {
            "ReinforcementsGrantedEvent.territoryBonus darf nicht negativ sein, " +
                "war aber $territoryBonus."
        }
        require(continentBonus >= 0) {
            "ReinforcementsGrantedEvent.continentBonus darf nicht negativ sein, " +
                "war aber $continentBonus."
        }
        require(cardBonus >= 0) {
            "ReinforcementsGrantedEvent.cardBonus darf nicht negativ sein, war aber $cardBonus."
        }
        require(amount == territoryBonus + continentBonus + cardBonus) {
            "ReinforcementsGrantedEvent.amount muss der Summe der Boni entsprechen."
        }
    }
}

/**
 * Legacy-Serializer für [ReinforcementsGrantedEvent].
 */
object ReinforcementsGrantedEventSerializer :
    KSerializer<ReinforcementsGrantedEvent> by
    at.aau.pulverfass.shared.message.codec.LegacyGeneratedSerializer(
        ReinforcementsGrantedEvent.serializer(),
    )
