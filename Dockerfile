FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY shared/build.gradle.kts shared/build.gradle.kts
COPY server/build.gradle.kts server/build.gradle.kts

RUN sed -i '/include(":app")/d' settings.gradle.kts && \
    sed -i '/include(":e2e")/d' settings.gradle.kts && \
    sed -i 's/\r$//' ./gradlew && \
    chmod +x ./gradlew

RUN ./gradlew --no-daemon :server:dependencies

COPY shared/src/ shared/src/
COPY server/src/ server/src/

RUN ./gradlew --no-daemon :server:installDist

FROM eclipse-temurin:25-jre AS runtime

ARG APP_VERSION=dev
ARG COMMIT_SHA=unknown

ENV PORT=8080 \
    APP_VERSION=${APP_VERSION} \
    COMMIT_SHA=${COMMIT_SHA}

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
