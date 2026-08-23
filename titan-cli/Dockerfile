# syntax=docker/dockerfile:1.7

FROM --platform=$BUILDPLATFORM golang:1.22-alpine AS builder

ARG TARGETOS
ARG TARGETARCH
ARG VERSION=dev

WORKDIR /workspace

COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download

COPY main.go ./
COPY internal ./internal

RUN --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS="${TARGETOS}" GOARCH="${TARGETARCH}" \
    go build -trimpath -ldflags "-s -w -X main.version=${VERSION}" -o /out/titan .

FROM alpine:3.22

RUN apk add --no-cache ca-certificates \
    && addgroup --gid 10001 titan \
    && adduser --uid 10001 --ingroup titan --disabled-password titan

COPY --from=builder --chown=titan:titan /out/titan /usr/local/bin/titan

USER titan

ENTRYPOINT ["/usr/local/bin/titan"]
