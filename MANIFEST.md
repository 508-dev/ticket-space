# Ticket Space Manifest

A short inventory of what remains after the devkit template selection. The full
adopt/adapt/skip/delete/defer selection report lives in the PR that applied this
cleanup.

## Top-Level

| Path | Purpose |
| --- | --- |
| `AGENTS.md` | Canonical agent operating instructions. |
| `MANIFEST.md` | This inventory. |
| `DECISIONS.md` | Project decisions; points to `docs/architecture.md`. |
| `docs/` | Contributor documentation (see below). |
| `apps/android/` | Kotlin/Jetpack Compose app (Gradle root). |
| `apps/pocketbase/` | Pinned PocketBase backend with JS migrations. |
| `scripts/` | Stable entrypoints plus `sync-version.sh` and its regression tests. |
| `version.txt`, `.release-please-config.json`, `.release-please-manifest.json` | Android version source and release-please configuration/state. |
| `apps/android/keystore.properties.example` | Local signing config template; real material is gitignored. |
| `package.json`, `bun.lock`, `bunfig.toml`, `biome.json` | Root Bun tooling (Biome). |
| `.github/` | Issue/PR templates; Android/Biome/PocketBase CI, PR-title check, signed stable and beta GitHub APK releases. |
| `.gitignore`, `.dockerignore`, `.editorconfig`, `.worktreeinclude` | Repo hygiene. |
| `.env.example` | Environment contract template. |
| `.cursor/rules/` | Cursor agent pointer to `AGENTS.md`. |
| `CLAUDE.md`, `CONTRIBUTING.md`, `SECURITY.md`, `LICENSE`, `llms.txt`, `renovate.json` | Standard repo files. |

## Docs

| Path | Purpose |
| --- | --- |
| `docs/architecture.md` | Design contract (authoritative). |
| `docs/development.md` | Local development runbook. |
| `docs/deployment.md` | Backend deployment and Android release/signing runbook. |
| `docs/beta-builds.md` | Per-branch beta installation, signing, and ARCore authorization. |
| `docs/tooling.md` | Tooling conventions. |
| `docs/supply-chain.md` | Dependency cooldown and locked-install policy. |
| `docs/github-workflows.md` | GitHub workflow guidance. |
| `docs/observability.md` | Logging and telemetry posture. |
| `docs/secrets.md` | Where secrets live and how they are handled. |
