package at.aau.pulverfass.shared.message.codec

import kotlinx.serialization.json.Json

/**
 * Gemeinsame JSON-Konfiguration für das gesamte Wire-Protokoll.
 *
 * `ignoreUnknownKeys = true` macht das Protokoll vorwärtskompatibel: Ein neuerer
 * Server darf einer Payload zusätzliche Felder hinzufügen, ohne dass ein älterer
 * Client beim Deserialisieren hart abbricht. Felder, die der Empfänger nicht
 * kennt, werden still übersprungen statt als Fehler behandelt.
 *
 * Pflichtfelder bleiben weiterhin Pflicht -- fehlt ein nicht-defaultbares Feld,
 * schlägt die Deserialisierung bewusst weiterhin fehl.
 */
internal val NetworkJson: Json = Json { ignoreUnknownKeys = true }
