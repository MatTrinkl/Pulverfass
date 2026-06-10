# Design: iOS-Port via Compose Multiplatform

**Datum:** 2026-06-10
**Status:** Freigegeben (Brainstorming abgeschlossen)

## Ziel

Die bestehende Android-App (`app`) bekommt einen iOS-Port mit Feature-Parität und
identischem Aussehen. Die Programmiersprache bleibt Kotlin. Das bestehende
`app`-Modul wird dabei **nicht verändert**.

## Rahmenbedingungen (geklärt)

- Ein Mac mit Xcode ist im Team verfügbar.
- Zielplattform: echte iPhones/iPads per Dev-Build (Xcode-Sideload, kostenloser
  Apple-Account reicht; Builds laufen nach 7 Tagen ab).
- Das Android-`app`-Modul bleibt unangetastet; die UI wird in ein neues Modul
  **kopiert**, nicht verschoben.
- Das `shared`-Modul darf auf Kotlin Multiplatform umgestellt werden.

## Gewählter Ansatz

Neues Compose-Multiplatform-Modul `client` mit **Android- und iOS-Target**.
Begründung gegenüber einem iOS-only-Modul: Das Team arbeitet primär auf Windows —
mit einem Android-Target kann die portierte UI am Emulator entwickelt und getestet
werden; der Mac wird nur für iOS-Builds und finale Verifikation gebraucht.
Außerdem kann `app` später per Dependency-Tausch auf das gemeinsame Modul
umsteigen, womit die Duplizierung endet.

Verworfene Alternativen:

- **iOS-only-Compose-Modul:** weniger Build-Setup, aber jede Iteration bräuchte
  den Mac; Duplizierung wäre permanent.
- **SwiftUI + shared-KMP:** verletzt „Kotlin bleiben“; identisches Aussehen wäre
  Handarbeit.

## Modulstruktur

```
SE2Risiko/
├── app/      → UNVERÄNDERT (bestehender Android-Client)
├── server/   → UNVERÄNDERT (konsumiert weiter das JVM-Target von shared)
├── shared/   → wird KMP: commonMain + jvmMain + iosMain
├── client/   → NEU: Compose-Multiplatform-Modul (Android + iOS)
│   ├── commonMain/   UI (Screens, Components, Theme, Map, Navigation),
│   │                 Lobby-/Game-Logik, Netzwerk-Stack,
│   │                 composeResources (Bilder, Fonts, Videos, Musik)
│   ├── androidMain/  VideoView, MediaPlayer, SharedPreferences,
│   │                 SensorManager, Ktor-CIO — 1:1-Kopien aus app
│   └── iosMain/      AVPlayer, AVAudioPlayer, NSUserDefaults,
│                     CoreMotion, Ktor-Darwin
└── iosApp/   → NEU: dünner Xcode-Wrapper (SwiftUI-Hülle um den
              ComposeUIViewController) + Projektdatei fürs Signing
```

- `client` bekommt ein eigenes Android-Anwendungs-Target mit eigener
  `applicationId` (`at.aau.pulverfass.client`) — parallel zur bestehenden App
  installierbar, dient als Entwicklungsvehikel auf Windows.
- Die „offizielle“ Android-App bleibt `app`.

## shared-Umstellung auf KMP

Targets: `jvm()`, `iosArm64()`, `iosSimulatorArm64()`. `app` und `server`
konsumieren weiter das JVM-Target; für sie ändert sich nichts. Fast alle Dateien
wandern unverändert nach `commonMain`. Die JVM-abhängigen Dateien:

| Datei | Strategie |
| --- | --- |
| `PacketCodec` (ByteBuffer/ByteOrder) | Pure-Kotlin-Rewrite auf `ByteArray`; Wire-Format byte-identisch, abgesichert durch bestehende Codec-Tests plus neuen Golden-Bytes-Test |
| `Utf8Decoding` (strikte Dekodierung) | Common-Kotlin: `decodeToString(throwOnInvalidSequence = true)` |
| `NetworkMessageSerializer` (CharacterCodingException) | Folgeanpassung an die common Utf8-Dekodierung |
| `MapDefinitionHashing` (SHA-256) | `expect`/`actual`: JVM `MessageDigest`, iOS `CC_SHA256` (CommonCrypto) |
| `MapConfigLoader` (InputStream) | Common-API auf `String`/`ByteArray`; `InputStream`-Overload bleibt in `jvmMain` für Quellkompatibilität von app/server |
| `LobbyStateProcessor` (ReentrantReadWriteLock) | KMP-fähiger Lock (kotlinx-atomicfu) |

## Datenfluss

Unverändert: Der iOS-Client spricht dasselbe WebSocket-Protokoll über denselben
`shared`-Codec mit dem Server. Nur die Ktor-Client-Engine ist plattformspezifisch
(Android: CIO wie bisher, iOS: Darwin). Ktor 2.3.12 bringt die Darwin-Engine
bereits mit, kein Upgrade nötig.

## Plattform-Abstraktionen im client-Modul

`expect`/`actual`-Paare; die Android-Seite ist jeweils eine Kopie des
existierenden Codes aus `app`:

| Komponente | Android (`androidMain`) | iOS (`iosMain`) |
| --- | --- | --- |
| `VideoPlayer`-Composable | `VideoView`-Interop (wie bisher) | `AVPlayer` + `AVPlayerLayer` via `UIKitView`; Center-Crop über `resizeAspectFill` |
| `BackgroundMusicManager` | `MediaPlayer` (wie bisher) | `AVAudioPlayer` |
| `PlayerNameStore` / `ReconnectSessionStore` | `SharedPreferences` (wie bisher) | `NSUserDefaults` (Interfaces existieren bereits) |
| Shake-Erkennung (`GameScreen`) | `SensorManager` (wie bisher) | `CMMotionManager` (CoreMotion) |
| Ktor-Engine | CIO | Darwin |

**Map-Rendering** braucht keinen Plattform-Code: PNG-Dekodierung zu `ImageBitmap`
und der Pixel-Lookup für die Territoriumserkennung (`map_region_id.png`,
`ImageBitmap.toPixelMap()`) laufen in common code; `BitmapFactory` entfällt.

## Ressourcen

Alle MP3s, MP4s, PNGs und Fonts aus `app/src/main/res` werden nach
`client/src/commonMain/composeResources/` kopiert (Zugriff über die generierte
`Res`-Klasse statt `R.raw.*`/`R.drawable.*`). Die Originale bleiben unberührt.

## Fehlerbehandlung

Unverändert übernommen (`GameErrorTextMapper`, Reconnect-Logik werden
mitkopiert). iOS-Media-Implementierungen kapseln Fehler genauso defensiv
(`runCatching`) wie die Android-Pendants.

## Tests

- **shared:** Bestehende JUnit-Tests laufen unverändert auf dem JVM-Target
  (Regressionsschutz für app/server). Codec-Tests laufen zusätzlich auf dem
  iOS-Target, um das Wire-Format plattformübergreifend abzusichern.
- **client:** Plattformneutrale Logik-Tests (Reducer, Mapper, LobbyController)
  als Kopie nach `commonTest`; Robolectric-UI-Tests bleiben dem Android-Target
  vorbehalten.
- **Manuell:** Demo-Durchlauf auf echtem iPhone gegen den lokalen Server
  (Lobby erstellen/beitreten, Verbindungsabbruch/Reconnect, Spielansicht);
  visueller Abgleich mit Android.

## CI

Bestehende Pipeline läuft unverändert. Optional: macOS-Job (GitHub-hosted
`macos-latest`) mit `:client:compileKotlinIosArm64` als reiner Compile-Check
(ohne Signing), damit iOS-Code nicht unbemerkt bricht, wenn auf Windows
entwickelt wird.

## Risiken & offene Punkte

1. **Versionsmatrix:** Kotlin 2.3.20 braucht eine kompatible
   Compose-Multiplatform-Version — wird zu Beginn der Implementierung verifiziert
   und im Plan festgenagelt.
2. **Wire-Format:** `PacketCodec`-Rewrite muss byte-identisch sein —
   Golden-Bytes-Test als zusätzliche Absicherung.
3. **Video-Parität:** Center-Crop/Loop-Verhalten der Video-Hintergründe auf iOS
   visuell gegen Android abgleichen.
4. **Apple-Account:** Kostenlose Dev-Builds laufen nach 7 Tagen ab und müssen
   neu deployt werden.
5. **Duplizierung:** Bis `app` (optional, später) auf `client` umsteigt, müssen
   UI-Änderungen doppelt gepflegt werden — bewusste Entscheidung zugunsten der
   Stabilität des bestehenden Android-Clients.
