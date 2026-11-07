#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."

ran=0

# Android unit tests when the app is present.
if [ -x apps/android/gradlew ]; then
  export JAVA_HOME="${JAVA_HOME:-/opt/android-studio/jbr}"
  (cd apps/android && ./gradlew test)
  ran=1
else
  echo "skipping Android tests: apps/android/gradlew not present"
fi

# PocketBase smoke test when present.
if [ -x apps/pocketbase/scripts/smoke.sh ]; then
  apps/pocketbase/scripts/smoke.sh
  ran=1
else
  echo "skipping PocketBase smoke test: apps/pocketbase/scripts/smoke.sh not present"
fi

if [ "$ran" -eq 0 ]; then
  echo "no testable components present; nothing to run"
fi
