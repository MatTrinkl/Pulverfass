package at.aau.pulverfass.shared.message.lobby.response

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.message.lobby.PublicGameStateWireSnapshot
import at.aau.pulverfass.shared.message.lobby.event.PublicGameStatePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Autoritative Catch-up-Antwort mit vollständigem öffentlichem GameState-Snapshot.
 *
 * Die Struktur entspricht absichtlich dem Full-Snapshot-Broadcast beim Turnwechsel,
 * wird hier aber als direkte S2C-Antwort auf eine explizite Catch-up-Anfrage genutzt.
 */
@Serializable(with = GameStateCatchUpResponseSerializer::class)
data class GameStateCatchUpResponse(
    val lobbyCode: LobbyCode,
    val stateVersion: Long,
    val determinism: PublicDeterminismMetadataSnapshot,
    val turnState: PublicTurnStateSnapshot,
    val definition: MapDefinitionSnapshot,
    val territoryStates: List<MapTerritoryStateSnapshot>,
) : PublicGameStatePayload {
    companion object {
        fun from(snapshot: PublicGameStateSnapshot): GameStateCatchUpResponse =
            GameStateCatchUpResponse(
                lobbyCode = snapshot.lobbyCode,
                stateVersion = snapshot.stateVersion,
                determinism = snapshot.determinism,
                turnState = snapshot.turnState,
                definition = snapshot.definition,
                territoryStates = snapshot.territoryStates,
            )
    }
}

/**
 * Technischer Serializer fuer [GameStateCatchUpResponse].
 */
object GameStateCatchUpResponseSerializer : KSerializer<GameStateCatchUpResponse> {
    private val wireSerializer = PublicGameStateWireSnapshot.serializer()

    override val descriptor = wireSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: GameStateCatchUpResponse,
    ) {
        wireSerializer.serialize(
            encoder,
            PublicGameStateWireSnapshot(
                lobbyCode = value.lobbyCode,
                stateVersion = value.stateVersion,
                determinism = value.determinism,
                turnState = value.turnState,
                definition = value.definition,
                territoryStates = value.territoryStates,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): GameStateCatchUpResponse {
        val wire = wireSerializer.deserialize(decoder)
        return GameStateCatchUpResponse(
            lobbyCode = wire.lobbyCode,
            stateVersion = wire.stateVersion,
            determinism = wire.determinism,
            turnState = wire.turnState,
            definition = wire.definition,
            territoryStates = wire.territoryStates,
        )
    }
}
