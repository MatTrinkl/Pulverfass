FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY . .

RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon :server:installDist

FROM eclipse-temurin:25-jre AS runtime

ARG APP_VERSION=dev

ENV PORT=8080 \
    APP_VERSION=${APP_VERSION} \
    DB_URL="" \
    DB_USER="" \
    DB_PASSWORD=""

WORKDIR /app

COPY --from=builder /workspace/server/build/install/server/ ./

EXPOSE 8080

ENTRYPOINT ["bin/server"]
