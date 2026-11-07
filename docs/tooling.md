# Tooling

Root tooling is Bun + Biome, used to lint the PocketBase JS migrations and root
config. Language and runtime conventions live in the apps and `stacks/android`.

## Android Stack

Use `stacks/android` for native Android (Kotlin + Jetpack Compose) projects.
Unlike the other stacks, its reusable part is the release/publishing pipeline
and a set of recurring architecture decisions, not a local-dev script set —
see `stacks/android/README.md` for the full rationale.

Required checks for `stacks/android`:

```bash
./gradlew ktlintCheck
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Track Gradle/Kotlin/Compose/library versions in `gradle/libs.versions.toml`
in the target repo, not in this devkit — Renovate natively understands Gradle
version catalogs and opens PRs against it on the repo's normal cooldown,
which is the expected update path. Don't hand-edit versions opportunistically
outside of that unless fixing something broken.

**AGP 9+ has built-in Kotlin support.** Do not apply
`org.jetbrains.kotlin.android` (`kotlin-android`) alongside it — AGP 9.0+
compiles Kotlin itself, and applying that plugin on top is a hard build
error, not a warning. See `stacks/android/README.md` for the rest of the
recurring Android decisions (Compose over Views, no DI framework by default,
Room vs. a flatter store, SDK level defaults, and the reverse-DNS
application-ID gotcha).

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
