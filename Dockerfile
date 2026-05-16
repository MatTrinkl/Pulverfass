FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY . .

RUN sed -i '/include(":app")/d' settings.gradle.kts && \
    sed -i '/include(":e2e")/d' settings.gradle.kts && \
    sed -i 's/\r$//' ./gradlew && \
    chmod +x ./gradlew
RUN ./gradlew --no-daemon :server:installDist

FROM eclipse-temurin:25-jre AS runtime

ARG APP_VERSION=dev

ENV PORT=8080 \
    APP_VERSION=${APP_VERSION}

WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/server/build/install/server/ ./

RUN groupadd --system appuser && useradd --system --gid appuser --create-home --home-dir /home/appuser appuser
RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["bin/server"]
