# NetworkMessage Quick Reference

**TL;DR** - The essentials for implementing a new network message.

---

## Naming Convention

| Type | Pattern | Example |
|------|---------|---------|
| Client Request | `*Request` | `KickPlayerRequest` |
| Server Response | `*Response` | `KickPlayerResponse` |
| Error Response | `*ErrorResponse` | `KickPlayerErrorResponse` |
| Lobby Event | `*Event` | `PlayerKickedLobbyEvent` |
| Serializer | `*Serializer` | `KickPlayerRequestSerializer` |

---

## Quick Checklist

```kotlin
// 1. Payload Class
@Serializable(with = MyRequestSerializer::class)
data class MyRequest(
    val field1: Type1,
    val field2: Type2,
) : NetworkMessagePayload

// 2. Serializer
object MyRequestSerializer : KSerializer<MyRequest> {
    override val descriptor = buildClassSerialDescriptor(
        "at.aau.pulverfass.shared.message.lobby.request.MyRequest"
    ) {
        element("field1", Type1.serializer().descriptor)
        element("field2", Type2.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: MyRequest) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, Type1.serializer(), value.field1)
        composite.encodeSerializableElement(descriptor, 1, Type2.serializer(), value.field2)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): MyRequest {
        val composite = decoder.beginStructure(descriptor)
        var field1: Type1? = null
        var field2: Type2? = null

        loop@ while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> field1 = composite.decodeSerializableElement(descriptor, 0, Type1.serializer())
                1 -> field2 = composite.decodeSerializableElement(descriptor, 1, Type2.serializer())
                CompositeDecoder.DECODE_DONE -> break@loop
                else -> throw IllegalArgumentException("Unexpected index $index")
            }
        }

        composite.endStructure(descriptor)
        return MyRequest(
            field1 = field1 ?: throw MissingFieldException("field1", descriptor.serialName),
            field2 = field2 ?: throw MissingFieldException("field2", descriptor.serialName),
        )
    }
}

// 3. Tests
@Test
fun `MyRequest roundtrip serialization`() {
    val original = MyRequest(Type1(...), Type2(...))
    val json = Json.encodeToString(MyRequestSerializer, original)
    val decoded = Json.decodeFromString(MyRequestSerializer, json)
    assertEquals(original, decoded)
}

@Test
fun `MyRequest missing field1 throws exception`() {
    val json = """{"field2":"value"}"""
    assertThrows<MissingFieldException> {
        Json.decodeFromString(MyRequestSerializer, json)
    }
}

// 4. Register in NetworkPayloadRegistry
// In payloadTypeByClass:
MyRequest::class to MessageType.LOBBY_MY_REQUEST,

// In payloadSerializerByClass:
MyRequest::class to MyRequestSerializer,

// In payloadDeserializerByType:
MessageType.LOBBY_MY_REQUEST to MyRequestSerializer,

// 5. Add MessageType
enum class MessageType(val typeId: Int) {
    // ...
    LOBBY_MY_REQUEST(42),
}
```

---

## Golden Rules

| Rule | Why | Example |
|------|-----|---------|
| **FQCN Descriptor** | Protocol stability | `"at.aau.pulverfass.shared.message.lobby.request.MyRequest"` |
| **Field Order Fixed** | Never change indices | Index 0 = field1 forever |
| **Required Fields** | No defaults | `val field1: Type` not `val field1: Type?` |
| **MissingField Check** | Strict validation | `?: throw MissingFieldException(...)` |
| **Unexpected Index** | Forward compat | `else -> throw IllegalArgumentException(...)` |

---

## Common Mistakes ❌

| Mistake | Problem | Fix |
|---------|---------|-----|
| Reordering fields | Breaks old clients | Keep indices 0,1,2,... stable |
| Auto @Serializable | Implicit reordering | Use manual CustomSerializer |
| Nullable fields | Masks missing data | Only required (non-null) fields |
| No MissingField check | Silent corruption | `?: throw MissingFieldException(...)` |
| Wrong FQCN | Protocol incompatibility | Use full package path |
| Serializer class | State management | Use `object` (singleton) |

---

## File Locations

```
shared/src/main/kotlin/at/aau/pulverfass/shared/message/lobby/
├── request/MyRequest.kt                    ← Payload class
├── response/MyResponse.kt                  ← Response/Success
├── response/error/MyErrorResponse.kt       ← Error response
└── event/MyLobbyEvent.kt                   ← Broadcast event

shared/src/test/kotlin/at/aau/pulverfass/shared/network/message/
└── MyMessageTest.kt                        ← Tests
```

---

## Documentation References

| Document | When to Use |
|----------|------------|
| `docs/NETWORK_MESSAGES.md` | Full reference, detailed explanation |
| `docs/SERIALIZER_TEMPLATE.kt` | Copy-paste template with variations |
| `docs/PR_REVIEW_CHECKLIST.md` | Before merging your PR |
| `NetworkSerializerContractTest.kt` | Validates your implementation |

---

## Test Template

```kotlin
class MyMessageTest {
    @Test
    fun `MyRequest roundtrip`() {
        val original = MyRequest(...)
        val json = Json.encodeToString(MyRequestSerializer, original)
        val decoded = Json.decodeFromString(MyRequestSerializer, json)
        assertEquals(original, decoded)
    }

    @Test
    fun `MyRequest missing field throws`() {
        val json = """{"field2":"value"}"""
        assertThrows<MissingFieldException> {
            Json.decodeFromString(MyRequestSerializer, json)
        }
    }
    
    // Repeat for each field...
}
```

---

## Validation Checklist

Before you merge:

- [ ] Class named `*Request` / `*Response` / `*Event`
- [ ] Serializer is `object`, not `class`
- [ ] Descriptor uses FQCN format
- [ ] All required fields have MissingFieldException
- [ ] Unexpected indices throw IllegalArgumentException
- [ ] Roundtrip test exists
- [ ] MissingField tests for each field
- [ ] Registered in NetworkPayloadRegistry (3 places)
- [ ] MessageType enum value added
- [ ] Tests pass

---

## Questions?

See `docs/NETWORK_MESSAGES.md` → FAQ section

**Common questions:**
- "Can I reorder fields?" → NO
- "Can I add nullable fields?" → NO (affects validation)
- "Can I use auto-serializer?" → NO (implicit reordering risk)
- "What if I add new fields?" → Add at END, increment index
- "What if I need to remove a field?" → Keep entry, mark deprecated

