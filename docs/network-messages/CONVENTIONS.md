# Network Message Konventionen & CustomSerializer Template

**Status:** Bindende Konvention für alle Lobby-Messages (C2S/S2C/S2L)  
**Version:** 1.0  
**Gültig ab:** Feature/NetworkAPI-RouterAPI

## Inhaltsverzeichnis

1. [Namenskonventionen](#namenskonventionen)
2. [Paket-Struktur](#paket-struktur)
3. [Payload-Klassen-Template](#payload-klassen-template)
4. [CustomSerializer Vollständiges Template](#customserializer-vollständiges-template)
5. [Descriptor-Namensgebung (FQCN)](#descriptor-namensgebung-fqcn)
6. [Feldordnung & Index-Konsistenz](#feldordnung--index-konsistenz)
7. [Fehlerbehandlung](#fehlerbehandlung)
8. [Referenzimplementierungen](#referenzimplementierungen)
9. [Prüfliste für PR-Reviews](#prüfliste-für-pr-reviews)

---

## Namenskonventionen

### Payload-Klassen

| Nachrichtentyp | Naming Pattern | Beispiel | Verwendung |
|---|---|---|---|
| **C2S Request** | `*Request` | `KickPlayerRequest` | Client → Server, Aktion anfordern |
| **S2C Response** | `*Response` | `KickPlayerResponse` | Server → Client, Success-Ack |
| **S2C Error** | `*ErrorResponse` | `KickPlayerErrorResponse` | Server → Client, Fehler-Response |
| **S2L Event** | `*Event` | `PlayerKickedLobbyEvent` | Server → Lobby, Broadcast-Event |

### Serializer-Klassen

| Payload-Typ | Serializer-Name | Beispiel |
|---|---|---|
| Alle Typen | `<PayloadName>Serializer` | `KickPlayerRequestSerializer` |

**Wichtig:** Serializer sind **object** (Singleton), nicht `class`.

```kotlin
object KickPlayerRequestSerializer : KSerializer<KickPlayerRequest> { ... }
```

---

## Paket-Struktur

Alle NetworkMessage-Payloads müssen im folgenden Package liegen:

```
shared/src/main/kotlin/at/aau/pulverfass/shared/network/message/
├── request/          # C2S Requests
│   ├── KickPlayerRequest.kt
│   ├── StartGameRequest.kt
│   └── ...
├── response/         # S2C Responses & Error Responses
│   ├── KickPlayerResponse.kt
│   ├── KickPlayerErrorResponse.kt
│   ├── StartGameResponse.kt
│   ├── StartGameErrorResponse.kt
│   └── ...
└── event/            # S2L Lobby Events
    ├── PlayerKickedLobbyEvent.kt
    ├── GameStartedEvent.kt
    └── ...
```

**Regel:** Package-Struktur MUSS sich in Descriptor-FQCN widerspiegeln.

---

## Payload-Klassen-Template

```kotlin
import at.aau.pulverfass.shared.network.message.NetworkMessagePayload
import kotlinx.serialization.Serializable

@Serializable(with = ExampleRequestSerializer::class)
data class ExampleRequest(
    val lobbyCode: LobbyCode,
    val targetPlayerId: PlayerId,
) : NetworkMessagePayload
```

### Anforderungen

- **@Serializable Annotation:** Muss `with = <SerializerName>::class` enthalten
- **Vererbung:** Muss `NetworkMessagePayload` implementieren
- **Eigenschaften:** Nur Pflichtfelder ohne Defaults
- **Datentypen:** Nur serialisierbare Typen (LobbyCode, PlayerId, String, Int, etc.)
- **Nullable-Felder:** Vermeiden; falls nötig, explizit dokumentieren

---

## CustomSerializer Vollständiges Template

### Struktur und Erklärung

```kotlin
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.CompositeDecoder
import java.lang.IllegalArgumentException
import kotlinx.serialization.SerializationException

object ExampleRequestSerializer : KSerializer<ExampleRequest> {
    
    // === DESCRIPTOR ===
    // Definiert die Struktur für Serialisierungs-/Deserialisierungsprozess.
    // FQCN (Fully Qualified Class Name) muss stabil für Protokollkompatibilität sein.
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.ExampleRequest") {
            // element(name, serializer, ...)
            // Index 0, 1, 2, ... muss KONSISTENT bleiben!
            element("lobbyCode", LobbyCode.serializer().descriptor)
            element("targetPlayerId", PlayerId.serializer().descriptor)
        }

    // === SERIALIZE ===
    // Objekt → Encoded bytes/JSON
    override fun serialize(encoder: Encoder, value: ExampleRequest) {
        // beginStructure öffnet "Struktur-Schreib-Kontext"
        val composite = encoder.beginStructure(descriptor)
        
        // Jedes Feld manuell encoden
        // Index muss dem descriptor-element-Index entsprechen!
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, PlayerId.serializer(), value.targetPlayerId)
        
        // Struktur abschließen
        composite.endStructure(descriptor)
    }

    // === DESERIALIZE ===
    // Encoded bytes/JSON → Objekt
    override fun deserialize(decoder: Decoder): ExampleRequest {
        // beginStructure öffnet "Struktur-Lese-Kontext"
        val composite = decoder.beginStructure(descriptor)
        
        // Felder mit null initialisieren
        var lobbyCode: LobbyCode? = null
        var targetPlayerId: PlayerId? = null

        // Loop bis DECODE_DONE
        loop@ while (true) {
            // decodeElementIndex gibt nächsten verfügbaren Index zurück
            // oder DECODE_DONE (-1) wenn alles gelesen
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = composite.decodeSerializableElement(
                    descriptor, 0, LobbyCode.serializer()
                )
                1 -> targetPlayerId = composite.decodeSerializableElement(
                    descriptor, 1, PlayerId.serializer()
                )
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        // Struktur abschließen
        composite.endStructure(descriptor)

        // Fehlende Pflichtfelder validieren
        return ExampleRequest(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            targetPlayerId = targetPlayerId ?: throw MissingFieldException("targetPlayerId", descriptor.serialName),
        )
    }
}
```

### Warum diese Struktur?

| Element | Grund |
|---------|-------|
| `descriptor: SerialDescriptor` | Definiert Protokoll-Struktur; FQCN ermöglicht Protokoll-Kompatibilität über Versionen |
| `buildClassSerialDescriptor(FQCN)` | Eindeutige, stabile Identifikation der Nachricht im Netzwerk |
| `element(name, descriptor)` | Reihenfolge ist FEST; Index (0, 1, ...) darf sich nie ändern! |
| `encodeSerializableElement(index, ...)` | Manuelle Kontrolle über Feldordnung und Typen |
| `decodeElementIndex(descriptor)` | Dekodierer teilt Index mit; Loop bis DECODE_DONE |
| `MissingFieldException` | Validierung bei Deserialisierung; Fehler sofort erkannt |
| `IllegalArgumentException("Unexpected index")` | Sicherheitsprüfung gegen unbekannte Indizes (zukünftige Versionen) |

---

### Template mit verschiedenen Feldtypen

```kotlin
// PRIMITIV (String, Int, Boolean, etc.)
element("name", String.serializer().descriptor)
element("count", Int.serializer().descriptor)

// SERIALIZABLE Klasse (LobbyCode, PlayerId, etc.)
element("lobbyCode", LobbyCode.serializer().descriptor)

// List
element("playerIds", serializer<List<PlayerId>>().descriptor)

// Enum
element("reason", serializer<KickReason>().descriptor)

// ENCODING
composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
composite.encodeSerializableElement(descriptor, 1, String.serializer(), value.reason)
composite.encodeSerializableElement(descriptor, 2, serializer<List<PlayerId>>(), value.players)

// DECODING
lobbyCode = composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
reason = composite.decodeSerializableElement(descriptor, 1, String.serializer())
players = composite.decodeSerializableElement(descriptor, 2, serializer<List<PlayerId>>())
```

---

## Descriptor-Namensgebung (FQCN)

### Regel

Der Descriptor-Name MUSS die **Fully Qualified Class Name (FQCN)** sein:

```
at.aau.pulverfass.shared.network.message.<subpackage>.<ClassName>
```

### Beispiele

| Klasse | Location | Descriptor Name |
|--------|----------|-----------------|
| `KickPlayerRequest` | `shared/.../network/message/request/` | `at.aau.pulverfass.shared.network.message.request.KickPlayerRequest` |
| `KickPlayerResponse` | `shared/.../network/message/response/` | `at.aau.pulverfass.shared.network.message.response.KickPlayerResponse` |
| `PlayerKickedLobbyEvent` | `shared/.../network/message/event/` | `at.aau.pulverfass.shared.network.message.event.PlayerKickedLobbyEvent` |

### Warum FQCN?

- **Protokoll-Kompatibilität:** Eindeutige Identifikation über Versionen hinweg
- **Debugging:** Nachricht-Typ sofort erkennbar in Logs
- **Versionierung:** Falls Nachricht umbenannt wird, FQCN-Änderung ist explizit erkennbar
- **Cross-Service:** Microservices können Nachrichttypen zuverlässig erkennen

---

## Feldordnung & Index-Konsistenz

### Goldene Regel

**Feldordnung im Descriptor DARF SICH NIE ÄNDERN.**

Wenn `lobbyCode` Index 0 hat, muss es IMMER Index 0 sein, auch in zukünftigen Versionen.

### Warum?

```kotlin
// Alter Client, alte Struktur: (lobbyCode, targetPlayerId)
// Index:                        (0,         1)
val oldData = encode(KickPlayerRequest(...))

// Neuer Server mit neuer Struktur: (targetPlayerId, lobbyCode)
// Index:                            (0,             1)
// ❌ CRASH! Index 0 ist jetzt targetPlayerId, aber wir erwarten lobbyCode
decode(oldData)
```

### Best Practice

1. **Neue Felder:** Am Ende der `element()`-Liste hinzufügen
2. **Veraltete Felder:** Markieren als deprecated, nicht entfernen
3. **Dokumentation:** Kommentar mit versionierter Historie

```kotlin
buildClassSerialDescriptor("at.aau.pulverfass.shared.network.message.request.Example") {
    element("lobbyCode", LobbyCode.serializer().descriptor)      // v1.0, stable
    element("targetPlayerId", PlayerId.serializer().descriptor)  // v1.0, stable
    element("reason", String.serializer().descriptor)            // v1.1, added
    // element("deprecated_field", ...) /* v1.0-v1.0, removed in v1.2 */
}
```

---

## Fehlerbehandlung

### Pflichtfeld-Fehler

Wenn beim Deserialisieren ein Pflichtfeld fehlt → `MissingFieldException`:

```kotlin
return ExampleRequest(
    lobbyCode = lobbyCode ?: throw MissingFieldException(
        "lobbyCode", 
        descriptor.serialName
    ),
)
```

**Nachricht-Format:**  
`"Field 'lobbyCode' is missing. Descriptor: 'at.aau.pulverfass.shared.network.message.request.ExampleRequest'"`

### Unbekannte Indizes

Wenn Decoder Index liefert, der nicht in der Struktur definiert ist → `IllegalArgumentException`:

```kotlin
when (val index = composite.decodeElementIndex(descriptor)) {
    0 -> ...
    1 -> ...
    CompositeDecoder.DECODE_DONE -> break@loop
    else -> throw IllegalArgumentException("Unexpected index $index")
}
```

**Nachricht-Format:**  
`"Unexpected index 99 in descriptor 'at.aau.pulverfass.shared.network.message.request.ExampleRequest'"`

**Grund:** Schutz gegen zukünftige Versionen, die neue Felder hinzufügen könnten. Alte Dekodierer müssen sichere Fehler werfen, nicht Daten ignorieren.

---

## Referenzimplementierungen

### 1. GameJoinRequest (Einfach: 1 Feld)

**Payload:**
```kotlin
@Serializable(with = GameJoinRequestSerializer::class)
data class GameJoinRequest(
    val lobbyCode: LobbyCode,
) : NetworkMessagePayload
```

**Serializer:**
```kotlin
object GameJoinRequestSerializer : KSerializer<GameJoinRequest> {
    override val descriptor = buildClassSerialDescriptor(
        "at.aau.pulverfass.shared.network.message.request.GameJoinRequest"
    ) {
        element("lobbyCode", LobbyCode.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: GameJoinRequest) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): GameJoinRequest {
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
        return GameJoinRequest(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", descriptor.serialName)
        )
    }
}
```

### 2. KickPlayerRequest (Mittel: 3 Felder)

**Payload:**
```kotlin
@Serializable(with = KickPlayerRequestSerializer::class)
data class KickPlayerRequest(
    val lobbyCode: LobbyCode,
    val targetPlayerId: PlayerId,
    val requesterPlayerId: PlayerId,
) : NetworkMessagePayload
```

**Serializer:** (gekürzt)
```kotlin
object KickPlayerRequestSerializer : KSerializer<KickPlayerRequest> {
    override val descriptor = buildClassSerialDescriptor(
        "at.aau.pulverfass.shared.network.message.request.KickPlayerRequest"
    ) {
        element("lobbyCode", LobbyCode.serializer().descriptor)
        element("targetPlayerId", PlayerId.serializer().descriptor)
        element("requesterPlayerId", PlayerId.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: KickPlayerRequest) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, LobbyCode.serializer(), value.lobbyCode)
        composite.encodeSerializableElement(descriptor, 1, PlayerId.serializer(), value.targetPlayerId)
        composite.encodeSerializableElement(descriptor, 2, PlayerId.serializer(), value.requesterPlayerId)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): KickPlayerRequest {
        val composite = decoder.beginStructure(descriptor)
        var lobbyCode: LobbyCode? = null
        var targetPlayerId: PlayerId? = null
        var requesterPlayerId: PlayerId? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> lobbyCode = composite.decodeSerializableElement(descriptor, 0, LobbyCode.serializer())
                1 -> targetPlayerId = composite.decodeSerializableElement(descriptor, 1, PlayerId.serializer())
                2 -> requesterPlayerId = composite.decodeSerializableElement(descriptor, 2, PlayerId.serializer())
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return KickPlayerRequest(
            lobbyCode = lobbyCode ?: throw MissingFieldException("lobbyCode", descriptor.serialName),
            targetPlayerId = targetPlayerId ?: throw MissingFieldException("targetPlayerId", descriptor.serialName),
            requesterPlayerId = requesterPlayerId ?: throw MissingFieldException("requesterPlayerId", descriptor.serialName),
        )
    }
}
```

### 3. StartGameErrorResponse (Komplex: String-Feld + Fehlerbehandlung)

**Payload:**
```kotlin
@Serializable(with = StartGameErrorResponseSerializer::class)
data class StartGameErrorResponse(
    val reason: String,
) : NetworkMessagePayload
```

**Serializer:**
```kotlin
object StartGameErrorResponseSerializer : KSerializer<StartGameErrorResponse> {
    override val descriptor = buildClassSerialDescriptor(
        "at.aau.pulverfass.shared.network.message.response.StartGameErrorResponse"
    ) {
        element("reason", String.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: StartGameErrorResponse) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, String.serializer(), value.reason)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): StartGameErrorResponse {
        val composite = decoder.beginStructure(descriptor)
        var reason: String? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> reason = composite.decodeSerializableElement(descriptor, 0, String.serializer())
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return StartGameErrorResponse(
            reason = reason ?: throw MissingFieldException("reason", descriptor.serialName),
        )
    }
}
```

---

## Prüfliste für PR-Reviews

Vor dem Merge jeder PR mit neuen NetworkMessage-Payloads checken:

### Struktur & Naming
- [ ] Klasse folgt Naming-Konvention (`*Request`, `*Response`, `*Event`)
- [ ] Klasse liegt im korrekten Package (`shared/.../network/message/request/`, etc.)
- [ ] Klasse erbt von `NetworkMessagePayload`
- [ ] Serializer ist `object` (Singleton), nicht `class`
- [ ] Serializer-Name ist `<PayloadName>Serializer`

### Serializer-Implementierung
- [ ] `@Serializable(with = <SerializerName>::class)` auf Payload
- [ ] `descriptor` nutzt FQCN Format
- [ ] `element()` Ordnung ist dokumentiert und stabil
- [ ] `serialize()` nutzt `encodeSerializableElement` für alle Felder
- [ ] `deserialize()` hat Loop mit `DECODE_DONE` Exit
- [ ] **Alle Pflichtfelder** werfen `MissingFieldException` wenn null
- [ ] Unbekannte Indizes werfen `IllegalArgumentException("Unexpected index $index")`

### Tests
- [ ] Roundtrip-Test vorhanden (Serialize → Deserialize → Gleichheit)
- [ ] MissingField-Tests für alle Pflichtfelder
- [ ] Negative Tests für unerwartete Indizes (optional aber empfohlen)

### Registrierung
- [ ] Payload in `NetworkPayloadRegistry.payloadTypeByClass` registriert
- [ ] Serializer in `NetworkPayloadRegistry.payloadSerializerByClass` registriert
- [ ] Deserializer in `NetworkPayloadRegistry.payloadDeserializerByType` registriert
- [ ] `MessageType` enum hat entsprechenden Wert

### Dokumentation
- [ ] Payload-Klasse hat KDoc mit Zweck (C2S/S2C/S2L)
- [ ] Serializer kommentiert falls nicht-trivial
- [ ] Feldordnung dokumentiert (falls kritisch)

---

## Häufig gestellte Fragen

### Q: Warum manueller Serializer statt `@Serializable` Auto?
A: Explizite Kontrolle über Feldordnung (Protokoll-Kompatibilität), Error-Handling (MissingFieldException), und Index-Handling (Sicherheit gegen unerwartete Indizes). Auto-Serializer ändern implizit Feldordnung bei Refactorings.

### Q: Was passiert, wenn Feldordnung sich ändert?
A: **Protokoll-Bruch.** Alte Clients/Server können neue Nachrichten nicht lesen. FQCN muss dann geändert werden (z.B. `KickPlayerRequestV2`).

### Q: Können neue Felder hinzugefügt werden?
A: **Ja, aber nur am Ende.** Neue `element()` zum Descriptor hinzufügen, neuer Index. Alte Clients ignorieren neue Felder (DECODE_DONE stoppt Loop). Neue Clients mit alten Daten werfen `MissingFieldException` → Error-Response.

### Q: Warum `CompositeDecoder.DECODE_DONE`?
A: Zeigt dem Deserialisierer, dass kein weiterer Index verfügbar ist. Garantiert Loop-Abbruch. Ohne wird bei partiellen Daten gehängt.

### Q: Kann ich nullable Felder haben?
A: **Möglich, aber vermeiden.** Falls nötig: `lobbyCode: LobbyCode? = null` in Payload, in Deserializer `lobbyCode ?: defaultValue` statt MissingFieldException.

### Q: Muss jede Message 3 Serializer haben (Request/Response/Error)?
A: **Nein.** Request hat 1 Serializer. Response hat 1 Serializer. ErrorResponse hat 1 Serializer. Events haben 1 Serializer. Unterschiedliche Payloads = unterschiedliche Serializer.

---

## Zusammenfassung

| Aspekt | Regel |
|--------|-------|
| **Naming** | *Request, *Response, *Event, *Serializer |
| **Package** | `at.aau.pulverfass.shared.network.message.<subpackage>` |
| **Serializer** | `object XSerializer : KSerializer<X>` mit manuellem encode/decode |
| **Descriptor** | FQCN, stabile Feldordnung, keine Änderungen |
| **Fehler** | `MissingFieldException` für Pflichtfelder, `IllegalArgumentException` für unbekannte Indizes |
| **Tests** | Roundtrip + MissingField für jede Payload |
| **Registrierung** | Alle 3 Maps in `NetworkPayloadRegistry` + `MessageType` enum |

