# iosApp — iOS-Build des Pulverfass-Clients

Dünner Xcode-Wrapper um das Compose-Multiplatform-Modul [`client`](../client). Die gesamte UI
und Logik lebt in Kotlin; dieses Projekt enthält nur den SwiftUI-Einstieg, die
`Info.plist` und die gebündelten Medien (MP3/MP4 in [`iosApp/media`](iosApp/media)).

## Voraussetzungen

- Mac mit **Xcode 16+** (Kotlin/Native-Linking läuft nur auf macOS)
- **JDK 17+** (für den Gradle-Build, der vom Xcode-Build-Script aufgerufen wird)
- Für echte Geräte: ein (kostenloser) Apple-Account in Xcode hinterlegt

> **Frischer Mac?** Komplette Schritt-für-Schritt-Anleitung von der
> Xcode-Installation bis zum iPhone-Build: [docs/ios/mac-setup.md](../docs/ios/mac-setup.md)

## Bauen & Starten

1. Repository klonen, Branch auschecken.
2. `open iosApp/iosApp.xcodeproj`
3. Unter *Signing & Capabilities* das eigene Team auswählen (Bundle-ID ggf.
   anpassen, falls die ID im Team schon belegt ist).
4. Simulator oder angeschlossenes iPhone wählen → **Run**.

Der Build-Step „Compile Kotlin Framework" ruft automatisch
`./gradlew :client:embedAndSignAppleFrameworkForXcode` auf — der erste Build
dauert daher mehrere Minuten.

### Verifikation der shared-Logik auf iOS

```bash
./gradlew :shared:iosSimulatorArm64Test
```

## Wichtige Hinweise

- **Kostenlose Dev-Builds laufen nach 7 Tagen ab** und müssen dann erneut über
  Xcode deployt werden (max. 3 Apps pro kostenlosem Account).
- Auf echten Geräten muss unter *Einstellungen → Datenschutz & Sicherheit →
  Entwicklermodus* der Developer Mode aktiviert sein.
- Die `Info.plist` enthält eine **ATS-Ausnahme** (`NSAllowsArbitraryLoads`),
  weil der Spielserver über unverschlüsseltes `ws://` erreichbar ist
  (`ws://5.189.160.80:8080/ws`). Für einen produktiven Release müsste der
  Server auf `wss://` umgestellt und die Ausnahme entfernt werden.
- Die App ist wie auf Android auf **Landscape** festgelegt; die Statusbar ist
  ausgeblendet.

## Manuelle Verifikations-Checkliste (Team-Mac)

- [ ] `./gradlew :shared:iosSimulatorArm64Test` → PASS (Wire-Format-Tests auf iOS)
- [ ] App startet im Simulator: Intro-Video → Ladescreen → Hauptmenü mit
      Video-Hintergrund und Musik
- [ ] Lobby-Flow gegen den Server: Lobby erstellen, von einem Android-Gerät
      beitreten, Charakterwahl, Spielstart
- [ ] Spielansicht: Karte rendert, Territorien anklickbar (Tap-Hitdetection),
      Zoom/Pan funktioniert
- [ ] Reconnect: App killen, neu starten → Session wird wiederhergestellt
- [ ] Visueller Abgleich mit Android (Fonts, Farben, Layout, Video-Center-Crop)
- [ ] Durchlauf auf echtem iPhone (Kabel, Developer Mode aktiv)
