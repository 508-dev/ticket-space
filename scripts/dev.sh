#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."

# No root dev orchestration yet (pre-MVP). Local entrypoints live in the apps.
cat <<'EOF'
Ticket Space - local development

- Android app: apps/android - see apps/android/README.md (Gradle).
- Backend:     apps/pocketbase - see apps/pocketbase/README.md (Dockerfile).

See docs/development.md and docs/architecture.md for the full workflow.
EOF
