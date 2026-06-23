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

COPY server/build/install/server/ ./

RUN chmod +x bin/server
RUN groupadd --system appuser && useradd --system --gid appuser --create-home --home-dir /home/appuser appuser
RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["bin/server"]
