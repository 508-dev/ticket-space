# Development

The Android app builds with Gradle on the host,
and the PocketBase backend runs from its pinned Dockerfile.

## Commands

```bash
./scripts/check-all.sh    # full local gate (skips absent components)
./scripts/lint.sh         # Biome + version sync + Android lint/ktlint
./scripts/test.sh         # Version-sync tests + Android tests + PocketBase smoke
```

Android-specific (from `apps/android/`):

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug lint test ktlintCheck
```

The authoritative merge gate is `docs/architecture.md` "Engineering rules".
Absent components are skipped with a message, not treated as an error.
CI runs the same Android checks and uploads a debug APK. For release signing
and version synchronization, see `docs/deployment.md` → Releases.

## Worktree Includes

Use `.worktreeinclude` to allowlist ignored local files that should be copied
into new sibling worktrees. Treat entries as gitignore-style path patterns, not
shell globs passed directly to `cp`.

Example:

```text
.env
.env.local
```

Do not include generated state such as caches, local databases, screenshots, or
raw logs. Those should be recreated per worktree.

## Workspace Context

Do not commit `.context/`. Conductor creates it as workspace-local scratch for
agents. Durable runbooks and decisions belong in tracked docs such as this file
and `docs/architecture.md`.

## Docker Build Contexts

Keep `.dockerignore` in sync with the repo shape so Docker build contexts stay
small and secret-safe (the PocketBase image is built from
`apps/pocketbase/Dockerfile`). Exclude local secrets, dependency directories,
caches, agent scratch state, and build outputs.

## Why Host-Run Services

Host-run app services are faster for reload loops, easier for agents to inspect,
and avoid rebuilding containers for normal code changes.

Use the PocketBase container when validating deployment parity.

## Agent Notes

- Keep root scripts as stable entrypoints. Change package-manager internals
  behind them when adapting the repo.
- Copy ignored local config through `.worktreeinclude`; do not commit copied
  `.env` files or generated workspace state.
