# Android Stack

Use this stack when the target project is a native Android app (Kotlin +
Jetpack Compose). An Android app has no server-side "run the dev server" shape
and no package-manager cooldown mechanism — the reusable part isn't local dev
tooling, it's the release and publishing pipeline. This stack is a convention
pack for that: how versioning works, how CI ships signed builds, and the
architecture decisions that keep coming up across 508.dev's Android apps. It was
distilled from two sibling apps built on this pattern — `soundboard` (the more
complete one; it has the full release pipeline) and `emotion-tracker` (the first
one; it set the baseline Gradle/Compose conventions). Where they differ, the
reasons are called out below.

> F-Droid distribution was removed from this repo's scope and is deferred to a
> later issue; only the release/versioning machinery is kept here (see GitHub
> issue #12).

## Contains

- `README.md`: this file.
- `scripts/sync-version.sh`: derives `versionCode` and store release notes from
  `version.txt`.
- `github/release.yml.example`, `github/pr-title.yml.example`: the release
  pipeline and the PR-title gate it depends on.
- `release-please-config.json.example`: release-please config for a
  single-package Android repo.
- `version.txt.example`: seed version file.
- `keystore.properties.example`: local release-signing config, mirrored by the
  CI environment variables in `release.yml`.

This stack does not include an app scaffold (no `MainActivity`, no
`build.gradle.kts`). Android app structure is too product-specific to template
usefully; what's reusable is the release machinery and the decisions below.

## Apply

Every example file below uses the package `dev.co508.example`, app slug
`example`, and env var prefix `EXAMPLE_` as placeholders. Replace all three
consistently when copying files in:

```bash
mkdir -p scripts .github/workflows
cp stacks/android/scripts/*.sh scripts/
cp stacks/android/version.txt.example version.txt
cp stacks/android/release-please-config.json.example .release-please-config.json
cp stacks/android/keystore.properties.example .
cp stacks/android/github/release.yml.example .github/workflows/release.yml
cp stacks/android/github/pr-title.yml.example .github/workflows/pr-title.yml
```

Then, in every copied file:

1. Replace `dev.co508.example` with the app's real application ID. See
   "Application ID" below before picking one.
2. Replace the `example` app slug (artifact file names) with the app's real
   short name.
3. Replace `EXAMPLE_` (the `EXAMPLE_KEYSTORE_*`-style env var prefix in
   `release.yml` / `keystore.properties.example`) with the app's slug,
   upper-cased.
4. Rewrite `.release-please-config.json`'s `package-name` to match.

Then read "Release & Versioning" below, set up the repository and secrets it
describes (`docs/deployment.md` in the target repo should carry a copy of that
section once adapted), and bake the version:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

## Recurring Architecture Decisions

These are the calls that were open on the first Android app under this pattern
and stayed closed on the second. Treat them as defaults, and write a
`DECISIONS.md` entry in the target repo (not here) when the target app deviates
— that's what both source apps did.

- **Jetpack Compose over Views.** Both apps use Compose (Material 3)
  exclusively. It's the better fit for custom gesture-driven controls (`Canvas`
  + `pointerInput`), and there's no reason to introduce the View system
  alongside it.
- **No DI framework by default.** Two or three hand-constructed objects in the
  `Application` subclass, reached through a `CreationExtras` helper, is not a
  graph that earns Hilt's ceremony. Introduce Hilt only once the object graph
  actually outgrows manual wiring, and say so in `DECISIONS.md` when it happens.
- **Local storage: Room vs. a flatter store.** Default to Room when the data has
  real relations, queries, or history. Reach for `DataStore<T>` with a
  `kotlinx.serialization` codec instead when the entire persisted state is one
  small ordered list or document with no queries. Revisit if the data model
  grows relations later.
- **SDK levels: `minSdk 26`, `targetSdk` one below `compileSdk`.** `minSdk 26`
  (Android 8.0, Oreo) buys `java.time` without desugaring, native notification
  channels, and `AudioFocusRequest`. Keep `targetSdk` one below `compileSdk`
  until that API level's behavior changes have been reviewed. Check
  `gradle/libs.versions.toml` in the target repo (or a sibling app) for current
  values rather than trusting a number written here — these move.
- **Application ID: reverse-DNS, and Android's package-name rule is stricter
  than Kotlin's.** A leading-digit segment (`dev.508.app`) is invalid twice
  over: Kotlin/Java identifiers can't start with a digit, and even the
  underscore-prefixed workaround (`dev._508.app`) still fails, because Android's
  manifest package-name rule requires every segment to *start with a letter*.
  Verify a new namespace end to end with `./gradlew assembleDebug` before
  treating it as settled. 508.dev's own apps use `dev.co508.<app>`. Treat the
  chosen application ID as permanent once a store listing exists under it.
- **AGP 9+ has built-in Kotlin support — do not apply
  `org.jetbrains.kotlin.android` alongside it.** AGP 9.0+ compiles Kotlin
  itself; applying the `kotlin-android` plugin on top is a hard build error. The
  `org.jetbrains.kotlin.plugin.compose` and
  `org.jetbrains.kotlin.plugin.serialization` sub-plugins are still applied
  normally. JVM target comes from `android.compileOptions.targetCompatibility`
  directly. See
  <https://developer.android.com/build/migrate-to-built-in-kotlin>.
- **Renovate handles Gradle version bumps; don't hand-edit versions
  opportunistically.** Renovate natively understands Gradle version catalogs —
  no extra config needed for it to open PRs against `gradle/libs.versions.toml`
  on the repo's normal cooldown.
- **Gradle daemon JVM pin.** Pin the Gradle daemon's JVM in
  `gradle/gradle-daemon-jvm.properties` rather than assuming the system JDK
  matches what AGP requires. From the CLI on a machine whose system JDK differs,
  invoke as `JAVA_HOME=/opt/android-studio/jbr ./gradlew <task>`.
- **Deferred `androidTest` coverage is a legitimate default, not neglect.** Both
  apps ship JVM unit tests only (pure logic) and defer instrumented
  (`androidTest`) coverage for anything needing a real device or emulator. Add
  `androidTest` when the deferred surface next changes shape.

## Release & Versioning

The release model is: **merging to `main` only grooms a standing release PR;
merging *that* PR is what tags a version and publishes it.**

```
merge a PR to main
  -> release-please opens/updates "chore(main): release X.Y.Z"
     (version.txt + CHANGELOG.md, from Conventional Commit PR titles)
     -> CI tops the PR up with files derived from those
        (versionCode, versionName, store release notes)
  -> you merge the release PR when you want to ship
     -> release-please tags vX.Y.Z and creates the GitHub Release
        -> publish job: signed APK + AAB -> GitHub Release and Play
```

Why not release on every merge to `main`: that makes every merge a public
release with no way to batch commits into one version, and it needs CI to push a
version-bump commit directly to `main` — which means either leaving `main`
unprotected or granting a bot a branch-protection bypass. The release-PR model
needs neither: the bot only ever opens a PR, and the human merge is the
deliberate act.

**`release-please` and `publish` run as two jobs in one workflow run, on
purpose.** A tag or PR created with `GITHUB_TOKEN` does not trigger another
workflow run. A separate tag-triggered publish workflow is the obvious design
and will silently never fire. `github/release.yml.example` keeps both jobs in one
`on: push: branches: [main]` workflow for this reason.

PR titles are squash-merged into `main`'s commit subjects, and release-please
reads those subjects to decide the next version:

| PR title prefix | Effect |
| --- | --- |
| `feat: ...` | minor bump |
| `fix: ...`, `perf: ...` | patch bump |
| `feat!: ...`, or a `BREAKING CHANGE:` footer | minor bump pre-1.0 (`bump-minor-pre-major`), major after |
| `docs:`, `chore:`, `ci:`, `refactor:`, `test:`, `build:`, `style:` | no release on their own |

`github/pr-title.yml.example` enforces this shape on open PRs with one regex —
deliberately no third-party action, since a release-gating check is a bad place
to add a supply-chain dependency.

### `versionCode` is derived, and stays a literal

`version.txt` is the only source of truth for the version. Everything else —
`versionCode`, `versionName` in `app/build.gradle.kts`, and the store release
notes — is derived from it by `scripts/sync-version.sh`. Never hand-edit
`versionCode`/`versionName`; run the script after changing `version.txt` and
commit what it writes. CI runs `sync-version.sh --check` and fails the build on
drift.

`versionCode` is computed as `major * 1000000 + minor * 1000 + patch`, never
chosen by hand. That keeps it strictly increasing across any semver bump — which
matters because **Play permanently rejects a `versionCode` it has already seen
for an app, including one from a deleted release.**

Both `versionCode` and `versionName` must stay plain literals on their own line
in `app/build.gradle.kts`, never an expression — `sync-version.sh` rewrites them
by regex and only trusts a literal it can prove it changed.

## GitHub Actions

`github/release.yml.example` covers both jobs described above: `release-please`
(grooms/tags the release) and `publish` (builds signed artifacts and ships them
to GitHub Releases and Play). Both declare a GitHub **Environment** (`Prod` in
the example) for their secrets rather than repo-level secrets — see "Secrets"
below for why that distinction matters.

`github/pr-title.yml.example` is a required check on PRs, gating the Conventional
Commit shape release-please depends on.

Adapt, don't skip, the following before relying on either:

- `concurrency: { group: release, cancel-in-progress: false }` — never let two
  releases interleave. Queue, don't cancel — a cancelled publish leaves a tagged
  release with no artifacts.
- The `release-please` job checks out the release PR's branch (not `main`) with a
  token that has push access, purely to run `sync-version.sh` and commit the
  derived files back onto the PR before a human reviews it.
- The `publish` job builds from the **tag**, not whatever `main` has drifted to
  since the release PR was opened.
- "Work out what is configured" degrades gracefully: no signing key means the
  release is tagged with notes but nothing is published; no Play credentials
  means GitHub Releases still ship. Useful while store accounts are still being
  set up — don't turn this into a hard failure.

## Store Publishing

### Signing

There are two signatures in play, and they are not interchangeable — Android
refuses to update an installed app with a differently-signed APK:

| Channel | Signed by |
| --- | --- |
| GitHub Release APK | the app's own release key |
| Google Play | Play App Signing (unless the same key above was uploaded to it at app creation) |

Upload the app's own key to **Play App Signing at app-creation time**, or Google
generates one instead and Play builds stop being interchangeable with everything
else. This choice is permanent.

CI reads the keystore from `EXAMPLE_KEYSTORE_FILE` / `EXAMPLE_KEYSTORE_PASSWORD`
/ `EXAMPLE_KEY_ALIAS` / `EXAMPLE_KEY_PASSWORD` environment variables (see
`keystore.properties.example` for the local equivalent, read by
`app/build.gradle.kts` when no env vars are set). With neither source configured,
a release build is simply unsigned.

### Google Play

CI uploads the AAB to the **internal** track only and stops. Promotion to
production stays a manual Play Console step on purpose. Store graphics aren't
uploaded by this pipeline; set them once in the Play Console UI. Upload the R8
mapping file too, or every Play crash report comes back as an unreadable
obfuscated stack trace.

The first Play release is unavoidably manual: the Play Developer API cannot
create an app or publish to a track that has never received one manual upload.

## Secrets

| Secret | Used for | Without it |
| --- | --- | --- |
| `RELEASE_KEYSTORE_BASE64` | app signing key, `base64 -w0` of the keystore | nothing is published; the release is still tagged |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password | as above |
| `RELEASE_KEY_ALIAS` | key alias within the keystore | as above |
| `RELEASE_KEY_PASSWORD` | same value as the keystore password — PKCS12 keystores have no separate key password | as above |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Developer API service account, whole JSON file | Play upload is skipped; GitHub Releases still publish |
| `RELEASE_PLEASE_TOKEN` | fine-grained PAT so the release PR itself triggers CI | falls back to `GITHUB_TOKEN`; the release PR arrives unchecked |

Add these under a GitHub **Environment** (e.g. `Prod`), not repo-level Secrets,
if the workflow declares `environment: Prod` on the jobs that need them (the
example does). A secret added to the wrong one of those two places doesn't error
— the job just runs with `secrets.WHATEVER` silently empty.

**The signing key cannot be rotated in practice.** Android refuses to update an
installed app with a differently-signed APK, so losing the keystore or its
password means every existing user has to uninstall and reinstall, losing their
local data. Back it up offline and off any development machine.

## Docs To Write In The Target Repo

This stack intentionally does not ship `docs/deployment.md`, `docs/tooling.md`,
or `docs/secrets.md` content — the target repo's copies of those (from the devkit
root) are meant to be *rewritten* for the app, not appended to.

## Agent Notes

- Do not copy this stack just because a repo happens to contain Kotlin. Select
  it only when the target is a native Android app.
- Treat the architecture decisions above as defaults to open a `DECISIONS.md`
  entry against, not silent requirements — write down *why* when the target app
  deviates.
- Never hand-edit `versionCode`, `versionName`, or `CHANGELOG.md`. Both are
  generated from `version.txt` — change that, then run `scripts/sync-version.sh`.
- Do not turn the version literals in `app/build.gradle.kts` into an expression,
  or move them onto a shared line — `sync-version.sh` parses them textually.
- Do not add publishing to a new store, or promote a Play upload past the
  internal track, without the maintainer asking. Release signing material and
  store submissions are deliberate human decisions.
- Never print keystore contents or echo an `EXAMPLE_KEYSTORE_*` secret value in a
  workflow step.
