#!/usr/bin/env bash
# Exercise the real script against isolated fixtures, never editing the app.
set -euo pipefail
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/scripts" "$fixture/apps/android/app"
cp "$root/scripts/sync-version.sh" "$fixture/scripts/"
cd "$fixture"

expect_failure() {
    if "$@" > /dev/null 2>&1; then
        echo "expected failure: $*" >&2
        exit 1
    fi
}

cat > apps/android/app/build.gradle.kts <<'EOF'
android {
    defaultConfig {
        versionCode = 1000
        versionName = "0.1.0"
    }
}
EOF
echo 0.1.0 > version.txt
./scripts/sync-version.sh --check
[[ $(./scripts/sync-version.sh --print) == $'version=0.1.0\nversion_code=1000' ]]
echo 1.2.3 > version.txt
expect_failure ./scripts/sync-version.sh --check
./scripts/sync-version.sh
./scripts/sync-version.sh --check
grep -q 'versionCode = 1002003' apps/android/app/build.gradle.kts
grep -q 'versionName = "1.2.3"' apps/android/app/build.gradle.kts
cp apps/android/app/build.gradle.kts before
./scripts/sync-version.sh
cmp before apps/android/app/build.gradle.kts
for version in 0.0.0 0.1000.0 0.1.1000 01.2.3 1.02.3 1.2.03 1.2.3-beta 9999.0.0 '1.2.3 extra'; do
    echo "$version" > version.txt
    expect_failure ./scripts/sync-version.sh --print
done
echo 0.1.0 > version.txt
sed -i 's/versionCode = 1002003/versionCode = 1000 + 1/' apps/android/app/build.gradle.kts
expect_failure ./scripts/sync-version.sh
expect_failure ./scripts/sync-version.sh --unknown
expect_failure ./scripts/sync-version.sh --check extra
echo 'sync-version tests passed'
