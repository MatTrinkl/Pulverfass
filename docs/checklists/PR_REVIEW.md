# PR Review Checklist

**Last Updated:** 2026-04-14  
**Version:** 2.0

Use this checklist before merging any Pull Request.

## General Checks

- [ ] PR title is descriptive
- [ ] Commits are logically organized
- [ ] No unnecessary files (debug logs, IDE configs, etc.)
- [ ] All tests pass locally
- [ ] No unresolved conflicts

## Code Quality

- [ ] Code follows team style guide
- [ ] No unused imports
- [ ] No commented-out code
- [ ] No hardcoded values (use constants)
- [ ] Error messages are clear and helpful
- [ ] No secrets/credentials committed

## Kotlin-Specific

- [ ] Uses appropriate visibility modifiers (private preferred)
- [ ] No deprecated APIs without justification
- [ ] Data classes use `data class` (not manual equals/hashCode)
- [ ] Null safety: no unnecessary `!!` operators
- [ ] No raw types (use generics)

## Tests

- [ ] New code has test coverage
- [ ] Tests are named descriptively
- [ ] Tests verify behavior, not implementation
- [ ] No flaky or timing-dependent tests
- [ ] Edge cases are tested

## Documentation

- [ ] Code comments for non-obvious logic
- [ ] Public functions/classes have KDoc
- [ ] README updated if necessary
- [ ] Changes documented if user-visible

## Network Messages (NEW!)

**Applies to:** Any PR adding/modifying NetworkMessage payloads or Serializers

### Payload Classes
- [ ] Class name follows convention: `*Request`, `*Response`, `*Event`
- [ ] Package location: `at.aau.pulverfass.shared.message.lobby.{request,response,event}`
- [ ] Inherits from `NetworkMessagePayload`
- [ ] Only required fields (no defaults)
- [ ] Has `@Serializable(with = <SerializerName>::class)` annotation

### CustomSerializer Implementation
- [ ] Serializer is `object` (singleton), not `class`
- [ ] Serializer name: `<PayloadName>Serializer`
- [ ] **Descriptor FQCN format:** `"at.aau.pulverfass.shared.message..."`
- [ ] **Descriptor elements:** Stable ordering, documented, never reordered
- [ ] **Serialize method:** Uses `beginStructure()` / `encodeSerializableElement()` / `endStructure()`
- [ ] **Deserialize method:**
  - [ ] Has `loop@ while(true)` with `DECODE_DONE` exit
  - [ ] Uses `when` statement for index routing
  - [ ] Has `else -> throw IllegalArgumentException("Unexpected index $index")`
  - [ ] **ALL required fields** throw `MissingFieldException` if null
  - [ ] Exception includes field name + descriptor.serialName

### Tests
- [ ] **Roundtrip test** for each payload (encode → decode → equals)
- [ ] **MissingField tests** for each required field
- [ ] Tests verify error messages are helpful
- [ ] Negative tests for malformed data (optional but encouraged)

### Registration
- [ ] Payload added to `NetworkPayloadRegistry.payloadTypeByClass`
- [ ] Serializer added to `NetworkPayloadRegistry.payloadSerializerByClass`
- [ ] Deserializer added to `NetworkPayloadRegistry.payloadDeserializerByType`
- [ ] `MessageType` enum has corresponding value

### Documentation
- [ ] Payload has KDoc: describes purpose (C2S/S2C/S2L)
- [ ] Payload fields documented: `@property fieldName description`
- [ ] Serializer has explanation comment if non-standard
- [ ] Fieldorder documented if critical

## References

- **Network Message Conventions:** See `docs/NETWORK_MESSAGES.md`
- **Serializer Template:** See `docs/SERIALIZER_TEMPLATE.kt`
- **Message Compliance Report:** See `docs/MESSAGE_COMPLIANCE_REPORT.md`

---

**Questions?** See docs/NETWORK_MESSAGES.md FAQ section.
