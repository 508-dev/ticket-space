# syntax=docker/dockerfile:1

# PocketBase backend for Ticket Space.
#
# Pinned to a specific PocketBase release (see PB_VERSION below). The pinned
# release must be at least 7 days old per docs/supply-chain.md; only the
# linux/amd64 asset is downloaded, so build with `--platform linux/amd64`.

FROM alpine:3.20

ARG PB_VERSION=0.40.6

# Only the linux/amd64 asset is supported by this Dockerfile. If you need
# another architecture, add the matching download URL and checksums grep here.
ARG PB_ARCH=linux_amd64

RUN apk add --no-cache \
    ca-certificates \
    tzdata \
    unzip

WORKDIR /pb

# Download the pinned release and its official checksums file, then verify the
# archive against the checksums entry before unpacking.
ADD "https://github.com/pocketbase/pocketbase/releases/download/v${PB_VERSION}/checksums.txt" /tmp/checksums.txt
ADD "https://github.com/pocketbase/pocketbase/releases/download/v${PB_VERSION}/pocketbase_${PB_VERSION}_${PB_ARCH}.zip" /tmp/pb.zip

RUN cd /tmp \
    && grep "pocketbase_${PB_VERSION}_${PB_ARCH}.zip" checksums.txt | awk '{ print $1 "  pb.zip" }' | sha256sum -c - \
    && unzip pb.zip -d /pb \
    && rm -f pb.zip checksums.txt \
    && chmod +x /pb/pocketbase

# Migrations and hooks are applied/loaded from these directories at startup.
COPY pb_migrations/ /pb/pb_migrations/
COPY pb_hooks/ /pb/pb_hooks/

# Data dir (mounted as a persistent volume in production and as a temp dir in
# the smoke test). With WORKDIR=/pb, PocketBase defaults to ./pb_data,
# ./pb_migrations and ./pb_hooks, which is exactly this layout.
ENV PB_DATA_DIR=/pb/pb_data

EXPOSE 8090

VOLUME /pb/pb_data

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q --spider "http://127.0.0.1:8090/api/health" || exit 1

CMD ["/pb/pocketbase", "serve", "--http=0.0.0.0:8090"]
