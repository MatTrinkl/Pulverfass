package at.aau.pulverfass.shared.network

/**
 * Diese Klasse ist das Netzwerkpaket selbst welches deserialisiert, übertragen und serialisiert wird.
 *
 * @property header Definiert welchen MessageType dieses Paket hat.
 * @property payload Der eigentliche Inhalt des Paketes.
 */
data class NetworkPacket<T : NetworkMessagePayload>(
    val header: MessageHeader,
    val payload: T,
)
