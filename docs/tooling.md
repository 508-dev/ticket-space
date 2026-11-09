# Tooling

Root tooling is Bun + Biome, used to lint the PocketBase JS migrations and root
config. Language and runtime conventions live in the apps and `docs/architecture.md`.

## Android

The native Android app uses Kotlin + Jetpack Compose, with the Gradle root at
`apps/android`. Release/versioning conventions are adopted into the real
workflows and `scripts/sync-version.sh`; see `docs/deployment.md`.

Required checks (from `apps/android`):

```bash
./gradlew assembleDebug lint test ktlintCheck
```

Track Gradle/Kotlin/Compose/library versions in `apps/android/gradle/libs.versions.toml`
— Renovate natively understands Gradle
version catalogs and opens PRs against it on the repo's normal cooldown,
which is the expected update path. Don't hand-edit versions opportunistically
outside of that unless fixing something broken.

**AGP 9+ has built-in Kotlin support.** Do not apply
`org.jetbrains.kotlin.android` (`kotlin-android`) alongside it — AGP 9.0+
compiles Kotlin itself, and applying that plugin on top is a hard build
error, not a warning. See `docs/architecture.md` for Android decisions.

## Dependency Safety

Use dependency cooldowns and frozen installs:

- Bun: `bunfig.toml` sets `minimumReleaseAge = 604800` seconds.
- Gradle: Renovate manages the version catalog on the repo's
  `minimumReleaseAge`; don't hand-edit versions opportunistically.
- CI should use locked or frozen installs.

Regenerate lockfiles when cooldown settings change so CI validates committed
pins instead of resolving fresh dependency graphs.

## Workflow Permissions

Keep workflow permissions at `contents: read` unless a job explicitly calls PR
APIs or posts PR comments.
