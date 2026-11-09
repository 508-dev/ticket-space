# Ticket Space Decisions

Last reviewed: 2026-11-08

This is the decision authority for Ticket Space. The design contract is
`docs/architecture.md`; change a decision there first, then in code.

## Dependency Cooldowns

Decision: use dependency cooldowns where the tooling supports them — Bun via
`bunfig.toml`, Gradle via Renovate's `minimumReleaseAge`.

Why: new package versions are a supply-chain risk window.

Deviate when: a security fix or production incident requires an immediate update. Document the exception in the PR.

## Locked Installs

Decision: commit lockfiles and use frozen or locked installs in CI
(`bun install --frozen-lockfile`; the committed Gradle wrapper for Android).

Why: reproducible dependency resolution for agents and humans.

## Bun For Root Tooling

Decision: Bun is the only root package manager, used to run Biome over the
PocketBase JS migrations and root config.

Why: it is fast, simple, and already governed by `bunfig.toml`'s cooldown.

## `.context/` Workspace Memory

Decision: keep `.context/` gitignored as workspace-local operational memory. Do not ship it as tracked content.

Why: runbooks and repeated-failure notes prevent re-deriving solutions; durable knowledge belongs in tracked docs.

## Secrets In The Environment

Decision: keep secrets in environment variables or gitignored files (`.env`,
`local.properties`, `keystore.properties`); never commit them.

Why: git history and CI logs are not secret stores. PocketBase bootstrap reads
credentials from the environment.

## GitHub Hygiene

Decision: pinned action SHAs, least-privilege workflows (`contents: read`), and
a Renovate cooldown policy.

Why: collaboration and security hygiene are broadly useful and easy to drift.

## Ticket Space Product Decisions

The product and engineering decisions — scope, backend schema, Android
architecture, AR/spatial model, and the local merge gate — are recorded in
`docs/architecture.md`. Summary:

- Android app (Kotlin/Compose) + PocketBase backend with JS migrations. No
  Python, Ruby, or TypeScript app, no pnpm, no Postgres/Redis, no web frontend.
- MVP v0.1.0 = P0 (photo tickets) + P1 (pins in space).
- Release-please grooms human-merged release PRs; a single main-push workflow
  tags and publishes a signed APK to GitHub Releases. `version.txt` is the source
  of truth, with literal Gradle versions derived by `scripts/sync-version.sh`.
  Secrets are in GitHub Environment `Prod`. No Play/F-Droid publishing or R8
  enablement. See `docs/deployment.md`.
