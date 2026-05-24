# Deployment

Diese Datei beschreibt den produktiven Setup-Pfad fuer den Deploy-Server und
die dazugehoerigen GitHub Actions Inputs.

Es werden nur Secret- und Variablen-Namen dokumentiert. Keine Secret-Werte
gehoeren ins Repo, nicht in Shell-History, nicht in Screenshots und nicht in
Commit-Messages.

## Zielbild

Der aktuelle Deploy-Pfad sieht so aus:

- der GitHub Actions Runner laeuft auf einem separaten CI-Host
- der Deploy-Server braucht keinen Runner
- der Workflow verbindet sich per SSH auf den Deploy-Server
- der Workflow kopiert vor jedem Deploy die aktuelle `compose.yaml`
- Runtime-Werte kommen direkt aus GitHub Vars und Secrets
- eine serverseitige `.env` ist fuer den CI-Deploy nicht noetig
- automatischer Deploy passiert bei Push auf `main`
- manueller Test-Deploy ist ueber `workflow_dispatch` moeglich

Wichtig:

- `compose.yaml` wird bei jedem Deploy aus dem Repo ueberschrieben
- sensible Werte liegen nicht dauerhaft als `.env` auf dem Server
- die Werte existieren waehrend des Deployments im Prozesskontext und danach in
  der Container-Konfiguration
- ein manueller `workflow_dispatch` publisht eindeutige `sha-*` Tags und
  ueberschreibt nicht das Floating-Tag `:main`

## GitHub Vars und Secrets

### Pflicht als Variables

- `DEPLOY_HOST`
- `DEPLOY_USER`
- `DEPLOY_PORT`
- `DEPLOY_PATH`
- `PORT`
- `DOKKA_PORT`
- `POSTGRES_USER`
- `POSTGRES_DB`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`

### Optional als Variables

- `DEPLOY_HOST_KEY`
- `DEPLOY_HEALTH_URL`
- `DEPLOY_DOKKA_URL`
- `GHCR_USERNAME`

### Pflicht als Secrets

- `DEPLOY_SSH_KEY`
- `POSTGRES_PASSWORD`
- `DB_PASSWORD`

### Optional als Secrets

- `GHCR_TOKEN`

Hinweis:

- Standardfall fuer GHCR ist das eingebaute `GITHUB_TOKEN`
- `GHCR_TOKEN` und `GHCR_USERNAME` brauchst du nur, wenn euer GitHub-Setup
  kein Pull per `GITHUB_TOKEN` erlaubt

## Bedeutung der Werte

### SSH

- `DEPLOY_SSH_KEY`
  Privater SSH-Key, den der Runner fuer die Verbindung zum Deploy-Server nutzt.
- `DEPLOY_HOST`
  Oeffentlicher DNS-Name oder IP des Deploy-Servers.
- `DEPLOY_USER`
  Linux-Benutzer fuer den SSH-Login.
- `DEPLOY_PORT`
  SSH-Port, meist `22`.
- `DEPLOY_PATH`
  Absoluter Zielpfad auf dem Deploy-Server, zum Beispiel `/opt/se2risiko`.

### Runtime

- `PORT`
  Externer App-Port fuer den Ktor-Service.
- `DOKKA_PORT`
  Externer Port fuer die Dokka-Auslieferung.
- `DB_HOST`
  Datenbank-Host fuer die Server-Anwendung. Im Compose-Standardfall `db`.
- `DB_PORT`
  Datenbank-Port fuer die Server-Anwendung. Im Compose-Standardfall `5432`.
- `DB_NAME`
  Datenbankname fuer die Server-Anwendung.
- `DB_USER`
  Datenbank-Benutzer fuer die Server-Anwendung.
- `DB_PASSWORD`
  Datenbank-Passwort fuer die Server-Anwendung.
- `POSTGRES_USER`
  PostgreSQL-Benutzer fuer den DB-Container-Init.
- `POSTGRES_PASSWORD`
  PostgreSQL-Passwort fuer den DB-Container-Init.
- `POSTGRES_DB`
  Name der initialen Datenbank fuer den DB-Container-Init.

### Optionale Deploy-Werte

- `DEPLOY_HOST_KEY`
  Gepinnter `known_hosts`-Eintrag fuer den Deploy-Server.
- `DEPLOY_HEALTH_URL`
  Explizite URL fuer den Health-Smoke-Test.
- `DEPLOY_DOKKA_URL`
  Explizite URL fuer den Dokka-Smoke-Test.

## Server Prerequisites

Der Deploy-Server braucht:

- Docker Engine
- Docker Compose Plugin (`docker compose`)
- SSH-Server
- Netzwerkzugriff auf `ghcr.io`

Der User aus `DEPLOY_USER` muss Docker ausfuehren duerfen.

## Frischer Deploy-Server: Schritt fuer Schritt

Diese Anleitung nimmt Ubuntu oder Debian an.

### 1. Basispakete installieren

```bash
sudo apt update
sudo apt upgrade -y
sudo apt install -y ca-certificates curl openssh-server ufw
sudo systemctl enable --now ssh
```

### 2. Docker und Compose installieren

```bash
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
```

### 3. Deploy-User anlegen

Wenn der User noch nicht existiert:

```bash
sudo adduser deploy
sudo usermod -aG docker deploy
```

Wenn du schon einen bestehenden User verwendest, muss nur die Docker-Berechtigung
passen:

```bash
sudo usermod -aG docker <dein-user>
```

### 4. Deploy-Verzeichnis anlegen

```bash
sudo mkdir -p /opt/se2risiko
sudo chown deploy:deploy /opt/se2risiko
```

Das ist spaeter dein `DEPLOY_PATH`.

### 5. Firewall setzen

Beispiel:

```bash
sudo ufw allow 22/tcp
sudo ufw allow 8080/tcp
sudo ufw allow 8081/tcp
sudo ufw enable
```

Nur die Ports oeffnen, die du wirklich brauchst. PostgreSQL bleibt intern und
wird nicht freigegeben.

### 6. SSH-Deploy-Key erzeugen

Den Key erzeugst du auf einem Admin-Rechner oder lokal, nicht auf dem
Deploy-Server:

```bash
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ./github-actions-deploy -N ""
```

Dabei entstehen:

- `github-actions-deploy`
  privater Schluessel, Inhalt kommt nach GitHub in `DEPLOY_SSH_KEY`
- `github-actions-deploy.pub`
  oeffentlicher Schluessel, kommt auf den Deploy-Server

Den Public Key auf dem Deploy-Server fuer `DEPLOY_USER` hinterlegen:

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
cat github-actions-deploy.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

Wenn du den Key fuer den User `deploy` einspielst, mache das als dieser User
oder mit einem Root-Shell-Wechsel auf genau diesen User.

### 7. SSH Host Key holen

Empfohlen ist Pinning per `DEPLOY_HOST_KEY`.

Vom Runner-Host oder von deinem Admin-Rechner:

```bash
ssh-keyscan -p 22 -t ed25519 dein.server.tld
```

Der komplette Output kommt nach GitHub in `DEPLOY_HOST_KEY`.

Optionaler Gegencheck direkt auf dem Deploy-Server:

```bash
sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

### 8. PostgreSQL-Passwort erzeugen

```bash
openssl rand -base64 32
```

Den Output als `POSTGRES_PASSWORD` in GitHub speichern.

### 9. GHCR-Zugriff pruefen

Falls ihr einen eigenen `GHCR_TOKEN` verwendet:

```bash
echo '<TOKEN>' | docker login ghcr.io -u '<USERNAME>' --password-stdin
```

Wenn ihr beim Standardfall bleibt, reicht im Normalfall das `GITHUB_TOKEN` aus
dem Workflow.

## GitHub konfigurieren

Pfad in GitHub:

- `Settings`
- `Secrets and variables`
- `Actions`

### Variables anlegen

Beispielwerte:

- `DEPLOY_HOST` = `example.org`
- `DEPLOY_USER` = `deploy`
- `DEPLOY_PORT` = `22`
- `DEPLOY_PATH` = `/opt/se2risiko`
- `PORT` = `8080`
- `DOKKA_PORT` = `8081`
- `POSTGRES_USER` = `se2risiko`
- `POSTGRES_DB` = `se2risiko`

Optional:

- `DEPLOY_HOST_KEY` = kompletter Output von `ssh-keyscan`
- `DEPLOY_HEALTH_URL` = `http://example.org:8080/health`
- `DEPLOY_DOKKA_URL` = `http://example.org:8081/index.html`
- `GHCR_USERNAME` = nur wenn ihr nicht mit `GITHUB_TOKEN` arbeitet

### Secrets anlegen

- `DEPLOY_SSH_KEY`
  kompletter Inhalt der privaten Datei `github-actions-deploy`
- `POSTGRES_PASSWORD`
  Output von `openssl rand -base64 32`
- `DB_PASSWORD`
  im Compose-Standard gleich wie `POSTGRES_PASSWORD`
- `GHCR_TOKEN`
  nur wenn noetig

## Was der Workflow heute macht

Beim Push auf `main` passiert im Deploy-Teil:

1. SSH konfigurieren
2. Zielverzeichnis auf dem Deploy-Server anlegen
3. aktuelle `compose.yaml` aus dem Repo auf den Deploy-Server kopieren
4. GHCR-Login auf dem Deploy-Server ausfuehren
5. Images pullen
6. `docker compose up -d` mit GitHub Vars und Secrets ausfuehren
7. Health- und Dokka-Smoke-Test ausfuehren

## Manueller Deploy-Test ohne Merge

Du kannst den Deploy testen, ohne einen Pull Request zu mergen.

Pfad in GitHub:

- `Actions`
- Workflow `CI`
- `Run workflow`

Empfohlener Ablauf:

1. Branch mit deiner Aenderung pushen.
2. In GitHub im CI-Workflow genau diesen Branch auswaehlen.
3. `deploy = true` lassen.
4. Workflow manuell starten.

Wichtig:

- der manuelle Lauf baut Images fuer den ausgewaehlten Commit
- diese Images bekommen eindeutige `sha-*` Tags
- der manuelle Lauf deployed genau diese Tags auf den Zielserver
- das Floating-Tag `:main` wird dabei nicht veraendert
- ein spaeterer Push auf `main` deployed wieder den echten Main-Stand

## Reproduzierbarer Rollout auf einem neuen Server

Ein neuer Server ist dann korrekt vorbereitet, wenn folgende Punkte erfuellt
sind:

- Docker und Compose laufen
- SSH funktioniert fuer `DEPLOY_USER`
- `DEPLOY_USER` darf Docker ausfuehren
- der Runner erreicht den Server auf `DEPLOY_PORT`
- der Server erreicht `ghcr.io`
- GitHub Vars und Secrets sind gesetzt
- nach einem Deploy antwortet `/health`
- PostgreSQL ist nicht extern erreichbar

## Smoke-Test nach Deployment

Minimal:

```bash
curl -i '<DEPLOY_HEALTH_URL_OR_SERVER_HEALTH_ENDPOINT>'
```

Optional:

```bash
curl -i '<DEPLOY_READY_URL_OR_SERVER_READY_ENDPOINT>'
curl -i '<DEPLOY_VERSION_URL_OR_SERVER_VERSION_ENDPOINT>'
curl -i '<DEPLOY_DOKKA_URL_OR_DOKKA_INDEX_ENDPOINT>'
```
