# Deployment

Ticket Space's PocketBase backend runs as a single Docker app on **Coolify** at
`https://ticketspace.508.dev`. There is no separate web frontend or database
service to deploy — PocketBase embeds SQLite and serves its own REST and
realtime APIs.

## Coolify application

Create one application in Coolify with these settings:

| Setting | Value |
| --- | --- |
| Source | Public repository `github.com/508-dev/ticket-space` |
| Branch | `main` |
| Build pack | Dockerfile |
| Dockerfile location | `/apps/pocketbase/Dockerfile` |
| Base directory | `/apps/pocketbase` |
| Ports exposes | `8090` |
| Domain | `https://ticketspace.508.dev` |
| Health check | `/api/health` |

The image is built from `apps/pocketbase/Dockerfile`, which downloads and
checksum-verifies the pinned PocketBase release (see
`apps/pocketbase/README.md`). Because the Dockerfile pins the `linux/amd64`
asset, ensure the build targets `linux/amd64`.

## Persistent storage

Add a persistent volume mounted at `/pb/pb_data`. This holds the SQLite
database, uploaded files and backups — everything durable.

- Host path (or Coolify volume) → `/pb/pb_data`

Migrations in `pb_migrations/` and hooks in `pb_hooks/` are baked into the
image and re-applied/loaded automatically on startup, so they do not need a
volume.

## First boot

1. Set `PB_SUPERUSER_EMAIL` and `PB_SUPERUSER_PASSWORD` as secret environment
   variables on the Coolify application. The container entrypoint
   (`apps/pocketbase/docker-entrypoint.sh`) upserts that superuser on every
   start, so rotating the password is a matter of changing the variable and
   redeploying.
2. Deploy the application and confirm `/api/health` returns `200`.

   Without those variables, create the superuser from inside the running
   container instead (from the Coolify UI, open a terminal on the container):

   ```bash
   docker exec <container> /pb/pocketbase superuser upsert EMAIL PASSWORD
   ```

3. Seed the app users with the bootstrap script:

   ```bash
   PB_URL=https://ticketspace.508.dev \
   PB_SUPERUSER_EMAIL=<superuser-email> \
   PB_SUPERUSER_PASSWORD=<superuser-password> \
   TICKETSPACE_USERS='email:password:name;email:password:name' \
     apps/pocketbase/scripts/bootstrap.sh
   ```

   `TICKETSPACE_USERS` is `;`-separated and `:`-delimited. The script is
   idempotent — safe to re-run to add or update accounts.

Secrets (superuser credentials and user passwords) live in environment
variables or a gitignored `.env`, never in git or in the migrations.

## Backups

Backups are PocketBase's built-in scheduled backups, configured by the
`1794072002_initial_settings.js` migration:

- schedule: daily at `03:00`
- retention: keep the last `7`
- storage: on the persistent volume (under `/pb/pb_data/backups`)

No external object storage is configured for the POC. Restore is done with
`/pb/pocketbase admin restore <backup>` (or the equivalent superuser UI/API
flow) against the same volume. For off-site backups later, attach the Coolify
scheduled backup to the `/pb/pb_data` volume, since the built-in backups live
inside it and are not covered by Coolify's app-level backups otherwise.

## Rollback

Roll back by redeploying a previous `main` commit. Migrations are forward-only
in practice; a rollback that removes collections requires a new `down`
migration. The `main` branch is the deploy source — merge to `main` to ship.

## Releases

Android releases are separate from backend deployment: **merges to `main` groom
a release PR; the owner merges that PR when ready to ship.** There is no Google
Play or F-Droid publishing, and release minification remains disabled.

1. Squash-merge Conventional Commit PRs into `main`. The PR-title check enforces
   the shape: `feat:` bumps minor, `fix:`/`perf:` bump patch, and `!`/`BREAKING
   CHANGE:` bumps minor before 1.0 and major afterwards. Other types (such as
   `ci:`, `docs:` and `chore:`) do not trigger a release alone.
2. `.github/workflows/release.yml` runs release-please on `main`, opening or
   updating a standing `chore(main): release ...` PR with `version.txt`,
   `CHANGELOG.md` and the manifest. The same job runs `scripts/sync-version.sh`
   and commits derived Gradle version literals onto that PR, never onto `main`.
3. The owner reviews and merges the release PR. Release-please tags `vX.Y.Z`
   and creates the GitHub Release. In the **same workflow run**, `publish`
   checks out that tag, validates the keystore, builds and verifies a signed
   release APK, and uploads `ticketspace-X.Y.Z.apk` and its SHA-256 checksum.
   This is intentionally not a separate tag-triggered workflow: tags created
   with `GITHUB_TOKEN` do not trigger another workflow.

### Per-branch beta builds

`.github/workflows/beta.yml` builds Ticket Space Beta for every pushed non-main
branch except release-please and `gh-pages` branches. Signed builds become
`beta-<branch>-<run>` pre-releases with `--latest=false`; older betas for that
branch are pruned, and branch deletion cleans them up once the workflow is on
`main`. Betas use repository-level `BETA_KEYSTORE_BASE64`,
`BETA_KEYSTORE_PASSWORD`, `BETA_KEY_ALIAS`, and `BETA_KEY_PASSWORD`, never the
`Prod` environment or release key. Missing beta credentials reduce the workflow
to a compile-only check. See `docs/beta-builds.md` for installation, local
signing, versioning, and the separate Android OAuth client required for beta
Cloud Anchors.

### Version source and first release

Root `version.txt` is the sole Android version source. Run
`./scripts/sync-version.sh` after changing it; commit the generated literals in
`apps/android/app/build.gradle.kts`. `versionCode` is
`major * 1000000 + minor * 1000 + patch` (minor/patch at most 999), and
`versionName` is the semantic version. Keep both as plain literals on their own
lines. CI runs `./scripts/sync-version.sh --check` and rejects drift. Run
`./scripts/test-sync-version.sh` for the isolated regression tests.

The app starts at `0.1.0` / `1000`. The manifest is deliberately empty at
bootstrap, with `initial-version: 0.1.0` and component-free tags in the config:
no version has been released yet. The first release PR includes the existing
feature history and proposes **v0.1.0**, not v0.2.0. Do not manually seed a
v0.1.0 tag or claim 0.1.0 as already released in the manifest. After that first
release, release-please manages the manifest and bumps from the released version.

### Secrets and owner checklist

Signing secrets already live under GitHub **Settings → Environments → Prod**,
not repository-level secrets. Both release jobs declare `environment: Prod`.

| Secret | Purpose |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded release keystore; whitespace is stripped when restoring. |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password. |
| `RELEASE_KEY_ALIAS` | Signing key alias. |
| `RELEASE_KEY_PASSWORD` | Key password (same as store password for PKCS12). |
| `RELEASE_PLEASE_TOKEN` (optional) | PAT allowing release-PR creation and derived-file pushes to trigger CI. |

`RELEASE_PLEASE_TOKEN` is not currently configured; the workflow falls back to
`GITHUB_TOKEN`. **Bot-created PRs and their bot-pushed updates do not trigger CI
or PR-title workflows with that fallback.** Before merging each release PR,
the owner must trigger checks on its final commit: use the Actions **Run
workflow** UI (CI, selecting the release branch), or
`gh workflow run ci.yml --ref <release-branch>`, and verify the results. A
human close/reopen of the PR can also trigger both PR checks; if the bot
subsequently pushes derived files, check the new head again. Adding a suitable
PAT as `Prod`'s `RELEASE_PLEASE_TOKEN` enables automatic checks instead. Do not
bypass required checks just to merge a release PR.

Require `ci-passed` and `conventional-commit` in branch protection. CI's Android
job uploads `ticketspace-debug-apk`, retained for 14 days. Documentation-only
PRs may skip component jobs after successful path detection; main/manual runs
always test both. If no keystore secret is configured, release tagging still
works, but APK publishing is skipped with a warning. An invalid keystore or
incomplete signing credentials fails publishing rather than uploading an
unsigned APK. For a failed publish, fix the underlying issue and re-run the
failed publish job of that original run (a fresh release-please run need not
report that already-created release again).

**Cloud Anchor authorization:**

- CI debug APKs use the runner's throwaway debug signing key, not the owner's
  registered local debug SHA-1. Keyless Cloud Anchor authentication will **not
  authorize** on CI debug builds. Use release builds (after registration below)
  or local debug builds for Cloud Anchor testing.
- Register the **release key's SHA-1 as a second Android OAuth client** for
  `dev.co508.ticketspace` in the `ticketspace-508dev` Google Cloud project.
  Keep the existing local-debug client. The app ships no API key/client ID.
  The owner can read the public fingerprint locally with `keytool -list -v
  -keystore /path/to/release.keystore -alias ticketspace` (password prompt).
- Signed betas need a separate **Android OAuth 2.0 Client ID** for package
  `dev.co508.ticketspace.beta` and beta-key SHA-1
  `29:0E:D5:BB:05:C3:FF:86:B6:6F:0F:1E:8A:C0:08:E9:D8:C0:41:64`.
  See `docs/beta-builds.md`.

### Local signing

Copy `apps/android/keystore.properties.example` to the gitignored
`apps/android/keystore.properties`, or export all four variables:
`TICKETSPACE_KEYSTORE_FILE`, `TICKETSPACE_KEYSTORE_PASSWORD`,
`TICKETSPACE_KEY_ALIAS`, `TICKETSPACE_KEY_PASSWORD`. A complete properties file
takes precedence; `storeFile` is absolute or relative to `apps/android`.
Gradle uses `providers.environmentVariable` for configuration-cache tracking.
With neither complete source, `./gradlew assembleRelease` from `apps/android`
produces `app/build/outputs/apk/release/app-release-unsigned.apk`.

With signing configured, build using `./gradlew assembleRelease
--no-configuration-cache` to avoid persisting signing passwords in Gradle's
local configuration cache. CI also disables that cache for the signing step.
Never commit keystores, signing properties or passwords; back up the release
key and password offline. A different signing certificate cannot update an
already installed APK, so losing the key forces users to reinstall.
