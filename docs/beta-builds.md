# Beta Builds

Every push to a branch other than `main`, `gh-pages`, or a
`release-please--...` branch builds **Ticket Space Beta**. The workflow can also
be started manually with **Run workflow**. It publishes a GitHub pre-release
containing one APK, then removes older beta releases for that branch. Deleting
a branch removes its beta releases once `beta.yml` is on the default branch.

Betas use the application ID `dev.co508.ticketspace.beta`, an orange launcher
background, and a separate signing key, so they install beside the production
app. Each build uses the workflow run number as its `versionCode`; its
`versionName` is `<version.txt>-beta.<run>+<short sha>`. A new tag per build
lets update clients distinguish successive betas.

The beta inherits the release build configuration and uses the same production
PocketBase backend, `https://ticketspace.508.dev`. The debug-only
`local.properties` backend override does not apply. The FileProvider authority
uses `${applicationId}.files` in the manifest and `${context.packageName}.files`
in the Kotlin caller, so it moves to the beta package automatically. There are
no deep links, OAuth redirect schemes, or other backend settings keyed to the
production application ID. PocketBase email/password authentication is
unchanged.

## Install

Download the latest APK for your branch from
<https://github.com/508-dev/ticket-space/releases> and open it on your phone.
Beta releases are marked **Pre-release** and their titles begin with `Beta`.
They are created with `--latest=false`, so they do not replace the stable
release returned by GitHub's latest-release API.

### Obtainium

1. Add the app from `https://github.com/508-dev/ticket-space`.
2. Turn on **Include prereleases**.
3. Set **Filter APKs by regular expression** to `ticketspace-beta`.
4. Set **Filter release titles by regular expression** to `^Beta`.

The APK filter prevents Obtainium from selecting the production APK attached
to a stable release. The title filter keeps it on beta pre-releases. Obtainium
will notify you when a new beta is published and can install it over the
previous beta while the signing key remains the same.

## Signing And Secrets

The repository secrets `BETA_KEYSTORE_BASE64`, `BETA_KEYSTORE_PASSWORD`,
`BETA_KEY_ALIAS`, and `BETA_KEY_PASSWORD` already exist. They belong only to
the beta app; the production signing key and its `Prod` environment are not
used by this workflow. If the beta key is lost, beta users must uninstall and
reinstall Ticket Space Beta rather than update it in place.

On a fork, or if the keystore secret is unavailable, the workflow still runs
`./gradlew assembleBeta` as a compile check and skips publishing. Local beta
builds without signing environment variables use the debug key. To sign one
locally, set `TICKETSPACE_BETA_KEYSTORE_FILE`,
`TICKETSPACE_BETA_KEYSTORE_PASSWORD`, `TICKETSPACE_BETA_KEY_ALIAS`, and
`TICKETSPACE_BETA_KEY_PASSWORD`, then run `./gradlew assembleBeta
--no-configuration-cache` from `apps/android`.

## Cloud Anchor Authorization

Cloud Anchors use ARCore's **keyless authorization**, not an API key: Google
Play services authenticates the app by its package name and signing-certificate
SHA-1. The beta therefore cannot use either the production package's OAuth
client or the production signing certificate.

In the existing `ticketspace-508dev` Google Cloud project, the maintainer must
create an **OAuth 2.0 Client ID with application type Android** using:

- Package name: `dev.co508.ticketspace.beta`
- SHA-1 certificate fingerprint:
  `29:0E:D5:BB:05:C3:FF:86:B6:6F:0F:1E:8A:C0:08:E9:D8:C0:41:64`

Keep the existing debug and release Android OAuth clients. No OAuth client ID
or API key is added to the APK or workflow. Until the beta Android OAuth client
is registered, Cloud Anchor operations in the signed beta can fail with
`ERROR_NOT_AUTHORIZED`; local anchors and the production PocketBase features
do not depend on that registration. A local beta signed with the debug key
needs its own package/debug-SHA-1 Android OAuth client for Cloud Anchors.
