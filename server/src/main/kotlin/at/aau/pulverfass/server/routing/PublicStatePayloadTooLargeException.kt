package at.aau.pulverfass.server.routing

import at.aau.pulverfass.shared.message.protocol.NetworkMessagePayload

/**
 * Signalisiert, dass eine oeffentliche Snapshot-Payload die konfigurierte
 * Transportgrenze ueberschreitet.
 */
class PublicStatePayloadTooLargeException(
    payload: NetworkMessagePayload,
    val encodedSizeBytes: Int,
    val maxAllowedBytes: Int,
) : IllegalStateException(
        "Payload '${payload::class.simpleName}' ist mit $encodedSizeBytes Bytes " +
            "groesser als die konfigurierte Grenze von $maxAllowedBytes Bytes.",
    )
