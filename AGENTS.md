# AI Agent Development Guide

## Environment

- Only `python3` is guaranteed. Do not assume `python` exists.
- Prefer package scripts and repo-provided entrypoints over raw commands. For
  root tooling use `bun run`; for the Android app use Gradle.
- Treat install, dev, and test commands as executable code. Inspect manifests, package scripts, lockfiles, Docker files, and setup scripts before running them in unfamiliar repos.

## Dependency Supply-Chain Safety

- Bun: keep `bunfig.toml` with `minimumReleaseAge = 604800`.
- CI should use locked installs: `bun install --frozen-lockfile`.
- Commit lockfiles and the Gradle wrapper/version catalog.

## Repository Shape

- `AGENTS.md`: canonical agent operating instructions.
- `MANIFEST.md`: file inventory.
- `DECISIONS.md`: project decisions; points to `docs/architecture.md`.
- `docs`: contributor-facing documentation. The design contract is `docs/architecture.md`.
- `apps/android`: Kotlin/Jetpack Compose app (Gradle root lives here).
- `apps/pocketbase`: pinned PocketBase backend (`Dockerfile`, `pb_migrations/`, `pb_hooks/`, `scripts`).
- `scripts`: stable project entrypoints (`lint`, `format`, `test`, `check-all`, `dev`).
- `version.txt`, `.release-please-*.json`: Android release version and release-please config.
- `.github/workflows`: CI, Conventional Commit PR-title gate, signed GitHub releases.
- `.context`: gitignored workspace-local scratch for agents.

## Android Notes

- `JAVA_HOME=/opt/android-studio/jbr` (a full JDK; `/usr/lib/jvm/java-21-openjdk` is a runtime only).
- `ANDROID_HOME=$HOME/Android/Sdk`.
- adb lives at `$ANDROID_HOME/platform-tools/adb`.
- Do not hand-edit `versionCode`/`versionName`; derive them with
  `scripts/sync-version.sh` from root `version.txt` (see `docs/deployment.md`).
- Release signing reads `apps/android/keystore.properties` (see its `.example`)
  or `TICKETSPACE_KEYSTORE_FILE`, `TICKETSPACE_KEYSTORE_PASSWORD`,
  `TICKETSPACE_KEY_ALIAS`, `TICKETSPACE_KEY_PASSWORD`. Without either complete
  source, release builds are unsigned. Keep release minification off.

## PocketBase Notes

- Schema and API rules live in versioned JS migrations under
  `apps/pocketbase/pb_migrations/`. Never edit the schema by hand in the
  dashboard; write a migration.
- Secrets (superuser credentials, user passwords) never go in git or
  migrations; read them from the environment.

## Development Workflow

- Use `./scripts/check-all.sh` as the local merge gate; the authoritative gate is
  the "Engineering rules" section of `docs/architecture.md`.
- Absent components are skipped with a message, not treated as an error.
- Keep `.worktreeinclude` as a short allowlist of ignored local config copied
  into sibling worktrees, such as `.env`.
- Keep `.dockerignore` in sync with the repo shape so Docker build contexts
  exclude secrets, local dependencies, caches, `.context/`, and generated outputs.

## Editing Rules

- Read target files, callers, exports, tests, and obvious shared utilities before editing.
- Keep edits surgical. Do not reformat unrelated files.
- Add or update tests when behavior changes.
- Update `.env.example` when adding configuration.
- Update docs when changing developer workflows.
- Keep secrets in environment variables or gitignored files (`.env`,
  `local.properties`, `keystore.properties`), never in code.

## `.context/`

Use `.context/` for workspace-local agent scratch only. Do not commit it.

Durable project knowledge belongs in tracked docs:

- Architecture and layout: `docs/architecture.md`.
- Local development runbooks: `docs/development.md`.
- Repeated failure patterns: concise tracked docs, not raw logs or transcripts.

## Validation

Before calling work complete, run the narrowest relevant checks:

```bash
./scripts/lint.sh
./scripts/test.sh
```

For broader changes, run:

```bash
./scripts/check-all.sh
```

The merge gate is in `docs/architecture.md` "Engineering rules". CI's stable
aggregate check is `ci-passed`; PR titles use `conventional-commit`:

- Android: `./gradlew assembleDebug lint test ktlintCheck` (from `apps/android`, with
  `JAVA_HOME=/opt/android-studio/jbr` and `ANDROID_HOME=$HOME/Android/Sdk`).
- Backend: `apps/pocketbase/scripts/smoke.sh`.
- Version sync: `./scripts/sync-version.sh --check` and `./scripts/test-sync-version.sh`.
- The owner merges release PRs; do not merge them or publish manually without
  authorization. `RELEASE_PLEASE_TOKEN` is optional; without it, bot-created
  release PRs do not automatically trigger CI (see `docs/deployment.md`).
