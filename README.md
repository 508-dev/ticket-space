# Ticket Space

Ticket Space is an Android AR todo app for physical spaces. Problems in a room —
a burnt-out bulb, a wobbly table, a leak — are filed as todos and pinned to the
exact spot where they are, then shown to anyone who opens the AR view in that
room. It is inspired by the community-space lighting in Cory Doctorow's
*Walkaway*.

The app is a Kotlin/Jetpack Compose Android client backed by a self-hosted
PocketBase server, whose schema and API rules live in versioned JS migrations.
It is currently pre-MVP: the P0 (photo tickets) and P1 (pins in space) features
are defined but not yet built.

## Layout

```text
apps/android/      Kotlin/Compose app (Gradle root lives here)
apps/pocketbase/   Pinned PocketBase backend (Dockerfile, pb_migrations/, pb_hooks/)
docs/              Architecture (the design contract), development, deployment
scripts/           Repo entrypoints (lint, format, test, check-all, dev)
stacks/android/    Android release/versioning conventions (for issue #12)
```

## Read Next

- [docs/architecture.md](docs/architecture.md) — the design contract for contributors and agents.
- [docs/development.md](docs/development.md) — local development runbook.
- [docs/deployment.md](docs/deployment.md) — deployment notes.
- [AGENTS.md](AGENTS.md) — agent operating instructions.
- [DECISIONS.md](DECISIONS.md) — project decisions.
- [MANIFEST.md](MANIFEST.md) — file inventory.

The apps carry their own build/run instructions in `apps/android/README.md` and
`apps/pocketbase/README.md`.

## Status

Pre-MVP. No release yet; CI/release is tracked in GitHub issue #12.
