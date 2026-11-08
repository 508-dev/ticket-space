#!/bin/sh
# Ticket Space PocketBase entrypoint.
#
# When PB_SUPERUSER_EMAIL and PB_SUPERUSER_PASSWORD are set (for example as
# secret environment variables in Coolify), ensure that superuser exists before
# serving. `superuser upsert` is idempotent and applies pending migrations first.
# Without those variables, create the superuser manually (see docs/deployment.md).
set -eu

if [ -n "${PB_SUPERUSER_EMAIL:-}" ] && [ -n "${PB_SUPERUSER_PASSWORD:-}" ]; then
  /pb/pocketbase superuser upsert "$PB_SUPERUSER_EMAIL" "$PB_SUPERUSER_PASSWORD" >/dev/null
  echo "entrypoint: superuser ensured for $PB_SUPERUSER_EMAIL"
fi

exec /pb/pocketbase serve --http=0.0.0.0:8090 "$@"
