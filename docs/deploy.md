# Deployment

Diese Datei beschreibt den produktiven Setup-Pfad für den Server-Host und die dazugehörigen GitHub Actions Inputs.

Es werden nur Token-Namen dokumentiert. Keine Secret-Werte gehören ins Repo, nicht in Shell-History, nicht in Screenshots und nicht in Commit-Messages.

## GitHub Secrets und Vars

### Pflicht für Deployment per SSH

- `DEPLOY_SSH_KEY`
- `DEPLOY_HOST`
- `DEPLOY_USER`
- `DEPLOY_PORT`

### Pflicht für GHCR Auth

- Standardfall: `GITHUB_TOKEN`
- alternativ:
  - `GHCR_USERNAME`
  - `GHCR_TOKEN`

### Pflicht für PostgreSQL auf dem Zielserver

- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_DB`

### Optional

- `DEPLOY_PATH`
- `DEPLOY_HOST_KEY`
- `DEPLOY_HEALTH_URL`
- `DEPLOY_DOKKA_URL`

## Bedeutung der Tokens

### SSH

- `DEPLOY_SSH_KEY`
  Privater SSH-Key für den GitHub Actions Runner, der den Zielserver erreichen darf.
- `DEPLOY_HOST`
  Öffentlicher DNS-Name oder die IP des Zielservers.
- `DEPLOY_USER`
  Benutzer für den SSH-Login auf dem Zielserver.
- `DEPLOY_PORT`
  SSH-Port des Zielservers, typischerweise `22`.

### GHCR

- `GITHUB_TOKEN`
  Bevorzugte Authentifizierung innerhalb von GitHub Actions für `ghcr.io`, sofern `packages:write` oder `packages:read` gesetzt ist.
- `GHCR_USERNAME`
  Nur nötig, wenn nicht mit `GITHUB_TOKEN` gearbeitet wird.
- `GHCR_TOKEN`
  Nur nötig, wenn nicht mit `GITHUB_TOKEN` gearbeitet wird. Muss mindestens die nötigen GHCR-Rechte für Pull oder Push haben.

### PostgreSQL

- `POSTGRES_USER`
  PostgreSQL-User für den Container-Init und für die App-Verbindung.
- `POSTGRES_PASSWORD`
  Passwort für PostgreSQL-Init und App-Verbindung.
- `POSTGRES_DB`
  Initiale Datenbank, die beim ersten Start angelegt wird.

### Deploy-Optionen

- `DEPLOY_PATH`
  Absoluter Pfad auf dem Zielserver, in dem `compose.yaml` und `.env` liegen.
- `DEPLOY_HOST_KEY`
  Empfohlener gepinnter Eintrag für `known_hosts`. Wenn gesetzt, nutzt der Workflow diesen Wert statt eines frischen `ssh-keyscan`.
- `DEPLOY_HEALTH_URL`
  Optionaler externer Health-Endpunkt für den Smoke-Test. Wenn nicht gesetzt, wird im Workflow die Default-URL aus dem Host abgeleitet.
- `DEPLOY_DOKKA_URL`
  Optionaler externer Dokka-Endpunkt für den Smoke-Test. Wenn nicht gesetzt, wird im Workflow standardmäßig `http://<DEPLOY_HOST>:8081/index.html` verwendet.

## Server Prerequisites

Der Zielserver muss vor dem ersten automatischen Deployment vorbereitet sein.

### Installationen

- Docker Engine
- Docker Compose Plugin (`docker compose`)
- Netzwerkzugriff auf `ghcr.io`

### Benutzer und Rechte

- Der in `DEPLOY_USER` konfigurierte Benutzer muss Docker-Kommandos ausführen dürfen.
- Entweder:
  - Benutzer ist in der `docker`-Gruppe
  - oder der Deploy-Prozess wird bewusst mit `sudo` davor angepasst

### Firewall und Ports

- SSH-Port aus `DEPLOY_PORT` muss vom GitHub Actions Runner erreichbar sein.
- Der externe Server-Port für den Ktor-Service muss erreichbar sein.
- Der externe Dokka-Port muss nur dann erreichbar sein, wenn die Doku öffentlich ausgeliefert werden soll.
- PostgreSQL darf **nicht** extern veröffentlicht werden.
- In Compose bleibt die Datenbank nur im internen Docker-Netz.
- Minimal erwartete externe Freigaben sind damit:
  - SSH auf `DEPLOY_PORT`
  - App-Port `PORT`
  - optional Doku-Port `DOKKA_PORT`

### GHCR Login auf dem Server

Vor dem ersten Rollout muss klar sein, dass ein Login gegen `ghcr.io` grundsätzlich funktioniert.

Minimaler Prüfpfad auf dem Server:

```bash
echo '<TOKEN_FROM_SECRET_STORE>' | docker login ghcr.io -u '<USERNAME_FROM_SECRET_STORE>' --password-stdin
```

Dabei gilt:

- nie echte Token-Werte in Terminal-History belassen
- kein Token in Skripte oder Repo-Dateien schreiben
- nach manuellen Tests Shell-History entsprechend behandeln

## SSH Host Key Handling

Bevorzugter Pfad:

- `DEPLOY_HOST_KEY` als gepinnter `known_hosts`-Eintrag in GitHub hinterlegen

Fallback:

- wenn `DEPLOY_HOST_KEY` nicht gesetzt ist, verwendet der Workflow `ssh-keyscan`

Empfehlung:

- für produktive Hosts `DEPLOY_HOST_KEY` setzen
- Host-Key-Rotation bewusst dokumentieren und den gepinnten Wert anschließend aktualisieren

## Wie die Compose-Datei auf den Server kommt

Es gibt zwei saubere Varianten.

### Variante A: Repo auf dem Server klonen

Empfohlen, wenn der Server das Repo dauerhaft auschecken darf.

Vorgehen:

1. Repo in ein Zielverzeichnis klonen.
2. `compose.yaml` dort belassen.
3. `.env` im selben Verzeichnis anlegen.
4. `DEPLOY_PATH` auf genau dieses Verzeichnis setzen.

Beispielstruktur auf dem Server:

```text
/opt/se2risiko/
  compose.yaml
  .env
```

### Variante B: Dateien per `scp` oder `rsync` übertragen

Empfohlen, wenn der Server kein Git-Checkout behalten soll.

Vorgehen:

1. Zielverzeichnis auf dem Server anlegen.
2. `compose.yaml` dorthin kopieren.
3. `.env` separat auf dem Server anlegen.
4. `DEPLOY_PATH` auf dieses Verzeichnis setzen.

Wichtig:

- `.env` nie aus dem Repo übertragen
- nur `compose.yaml` und ggf. ergänzende nicht-sensitive Dateien kopieren

## Server-Dateien

### `compose.yaml`

Die produktive `compose.yaml` im Repo erwartet:

- ein Server-Image aus GHCR über `SERVER_IMAGE`
- ein Dokka-Image aus GHCR über `DOKKA_IMAGE`
- App-Version über `APP_VERSION`
- öffentlichen Dokka-Port über `DOKKA_PORT`
- PostgreSQL-Konfiguration über:
  - `POSTGRES_USER`
  - `POSTGRES_PASSWORD`
  - `POSTGRES_DB`

Security-Basics im Compose-Setup:

- PostgreSQL ohne öffentliches Port-Mapping
- nur App-Port und optional Dokka-Port nach außen
- `server` und `dokka` mit `no-new-privileges`
- `server` und `dokka` ohne Linux-Capabilities

### `.env`

Die `.env` liegt nur auf dem Server. Sie enthält keine Beispielwerte aus dem Repo, sondern echte Werte aus dem Secret-Store des Teams.

Erwartete Variablen:

- `SERVER_IMAGE`
- `DOKKA_IMAGE`
- `APP_VERSION`
- `PORT`
- `DOKKA_PORT`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_DB`

Hinweis:

- `SERVER_IMAGE`, `DOKKA_IMAGE` und `APP_VERSION` können beim Deployment per GitHub Actions temporär überschrieben werden.
- `POSTGRES_USER`, `POSTGRES_PASSWORD` und `POSTGRES_DB` bleiben serverseitige Konfiguration.

## Initiale Server-Vorbereitung

1. Server bereitstellen.
2. Docker und Compose installieren.
3. Zielverzeichnis für Deployment anlegen.
4. `compose.yaml` auf den Server bringen oder Repo klonen.
5. `.env` auf dem Server anlegen.
6. SSH-Zugriff für `DEPLOY_USER` mit `DEPLOY_SSH_KEY` vorbereiten.
7. Sicherstellen, dass `ghcr.io` vom Server aus erreichbar ist.
8. Firewall für SSH und den externen App-Port prüfen.
9. Einmalig `docker compose up -d` manuell validieren.
10. Erst danach GitHub Actions Deployment aktiv nutzen.

## Reproduzierbarer Rollout auf einem neuen Server

Ein neuer Server ist dann reproduzierbar vorbereitet, wenn folgende Punkte erfüllt sind:

- alle Pflicht-Tokens existieren in GitHub
- Docker und Compose laufen auf dem Zielserver
- `compose.yaml` liegt unter `DEPLOY_PATH`
- `.env` liegt unter `DEPLOY_PATH`
- GHCR Login funktioniert
- der externe Health-Endpunkt antwortet nach `docker compose up -d`
- der externe Dokka-Endpunkt liefert `index.html`
- PostgreSQL ist von außen nicht erreichbar

## Smoke-Test nach Deployment

Minimal prüfen:

```bash
curl -i '<DEPLOY_HEALTH_URL_OR_SERVER_HEALTH_ENDPOINT>'
```

Optional zusätzlich:

```bash
curl -i '<DEPLOY_READY_URL_OR_SERVER_READY_ENDPOINT>'
curl -i '<DEPLOY_VERSION_URL_OR_SERVER_VERSION_ENDPOINT>'
curl -i '<DEPLOY_DOKKA_URL_OR_DOKKA_INDEX_ENDPOINT>'
```
