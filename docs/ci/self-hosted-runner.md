# Self-hosted Ubuntu Runner Voraussetzungen (CI)

Für die Workflows in `.github/workflows/ci.yml` wird ein Runner mit folgenden Eigenschaften benötigt:

- Label: `self-hosted`
- Java: Temurin JDK 25 verfügbar (wird im Workflow via `actions/setup-java` gesetzt)
- Gradle Wrapper-Ausführung erlaubt (`./gradlew`)
- Netzwerkzugriff auf Maven Central / Gradle Plugin Portal / Google Maven (Dependency-Auflösung)

Hinweis zum E2E-Teil: Das Modul `:e2e` ist aktuell ein Placeholder zur CI-Vorbereitung. Echte End-to-End-Tests folgen erst, wenn die benötigten Dependencies und technischen Grundlagen final verfügbar sind.
