#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."

# Full local gate. Absent components (apps/android, apps/pocketbase) are
# skipped with a message, not treated as an error. The merge gate is described
# in docs/architecture.md "Engineering rules".
bun run format:check
./scripts/lint.sh
./scripts/test.sh
