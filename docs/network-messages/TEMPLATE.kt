// ============================================================================
// SERIALIZER TEMPLATE - Copy-Paste Ready
// ============================================================================
// This is a complete working template for any NetworkMessage payload.
// Replace "ExampleRequest" with your actual payload class name everywhere.
// See docs/NETWORK_MESSAGES.md for detailed explanation.
// ============================================================================

package at.aau.pulverfass.shared.network.message.request

import at.aau.pulverfass.shared.ids.LobbyCode
import at.aau.pulverfass.shared.ids.PlayerId
import at.aau.pulverfass.shared.network.message.NetworkMessagePayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.SerializationException

// ============================================================================
// PAYLOAD CLASS
// ============================================================================

/**
 * [CLIENT TO SERVER] Example request
 * 
 * This message is sent by the client to request an action.
 * Only include MANDATORY fields.
 */
@Serializable(with = ExampleRequestSerializer::class)
data class ExampleRequest(
    val lobbyCode: LobbyCode,
    val targetPlayerId: PlayerId,
) : NetworkMessagePayload

// ============================================================================
// SERIALIZER
// ============================================================================

/**
 * Serializes/Deserializes ExampleRequest with explicit field control.
 * 
 * WHY MANUAL SERIALIZER?
 * - Explicit field ordering (protocol stability across versions)
 * - Custom error handling (MissingFieldException for required fields)
 * - Protection against unknown indices (forward compatibility)
 * 
 * WHY NOT AUTO @Serializable?
 * - Auto serializers can reorder fields on refactoring
 * - No validation on missing fields
 * - No control over unknown fields from future versions
 */
object ExampleRequestSerializer : KSerializer<ExampleRequest> {

    // ========================================================================
    // DESCRIPTOR: Defines the message structure for serialization
    // ========================================================================
    
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.request.ExampleRequest") {
            // IMPORTANT: Order is FINAL. Never change this list.
            // New fields: ALWAYS add at the end.
            // Removed fields: Keep entry but mark as deprecated (DO NOT remove).
            
            // Format: element("fieldName", fieldSerializerDescriptor)
            // Index 0 = lobbyCode
            // Index 1 = targetPlayerId
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("targetPlayerId", PlayerId.serializer().descriptor)
        }

    // ========================================================================
    // SERIALIZE: Object → Encoded Bytes/JSON
    // ========================================================================
    
    override fun serialize(encoder: Encoder, value: ExampleRequest) {
        // beginStructure: Opens a "structure write context"
        val composite = encoder.beginStructure(descriptor)

        // Encode each field manually in order.
        // Index MUST match descriptor.element index.
        // Format: encodeSerializableElement(descriptor, INDEX, serializer, value)
        
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, PlayerId.serializer(), value.targetPlayerId)

        // endStructure: Finalize encoding
        composite.endStructure(descriptor)
    }

    // ========================================================================
    // DESERIALIZE: Encoded Bytes/JSON → Object
    // ========================================================================
    
    override fun deserialize(decoder: Decoder): ExampleRequest {
        // beginStructure: Opens a "structure read context"
        val composite = decoder.beginStructure(descriptor)

        // Initialize all fields to null
        // We'll check for missing fields after the loop
        var lobbyCode: LobbyCode? = null
        var targetPlayerId: PlayerId? = null

        // Loop until all elements are decoded
        // The decoder tells us which index to read next via decodeElementIndex()
        loop@ while (true) {
            // decodeElementIndex() returns:
            // - The next available index (0, 1, 2, ...)
            // - Or CompositeDecoder.DECODE_DONE (-1) when finished
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> {
                    // Decode field at index 0 (lobbyCode)
                    // Format: decodeSerializableElement(descriptor, INDEX, serializer)
                    lobbyCode = composite.decodeSerializableElement(
                        descriptor, 0, LobbyCode.serializer()
                    )
                }
                1 -> {
                    // Decode field at index 1 (targetPlayerId)
                    targetPlayerId = composite.decodeSerializableElement(
                        descriptor, 1, PlayerId.serializer()
                    )
                }
                CompositeDecoder.DECODE_DONE -> {
                    // All fields decoded, exit loop
                    break@loop
                }
                else -> {
                    // Unknown index: throw error
                    // This protects against future versions with new fields
                    throw IllegalArgumentException(
                        "Unexpected index $index in descriptor '${descriptor.serialName}'"
                    )
                }
            }
        }

        // Finalize deserialization
        composite.endStructure(descriptor)

        // ====================================================================
        // VALIDATION: Check all required fields are present
        // ====================================================================
        
        return ExampleRequest(
            // If field is null, it was missing in the encoded data
            // Throw MissingFieldException to signal protocol error
            lobbyCode = lobbyCode ?: throw MissingFieldException(
                "lobbyCode", descriptor.serialName
            ),
            targetPlayerId = targetPlayerId ?: throw MissingFieldException(
                "targetPlayerId", descriptor.serialName
            ),
        )
    }
}

// ============================================================================
// NOTES FOR VARIATIONS
// ============================================================================

/*
VARIATION 1: Simple payload with 1 field
=======================================
@Serializable(with = SimpleRequestSerializer::class)
data class SimpleRequest(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload

object SimpleRequestSerializer : KSerializer<SimpleRequest> {
    override val descriptor = buildClassSerialDescriptor(
        "at.aau.pulverfass.shared.network.message.request.SimpleRequest"
    ) {
        element("lobbyCode", LobbyCode.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: SimpleRequest) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): SimpleRequest {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return SimpleRequest(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
        )
    }
}


VARIATION 2: With String field
===============================
@Serializable(with = ReasonRequestSerializer::class)
data class ReasonRequest(
    val lobbyCode: LobbyCode,
    val reason: String,  // String field
) : NetworkMessagePayload

object ReasonRequestSerializer : KSerializer<ReasonRequest> {
    override val descriptor = buildClassSerialDescriptor(
        "at.aau.pulverfass.shared.network.message.request.ReasonRequest"
    ) {
        element("lobbyCode", LobbyCode.serializer().descriptor)
        element("reason", String.serializer().descriptor)  // String descriptor
    }

    override fun serialize(encoder: Encoder, value: ReasonRequest) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, String.serializer(), value.reason)  // String serializer
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ReasonRequest {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var reason: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
                1 -> reason = composite.decodeSerializableElement(descriptor, 1, String.serializer())  // String deserialize
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return ReasonRequest(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
        )
    }
}


VARIATION 3: Empty payload (only for Success/Ack responses)
===========================================================
@Serializable(with = SimpleResponseSerializer::class)
object SimpleResponse : NetworkMessagePayload

object SimpleResponseSerializer : KSerializer<SimpleResponse> {
    override val descriptor = buildClassSerialDescriptor(
        "at.aau.pulverfass.shared.network.message.response.SimpleResponse"
    ) {
        // No elements for empty payload
    }

    override fun serialize(encoder: Encoder, value: SimpleResponse) {
        val composite = encoder.beginStructure(descriptor)
        composite.endStructure(descriptor)  // Nothing to encode
    }

    override fun deserialize(decoder: Decoder): SimpleResponse {
        val composite = decoder.beginStructure(descriptor)

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return SimpleResponse  // Return singleton
    }
}


VARIATION 4: With List field (e.g., List<PlayerId>)
====================================================
@Serializable(with = PlayerListRequestSerializer::class)
data class PlayerListRequest(
    val lobbyCode: LobbyCode,
    val playerIds: List<PlayerId>,  // List field
) : NetworkMessagePayload

object PlayerListRequestSerializer : KSerializer<PlayerListRequest> {
    override val descriptor = buildClassSerialDescriptor(
        "at.aau.pulverfass.shared.network.message.request.PlayerListRequest"
    ) {
        element("lobbyCode", LobbyCode.serializer().descriptor)
        element("playerIds", serializer<List<PlayerId>>().descriptor)  // List descriptor
    }

    override fun serialize(encoder: Encoder, value: PlayerListRequest) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, serializer<List<PlayerId>>(), value.playerIds)  // List serializer
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): PlayerListRequest {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var playerIds: List<PlayerId>? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
                1 -> playerIds = composite.decodeSerializableElement(descriptor, 1, serializer<List<PlayerId>>())  // List deserialize
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return PlayerListRequest(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            playerIds = playerIds ?: throw MissingFieldException("playerIds", descriptor.serialName),
        )
    }
}


VARIATION 5: Response with different field types
==================================================
See docs/NETWORK_MESSAGES.md for "Referenzimplementierungen" section.
*/
