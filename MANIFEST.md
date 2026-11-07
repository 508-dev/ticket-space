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
| `scripts/` | Stable entrypoints: `lint`, `format`, `test`, `check-all`, `dev`. |
| `stacks/android/` | Android release/versioning conventions, kept for issue #12. |
| `package.json`, `bun.lock`, `bunfig.toml`, `biome.json` | Root Bun tooling (Biome). |
| `.github/` | Issue/PR templates and minimal CI. |
| `.gitignore`, `.dockerignore`, `.editorconfig`, `.worktreeinclude` | Repo hygiene. |
| `.env.example` | Environment contract template. |
| `.cursor/rules/` | Cursor agent pointer to `AGENTS.md`. |
| `CLAUDE.md`, `CONTRIBUTING.md`, `SECURITY.md`, `LICENSE`, `llms.txt`, `renovate.json` | Standard repo files. |

## Docs

| Path | Purpose |
| --- | --- |
| `docs/architecture.md` | Design contract (authoritative). |
| `docs/development.md` | Local development runbook. |
| `docs/deployment.md` | Deployment notes. |
| `docs/tooling.md` | Tooling conventions. |
| `docs/supply-chain.md` | Dependency cooldown and locked-install policy. |
| `docs/github-workflows.md` | GitHub workflow guidance. |
| `docs/interfaces.md` | Kept from the devkit; not product-specific. |
| `docs/observability.md` | Observability env-var defaults. |
| `docs/secrets.md` | Secret-handling guidance. |
