# syntax=docker/dockerfile:1.7

FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-jammy AS builder

ARG VERSION=1.0-SNAPSHOT

RUN apt-get update \
    && apt-get install --yes --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

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
COPY --chown=titan:titan docker/titan-env.yml /etc/titan/titan-env.yml

USER titan

EXPOSE 61613 7777

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
    CMD bash -c "exec 3<>/dev/tcp/127.0.0.1/7777 && printf 'GET /titan/monitor/health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3 && grep -q '200 OK' <&3"

STOPSIGNAL SIGTERM

ENTRYPOINT ["java", "-Dtitan.environment.path=/etc/titan/titan-env.yml", "-jar", "/opt/titan/titan-server.jar"]
