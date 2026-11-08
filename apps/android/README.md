# Ticket Space Android (P0 + P1)

Native Kotlin / Compose / Material 3 app; the Gradle root is **this directory**.
P0 covers photo todos, rooms, sign-in and live lists. P1 adds room registration,
Local/Cloud anchors, room-frame Things, live beacons and edge arrows. ARCore is
optional: unsupported devices still use all list/photo flows.

## Build and run

Requirements: a full JDK (21 or Android Studio's bundled JDK), Android SDK platform
37, platform-tools, and the SDK/build-tools requested by AGP. The app supports API
31+, targets 36 and compiles against 37, matching soundboard. AGP 9 provides Kotlin:
do **not** add `org.jetbrains.kotlin.android`.

Create gitignored `local.properties`:

```properties
sdk.dir=/home/caleb/Android/Sdk
# Optional debug-only override; adb reverse forwards this to the host.
# ticketspace.baseUrl=http://127.0.0.1:8090
# ARCORE_API_KEY=your-restricted-development-key
```

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew assembleDebug lint test ktlintCheck
```

The supplied workstation's `/usr/lib/jvm/java-21-openjdk` is runtime-only (no
`javac`): it fails with “No Java compiler found”. `/opt/android-studio/jbr`
is the installed full JDK 25.0.2 and is used for local verification. A full
Java 21 JDK is also valid; do not point Gradle at a JRE.

See [device testing](../../docs/device-testing.md) for install, screenshot and logs.

## Configuration and safety

- Default `BuildConfig.BASE_URL`: `https://ticketspace.508.dev`.
- `./gradlew assembleDebug -Pticketspace.baseUrl=https://example.org` sets the
  build default. Debug alone permits `local.properties` `ticketspace.baseUrl`
  to override it. Release ignores that local URL override.
- Only debug permits cleartext, only for `127.0.0.1` (not localhost or LAN IPs).
- `ARCORE_API_KEY` is read from `local.properties` into the
  `com.google.android.ar.API_KEY` manifest placeholder; absent means empty.
  Keys are packaged into APKs: restrict any future key to this app and signing
  certificate. Never commit keys, signing files, tokens or account passwords.
- `BuildConfig.ARCORE_AUTH` defaults to `apikey` (one-day Cloud Anchor TTL).
  `-Pticketspace.arcoreAuth=keyless` selects 365 days and omits the API key from
  the manifest; use only after Android OAuth setup (issue #13). Expiry is stored
  per anchor. Expired anchors require re-registration; renewal is a later phase.
- SceneView 4.64.0 was released 2026-10-31 15:55 UTC; ARCore 1.57.0 on
  2026-10-16. SceneView 4.65.0 (2026-11-01 16:28 UTC) was not seven full days old
  at this build's cutoff (2026-11-07 evening UTC / November 8 locally).
- Application ID / namespace: `dev.co508.ticketspace`; version `0.1.0` / `1000`.
  Release signing/publishing and version automation are intentionally deferred.
- Exact versions live in `gradle/libs.versions.toml`; shared Android pins are
  soundboard's 2026-08-26 versions, OkHttp 4.12.0 is from 2023-10-17 and Coil 3.3.0
  from 2025-07-22. These predate the 2026-11-01 cooldown cutoff. The wrapper is
  checksum-verified. Dependency lockfiles are tracked; deliberate dependency
  updates require regenerating them with the relevant tasks and `--write-locks`.

## Code map

- `AppContainer.kt`: manual dependency injection; foreground session lifecycle.
- `data/`: contract models, paginated REST/multipart API, private DataStore
  session, SSE reconnect/resubscription, snapshot + event StateFlow repository,
  orientation-aware image decode/downscale (long edge 1600, JPEG 85).
- `ui/`: one ViewModel per screen, Material 3 theme, navigation, forms and camera.
- `ar/`: retained SceneView session, permission/install/disclosure gate, registration,
  camera capture, placement, beacons and sheets; `math/` is platform-free rigid
  transform/projection math; `providers/` contains Local/Cloud backends and policy.
- `app/src/test/`: JSON mapping, filter escaping/JSON tags, sorting, event merges,
  HTTP requests/errors/uploads, SSE subscription/reconnect and image sizing,
  cross-session room-frame recovery, carry-over, projection and provider timeout/concurrency.

PocketBase JSON-array tags do not support the select/relation `:each` modifier.
The query client follows the [PocketBase maintainer's JSON-array guidance](https://github.com/pocketbase/pocketbase/discussions/2332#discussioncomment-9238250):
`tags ~ '"repair"'`, followed by exact client-side membership (LIKE wildcard
characters must not cause false positives). Home filters the live in-memory
snapshot with the same exact predicate, keeping tag suggestions and room counts
independent of the active filters. All lists use priority descending, created
ascending for todos. Every page is fetched; no silent 200-record truncation.

Realtime subscribes to `todos/*`, `rooms/*`, `things/*`, `room_anchors/*` on
`PB_CONNECT`, using the auth token on the POST. Failures reconnect with bounded
1–30 second backoff and resubscribe, followed by a full snapshot to close gaps.
Newer snapshot records are not overwritten by older queued events. SSE stops in
the background/sign-out, restarts in the foreground; auth refresh runs on startup
and every 15 minutes while active. Network and live-update failures are visible.

No persistent record cache or offline mode. A failed initial load shows an error;
a dropped live connection marks displayed in-memory records as potentially stale.
Session storage is app-private; Android cloud backup and device transfer exclude
app data. Draft fields/photos survive activity rotation via ViewModels, but drafts
are not persisted across process death. Camera originals are temporary and uploads
are downscaled; abandoned temporary files may remain until Android clears cache.

## Remaining integration verification

Do not sign in without the backend/account bootstrap. Once it is available,
exercise two-device CRUD/live updates, photo capture/orientation, filters, session
expiry, delete events and network loss on both target phones. MockWebServer tests
are not a substitute for that integration pass. The Xperia/Android 16 device and
P1 spatial tracking and Cloud authorization need the physical-device AR script.

The retained AR surface pauses its lifecycle/camera when returning to lists, so
Local anchors remain usable during the same activity/app session. Sign-out,
activity recreation or process death destroys that session. Cloud resolution uses
the provider on each saved anchor, independent of the default in Settings. The
first resolved room wins; a manual room choice pins selection without pretending
the room is located. The nearest tracking anchor in the current room supplies its frame.

Registration uses three anchors at least 75 cm apart and captures a camera reference
photo (not a stitched 360° panorama). Re-registration transforms current Things when
an old anchor resolves; otherwise it explicitly stores null poses. PocketBase record
writes are not transactional: confirmed writes are compensated on failure, but an
ambiguous network failure or concurrent edit can require re-registration. Do not
re-register the same room concurrently from two devices.
