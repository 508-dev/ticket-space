#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."

# Root JS lint (Biome covers the PocketBase migrations and root config).
bun run lint
./scripts/sync-version.sh --check

# Android lint when the app is present. Absent components are skipped, not
# treated as an error (apps/android is built in parallel).
if [ -x apps/android/gradlew ]; then
  export JAVA_HOME="${JAVA_HOME:-/opt/android-studio/jbr}"
  (cd apps/android && ./gradlew lint ktlintCheck)
else
  echo "skipping Android lint: apps/android/gradlew not present"
fi
