#!/usr/bin/env bash
#
# Run the on-device backend integration test (BackendIntegrationTest) against
# the live PocketBase backend.
#
# Reads TS_BASE_URL, TS_TEST_EMAIL and TS_TEST_PASSWORD from the environment,
# falling back to the repo-root `.env` when they are unset (environment wins).
# The values are passed to Gradle only as instrumentation-argument properties
# and are never echoed; the Gradle command line (which would contain them) is
# never printed.
#
# Usage: apps/android/scripts/integration-test.sh [ANDROID_SERIAL]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ANDROID_DIR="$REPO_ROOT/apps/android"

# Load the repo-root .env, but only for keys that are not already set in the
# environment (the caller's environment takes precedence over the file).
if [ -f "$REPO_ROOT/.env" ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in '' | \#*) continue ;; esac
    key="${line%%=*}"
    value="${line#*=}"
    case "$value" in
      \"*\") value="${value#\"}"; value="${value%\"}" ;;
      \'*\') value="${value#\'}"; value="${value%\'}" ;;
    esac
    if [ -z "${!key+x}" ]; then
      export "$key=$value"
    fi
  done < "$REPO_ROOT/.env"
fi

: "${TS_BASE_URL:?TS_BASE_URL is required (set it or add it to $REPO_ROOT/.env)}"
: "${TS_TEST_EMAIL:?TS_TEST_EMAIL is required (set it or add it to $REPO_ROOT/.env)}"
: "${TS_TEST_PASSWORD:?TS_TEST_PASSWORD is required (set it or add it to $REPO_ROOT/.env)}"

SERIAL="${1:-${ANDROID_SERIAL:-NAAIB700H902PD8}}"

export JAVA_HOME="${JAVA_HOME:-/opt/android-studio/jbr}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SERIAL="$SERIAL"

# Never echoed: these carry the credentials into the instrumentation arguments.
args=(
  "-Pandroid.testInstrumentationRunnerArguments.tsBaseUrl=$TS_BASE_URL"
  "-Pandroid.testInstrumentationRunnerArguments.tsEmail=$TS_TEST_EMAIL"
  "-Pandroid.testInstrumentationRunnerArguments.tsPassword=$TS_TEST_PASSWORD"
)

cd "$ANDROID_DIR"
./gradlew connectedDebugAndroidTest "${args[@]}"
