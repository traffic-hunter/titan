# syntax=docker/dockerfile:1.7

FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-jammy AS builder

ARG VERSION=1.0-SNAPSHOT

WORKDIR /workspace

COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -PversionName="${VERSION}" :bootstrap:shadowJar \
    && cp "bootstrap/build/libs/titan-server-${VERSION}.jar" /workspace/titan-server.jar

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --gid 10001 titan \
    && useradd --uid 10001 --gid titan --create-home titan

WORKDIR /opt/titan

COPY --from=builder --chown=titan:titan /workspace/titan-server.jar ./titan-server.jar

USER titan

EXPOSE 61613 7777

STOPSIGNAL SIGTERM

ENTRYPOINT ["java", "-Dtitan.environment.path=/etc/titan/titan-env.yml", "-jar", "/opt/titan/titan-server.jar"]
