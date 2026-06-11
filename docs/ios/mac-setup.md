# Team-Mac-Setup: Von null bis zum iPhone/iPad-Build

Schritt-für-Schritt-Anleitung, um den Pulverfass-Client auf einem frischen Mac
zu bauen und auf einem echten iPhone oder iPad zu starten. Die App selbst ist
komplett in Kotlin ([`client`](../../client)-Modul); Xcode wird nur als
Build-/Signing-Werkzeug gebraucht.

## 0. Voraussetzungen

| Was | Mindestens | Anmerkung |
| --- | --- | --- |
| Mac | Apple Silicon (M1+) empfohlen | Intel-Mac: siehe [Troubleshooting](#intel-mac) |
| macOS | passend zur Xcode-Version (Xcode 16 → macOS 14.5+) | |
| Freier Speicher | ~40 GB | Xcode ~15 GB + iOS-Support + Kotlin/Native-Toolchain |
| Apple-ID | kostenlos reicht | für das Code-Signing der Dev-Builds |
| iPhone/iPad | iOS 15+ | Deployment-Target der App |

## 1. Xcode installieren

1. **Xcode 16+** aus dem Mac App Store laden (groß, dauert).
2. Xcode einmal starten, Lizenz akzeptieren und die nachgeforderten
   Komponenten installieren — dabei unbedingt die **iOS-Plattform** mit
   herunterladen (Dialog beim ersten Start, sonst später unter
   *Xcode → Settings → Components*).
3. Command Line Tools aktivieren (macht Xcode beim ersten Start meist selbst):

   ```bash
   xcode-select --install        # nur falls noch nicht vorhanden
   xcodebuild -version           # sollte die Xcode-Version ausgeben
   ```

## 2. JDK installieren

Der Xcode-Build ruft intern Gradle auf, dafür braucht der Mac ein JDK (17+).
Am einfachsten per [Homebrew](https://brew.sh):

```bash
brew install --cask temurin
/usr/libexec/java_home -V      # JDK muss hier gelistet sein
java -version
```

> Wichtig: Das JDK muss systemweit auffindbar sein (`/usr/libexec/java_home`),
> weil die Xcode-Build-Phase mit minimaler Shell-Umgebung läuft. Der
> Homebrew-Cask erledigt das korrekt; ein nur in der `.zshrc` gesetztes
> `JAVA_HOME` reicht **nicht**.

Android Studio ist auf dem Mac **nicht** nötig — es sei denn, man will dort
auch am Android-Teil arbeiten.

## 3. Repository klonen und Branch auschecken

```bash
git clone https://github.com/MatTrinkl/SE2Risiko.git
cd SE2Risiko
git checkout feature/ios-port-design   # bzw. main, sobald gemergt
```

## 4. Erste Verifikation auf der Kommandozeile

Bevor Xcode ins Spiel kommt, einmal prüfen, dass Gradle und Kotlin/Native auf
dem Mac funktionieren:

```bash
./gradlew :shared:iosSimulatorArm64Test
./gradlew :client:linkDebugFrameworkIosSimulatorArm64
```

Der **erste** Lauf lädt die Kotlin/Native-Toolchain (mehrere GB nach
`~/.konan`) und dauert entsprechend — danach geht es schnell. Beide Befehle
müssen mit `BUILD SUCCESSFUL` enden; die Tests sichern u. a. das binäre
Wire-Format des Netzwerkprotokolls auf iOS ab.

## 5. Xcode-Projekt öffnen und Signing einrichten

```bash
open iosApp/iosApp.xcodeproj
```

1. **Apple-ID hinterlegen:** *Xcode → Settings → Accounts → „+" → Apple ID*
   anmelden. Dadurch entsteht automatisch ein „Personal Team".
2. Links im Navigator das Projekt **iosApp** anklicken → Target **iosApp** →
   Tab **Signing & Capabilities**:
   - *Automatically manage signing*: ✅ (Standard)
   - *Team*: das eigene (Personal) Team auswählen
   - *Bundle Identifier*: `at.aau.pulverfass.client` — falls Xcode meldet, die
     ID sei bereits vergeben, ein persönliches Suffix anhängen, z. B.
     `at.aau.pulverfass.client.<nachname>`. Für die App-Funktion ist die ID
     egal.

## 6. Erst im Simulator starten

1. Oben in der Toolbar als Ziel einen Simulator wählen (z. B.
   *iPhone 16*).
2. **Run** (▶ bzw. `Cmd+R`).
3. Der Build-Step „Compile Kotlin Framework" ruft
   `./gradlew :client:embedAndSignAppleFrameworkForXcode` auf — der erste
   Xcode-Build dauert deshalb mehrere Minuten.

Erwartung: Studio-Intro-Video → Ladescreen → Hauptmenü mit Video-Hintergrund
und Musik. Die App ist auf Landscape festgelegt (Simulator ggf. mit
`Cmd+→` drehen).

## 7. Auf echtem iPhone/iPad bauen

### Gerät vorbereiten

1. iPhone/iPad per **Kabel** anschließen, am Gerät „Diesem Computer
   vertrauen" bestätigen.
2. **Entwicklermodus aktivieren** (einmalig, iOS 16+):
   *Einstellungen → Datenschutz & Sicherheit → Entwicklermodus* → an →
   Gerät neu starten → nach dem Neustart bestätigen.
   (Der Menüpunkt erscheint erst, nachdem das Gerät einmal mit Xcode
   verbunden war.)

### Bauen und starten

1. In Xcode oben das echte Gerät als Ziel auswählen.
2. **Run**. Beim ersten Mal erstellt Xcode automatisch ein Provisioning-Profil
   für das Personal Team.
3. Beim ersten Start meldet iOS „Nicht vertrauenswürdiger Entwickler":
   *Einstellungen → Allgemein → VPN & Geräteverwaltung →* eigenes
   Entwickler-Zertifikat → **Vertrauen**. Danach lässt sich die App starten.

### Einschränkungen kostenloser Apple-Accounts

- Provisioning-Profile laufen nach **7 Tagen** ab → App danach einfach erneut
  über Xcode aufs Gerät bauen.
- Maximal **3 Apps** gleichzeitig pro kostenlosem Account signiert.
- Mit einem bezahlten Developer-Account (99 €/Jahr) entfallen beide Limits.

## 8. Funktions-Check gegen den Server

Die App spricht den Produktionsserver `ws://5.189.160.80:8080/ws` (Default in
`LobbyControllerConfig`). Für den Durchstich:

1. Auf dem iPhone: Lobby erstellen.
2. Auf einem Android-Gerät (App aus `:app` oder `:client`): mit dem
   4-stelligen Code beitreten.
3. Charakterwahl, Spielstart, Karte antippen, App killen und neu starten
   (Reconnect) — vollständige Checkliste in
   [iosApp/README.md](../../iosApp/README.md#manuelle-verifikations-checkliste-team-mac).

## Troubleshooting

| Symptom | Ursache / Lösung |
| --- | --- |
| Build-Phase „Compile Kotlin Framework" schlägt fehl: *Unable to locate a Java Runtime* | JDK nicht systemweit installiert → Schritt 2; prüfen mit `/usr/libexec/java_home` |
| `Command PhaseScriptExecution failed` ohne klare Meldung | Im Xcode *Report Navigator* (`Cmd+9`) das Build-Log öffnen; zur Diagnose denselben Befehl im Terminal laufen lassen: `./gradlew :client:embedAndSignAppleFrameworkForXcode` |
| Erster Build extrem langsam / scheinbar hängend | Kotlin/Native lädt die Toolchain nach `~/.konan` (mehrere GB) — abwarten, nur beim ersten Mal |
| *Signing for "iosApp" requires a development team* | Schritt 5: Apple-ID hinterlegen und Team im Target auswählen |
| *Bundle identifier is not available* | Eigenes Suffix an die Bundle-ID anhängen (Schritt 5) |
| App startet auf dem Gerät nicht („Nicht vertrauenswürdiger Entwickler") | Zertifikat unter *VPN & Geräteverwaltung* vertrauen (Schritt 7) |
| App lief, startet nach ~1 Woche nicht mehr | 7-Tage-Profil abgelaufen → erneut über Xcode bauen |
| Kein `ws://`-Verbindungsaufbau | ATS-Ausnahme ist in der `Info.plist` enthalten; prüfen, ob der Server erreichbar ist (`curl http://5.189.160.80:8080/health`) |

<a id="intel-mac"></a>
### Intel-Mac

Das Projekt konfiguriert die Targets `iosArm64` (Geräte) und
`iosSimulatorArm64` (Simulator auf Apple Silicon). Auf einem Intel-Mac
funktioniert der **Simulator** erst, wenn in
[`client/build.gradle.kts`](../../client/build.gradle.kts) und
[`shared/build.gradle.kts`](../../shared/build.gradle.kts) zusätzlich
`iosX64()` als Target ergänzt wird. Builds für **echte Geräte** (iosArm64)
funktionieren auch vom Intel-Mac aus unverändert.
