# Ktor server image: build the installJvmDist distribution with Gradle, run on a JRE.
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dkotlin.compiler.execution.strategy=in-process"

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY server server
COPY shared shared
# Included by settings.gradle.kts but not needed by the server; without build
# scripts Gradle configures them as empty projects.
RUN mkdir -p app/androidApp app/desktopApp app/webApp app/shared

RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :server:installJvmDist

FROM eclipse-temurin:21-jre AS runner

WORKDIR /app

# curl is used by the HEALTHCHECK and is not present in the base image.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY docker-entrypoint.sh /
COPY server/src/jvmMain/resources/application.conf .
COPY --from=builder /build/server/build/install/server-jvm/ ./server/

RUN chmod 0755 /docker-entrypoint.sh

USER nobody
EXPOSE 8000

ENTRYPOINT ["/docker-entrypoint.sh"]

HEALTHCHECK --start-period=15s --start-interval=3s \
    CMD curl -s http://localhost:${PORT:-8000} || exit 1
