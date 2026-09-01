ARG BUILD_FROM=ghcr.io/hassio-addons/base:21.0.3
ARG HOMEBOX_VERSION="0.26.2"

# Homebox's frontend is a Nuxt application that the backend serves out of its
# own executable, so it has to be built before the backend is compiled.
#
# What comes out is JavaScript and CSS, which is the same whatever it was built
# on, so this stage is pinned to the machine doing the building. Left to follow
# the target, an aarch64 image would run the whole Node toolchain under
# emulation for no benefit.
# hadolint ignore=DL3029
FROM --platform=${BUILDPLATFORM} node:24 AS frontend
ARG HOMEBOX_VERSION
ARG PNPM_VERSION="10.28.0"

ENV \
    NUXT_TELEMETRY_DISABLED=1

WORKDIR /usr/src/homebox

# Homebox addresses everything from the site root, which is the one thing that
# stops it working under Home Assistant Ingress. The patches point it at
# SUB_PATH instead, a value NGINX writes into the page per request. The first
# adds the plugin that reads it back and moves the whole API onto it; the
# second fixes the addresses the browser resolves itself, where no plugin can
# reach.
COPY patches /patches

# hadolint ignore=DL3003
RUN \
    curl -J -L -o /tmp/homebox.tar.gz \
        "https://github.com/sysadminsmedia/homebox/archive/v${HOMEBOX_VERSION}.tar.gz" \
    && tar zxf /tmp/homebox.tar.gz --strip 1 -C /usr/src/homebox \
    \
    && patch -p1 < /patches/ingress-base-path.patch \
    && patch -p1 < /patches/ingress-absolute-urls.patch \
    \
    && npm install --global "pnpm@${PNPM_VERSION}" \
    \
    && cd /usr/src/homebox/frontend \
    && CI=true pnpm install --frozen-lockfile \
    && pnpm build

# The backend is a single Go binary with the frontend built into it. Go
# cross-compiles, so this stage is pinned to the build machine as well and the
# architecture to produce is passed in rather than emulated.
# hadolint ignore=DL3029
FROM --platform=${BUILDPLATFORM} golang:1.26-alpine AS backend
ARG BUILD_ARCH=amd64
ARG HOMEBOX_VERSION

WORKDIR /usr/src/homebox

COPY --from=frontend /usr/src/homebox /usr/src/homebox

# Homebox reaches for the image codecs by name at runtime rather than linking
# them in, which is what leaves the binary asking for a dynamic loader despite
# being pure Go. Building natively, Go writes down the loader of the machine it
# ran on; cross-compiling, it falls back to the one glibc uses, which does not
# exist on Alpine. Naming it here is what keeps the aarch64 build runnable.
# hadolint ignore=DL3003
RUN \
    if [ "${BUILD_ARCH}" = "aarch64" ]; then \
        ARCH="arm64"; INTERPRETER="/lib/ld-musl-aarch64.so.1"; \
    fi \
    && if [ "${BUILD_ARCH}" = "amd64" ]; then \
        ARCH="amd64"; INTERPRETER="/lib/ld-musl-x86_64.so.1"; \
    fi \
    \
    && cp -r /usr/src/homebox/frontend/.output/public/. \
        /usr/src/homebox/backend/app/api/static/public/ \
    \
    && cd /usr/src/homebox/backend \
    && CGO_ENABLED=0 GOOS=linux GOARCH="${ARCH}" go build \
        -trimpath \
        -ldflags "-s -w -I ${INTERPRETER} -X main.version=v${HOMEBOX_VERSION}" \
        -o /homebox \
        ./app/api

# hadolint ignore=DL3006
FROM ${BUILD_FROM}

# Set shell
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# The image libraries are what Homebox loads to make thumbnails of photos that
# are not already JPEG or PNG, which is most of what a phone camera produces.
# NGINX faces Ingress and the network; Homebox itself only listens on loopback.
RUN \
    apk add --no-cache \
        libavif=1.4.1-r0 \
        libheif=1.23.0-r0 \
        libjxl=0.11.2-r1 \
        libwebp=1.6.0-r0 \
        nginx=1.30.4-r1 \
    \
    && rm -f -r \
        /etc/nginx \
        /tmp/*

COPY --from=backend /homebox /usr/bin/homebox

# Copy root filesystem
COPY rootfs /

# Health check
HEALTHCHECK \
    CMD curl --silent --fail http://127.0.0.1:47745/api/v1/status || exit 1

# Build arguments
ARG BUILD_ARCH
ARG BUILD_DATE
ARG BUILD_DESCRIPTION
ARG BUILD_NAME
ARG BUILD_REF
ARG BUILD_REPOSITORY
ARG BUILD_VERSION

# Labels
LABEL \
    io.hass.name="${BUILD_NAME}" \
    io.hass.description="${BUILD_DESCRIPTION}" \
    io.hass.arch="${BUILD_ARCH}" \
    io.hass.type="addon" \
    io.hass.version=${BUILD_VERSION} \
    maintainer="Franck Nijhof <opensource@frenck.dev>" \
    org.opencontainers.image.title="${BUILD_NAME}" \
    org.opencontainers.image.description="${BUILD_DESCRIPTION}" \
    org.opencontainers.image.vendor="Home Assistant Community Apps" \
    org.opencontainers.image.authors="Franck Nijhof <opensource@frenck.dev>" \
    org.opencontainers.image.licenses="MIT" \
    org.opencontainers.image.url="https://frenck.dev/home-assistant-apps" \
    org.opencontainers.image.source="https://github.com/${BUILD_REPOSITORY}" \
    org.opencontainers.image.documentation="https://github.com/${BUILD_REPOSITORY}/blob/main/README.md" \
    org.opencontainers.image.created=${BUILD_DATE} \
    org.opencontainers.image.revision=${BUILD_REF} \
    org.opencontainers.image.version=${BUILD_VERSION}
