#!/usr/bin/env bash
# Propagate root version.txt into the Android version literals. No store metadata:
# Ticket Space distributes only an APK through GitHub Releases.
# Usage: scripts/sync-version.sh [--check|--print]
# --print emits safe version=/version_code= assignments for shell callers.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

mode=${1:-write}
case $mode in
    write | --check | --print) ;;
    *)
        echo "usage: ${BASH_SOURCE[0]##*/} [--check|--print]" >&2
        exit 2
        ;;
esac
[[ $# -le 1 ]] || { echo "too many arguments" >&2; exit 2; }

version=$(cat version.txt)
[[ $version =~ ^(0|[1-9][0-9]{0,3})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})$ ]] || {
    echo "version.txt must hold a bare MAJOR.MINOR.PATCH version (minor/patch <=999)" >&2
    exit 1
}
major=${BASH_REMATCH[1]} minor=${BASH_REMATCH[2]} patch=${BASH_REMATCH[3]}
version_code=$((major * 1000000 + minor * 1000 + patch))
if ((version_code < 1 || version_code > 2100000000)); then
    echo "derived versionCode must be between 1 and 2100000000" >&2
    exit 1
fi

if [[ $mode == --print ]]; then
    printf 'version=%s\nversion_code=%s\n' "$version" "$version_code"
    exit 0
fi

gradle_file=apps/android/app/build.gradle.kts
# Require exactly one literal of each kind, so a reformatted declaration cannot
# silently no-op (or leave an additional conflicting declaration behind).
if [[ $(grep -cE '^[[:space:]]*versionCode = [0-9]+$' "$gradle_file") != 1 ]] ||
    [[ $(grep -cE '^[[:space:]]*versionName = "[^"]*"$' "$gradle_file") != 1 ]]; then
    echo "versionCode and versionName in $gradle_file must each be a plain literal on its own line" >&2
    exit 1
fi
gradle_updated=$(sed -E \
    -e "s/^([[:space:]]*)versionCode = [0-9]+$/\1versionCode = ${version_code}/" \
    -e "s/^([[:space:]]*)versionName = \"[^\"]*\"$/\1versionName = \"${version}\"/" \
    "$gradle_file")

if [[ $mode == --check ]]; then
    if ! diff -u "$gradle_file" - <<<"$gradle_updated"; then
        echo "Derived version files are stale. Run: scripts/sync-version.sh" >&2
        exit 1
    fi
    exit 0
fi

printf '%s\n' "$gradle_updated" > "$gradle_file"
echo "version ${version} -> versionCode ${version_code}, ${gradle_file}"
