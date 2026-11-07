#!/usr/bin/env bash
#
# End-to-end smoke test for the Ticket Space PocketBase backend.
#
# Builds the image, runs it on a random free host port with a throwaway data
# dir, creates the superuser, bootstraps two test users, then asserts schema,
# rules, CRUD (incl. a multipart upload), sort order, the done/open hook, and
# the realtime endpoint. The container and data dir are always removed on exit,
# and the script exits non-zero on any failure.
#
# Usage:  apps/pocketbase/scripts/smoke.sh
#
# Requires: docker, curl, jq, bun.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POCKETBASE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

for cmd in docker curl jq bun; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "error: '$cmd' is required" >&2; exit 1; }
done

IMAGE_NAME="ticketspace-pocketbase:smoke"
CONTAINER_NAME="ticketspace-smoke-$$-${RANDOM}"
DATA_DIR="$(mktemp -d)"
PORT=""

# Test-only credentials for a throwaway container that is always removed.
SUPERUSER_EMAIL="smoke-superuser@example.com"
SUPERUSER_PASSWORD="smoke-$(head -c 16 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 16)"
TICKETSPACE_USERS="alice@example.com:alice-password-1:Alice;bob@example.com:bob-password-1:Bob"

cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  # The container writes uploaded files as root into the temp data dir, so the
  # host user can't remove them directly. Empty the dir via a root container
  # (alpine:3.20 is already present — it is the image's base), then rmdir it.
  docker run --rm -v "$DATA_DIR:/d" alpine:3.20 rm -rf /d >/dev/null 2>&1 || true
  rmdir "$DATA_DIR" 2>/dev/null || true
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# Pick a random free host port (never 8090, which other agents may use).
# ---------------------------------------------------------------------------
find_free_port() {
  local p
  while true; do
    p=$((20000 + (RANDOM % 30000)))
    if (echo >/dev/tcp/127.0.0.1/"$p") 2>/dev/null; then
      continue # in use, retry
    fi
    PORT="$p"
    return 0
  done
}
find_free_port

echo "=== building image (context: $POCKETBASE_DIR) ==="
docker build --platform linux/amd64 -t "$IMAGE_NAME" "$POCKETBASE_DIR"

echo "=== starting container on 127.0.0.1:$PORT ==="
docker run -d --name "$CONTAINER_NAME" \
  -p "127.0.0.1:$PORT:8090" \
  -v "$DATA_DIR:/pb/pb_data" \
  "$IMAGE_NAME" >/dev/null

echo "=== waiting for /api/health ==="
healthy=0
for _ in $(seq 1 60); do
  if curl -sf "http://127.0.0.1:$PORT/api/health" >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 1
done
if [ "$healthy" -ne 1 ]; then
  echo "error: container did not become healthy in time" >&2
  docker logs "$CONTAINER_NAME" >&2 || true
  exit 1
fi

echo "=== creating superuser (docker exec superuser upsert) ==="
upserted=0
for _ in 1 2 3 4 5; do
  if docker exec "$CONTAINER_NAME" /pb/pocketbase superuser upsert "$SUPERUSER_EMAIL" "$SUPERUSER_PASSWORD" >/dev/null 2>&1; then
    upserted=1
    break
  fi
  sleep 1
done
if [ "$upserted" -ne 1 ]; then
  echo "error: superuser upsert failed" >&2
  exit 1
fi

echo "=== bootstrapping test users ==="
PB_URL="http://127.0.0.1:$PORT" \
PB_SUPERUSER_EMAIL="$SUPERUSER_EMAIL" \
PB_SUPERUSER_PASSWORD="$SUPERUSER_PASSWORD" \
TICKETSPACE_USERS="$TICKETSPACE_USERS" \
  "$SCRIPT_DIR/bootstrap.sh"

echo "=== running assertions ==="
PB_URL="http://127.0.0.1:$PORT" \
PB_SUPERUSER_EMAIL="$SUPERUSER_EMAIL" \
PB_SUPERUSER_PASSWORD="$SUPERUSER_PASSWORD" \
TICKETSPACE_USERS="$TICKETSPACE_USERS" \
  bun "$SCRIPT_DIR/smoke_assert.mjs"

echo "=== smoke test passed ==="
