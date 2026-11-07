# Ticket Space architecture

Ticket Space is an Android AR todo app inspired by the community-space lighting
in Cory Doctorow's *Walkaway*: problems in a physical space are filed as todos,
pinned to the spot where they are, and shown to anyone who opens the AR view in
that room.

This file is the contract for contributors and agents. Decisions were settled
in a design review on 2026-11-07/08; change them here first, then in code.

## Scope

- **MVP / v0.1.0 = P0 + P1.**
  - **P0 — photo tickets:** rooms, todos with photos, a master list. No AR.
  - **P1 — pins in space:** register a room in AR, tap to place a Thing, attach
    todos, and see priority-coloured beacons for open todos in AR.
- Later phases are GitHub issues: P2 tap-to-segment and object naming, P3 live
  glow outlines, a self-hosted FOSS localization provider (OpenVPS on a GPU
  server), anchor TTL renewal, off-site backups, recurring todos, CI/release.
- Explicitly out of scope: AI problem spotting, assignees, due dates,
  recurrence, sub-tasks, blockers, comments, push notifications, offline mode,
  iOS.
- Users: a small trusted group (2 for the POC), one site, a few rooms.
- Test devices: ASUS Zenfone 9 (Android 14, ARCore + Depth) and Sony Xperia 10
  VII (Android 16, ARCore + Depth).

## Repository layout

```
apps/android/      Kotlin/Compose app (Gradle root lives here)
apps/pocketbase/   Dockerfile (pinned PocketBase), pb_migrations/, pb_hooks/, scripts
docs/              architecture (this file), development, deployment, device testing
scripts/           repo-level entrypoints (lint, test, check-all, smoke tests)
```

## Backend: PocketBase

- Pinned PocketBase release, built from `apps/pocketbase/Dockerfile`, deployed
  on Coolify at `https://ticketspace.508.dev` from `main`. Persistent volume for
  `/pb/pb_data`. Built-in daily backups to the volume, keep 7.
- Schema and API rules live in versioned JS migrations in
  `apps/pocketbase/pb_migrations/`. Never edit the schema by hand in the
  dashboard; write a migration.
- Accounts: email + password. Public signup is disabled (`users.createRule =
  null`); the superuser creates accounts. Every authenticated user may read and
  write every record — no roles.
- Secrets (superuser credentials, user passwords) never go in git or
  migrations. A bootstrap script reads them from the environment.

### Collections

All non-auth collections: list/view/create/update/delete rule
`@request.auth.id != ""` unless noted. All have `created`/`updated` autodate
fields.

| Collection | Field | Type | Notes |
|---|---|---|---|
| `users` (auth) | `name` | text | list/view: authenticated; update: self; create: superuser only |
| `sites` | `name` | text, required | create/update/delete: superuser only. Seed one record, "Home". |
| `rooms` | `site` | relation → sites, required | |
| | `name` | text, required, max 80 | |
| | `panorama` | file, single, image/*, ≤10 MB, thumb `480x240` | optional reference photo |
| `room_anchors` | `room` | relation → rooms, required, cascade delete | |
| | `provider` | select `local` \| `cloud`, required | which `AnchorProvider` created it |
| | `anchor_id` | text, required | Cloud Anchor ID, or a local session UUID |
| | `pose_in_room` | json, required | `{"t":[x,y,z],"q":[x,y,z,w]}` — anchor pose in the room frame |
| | `expires_at` | date, optional | Cloud Anchor expiry |
| `things` | `room` | relation → rooms, required, cascade delete | |
| | `label` | text, optional, max 80 | e.g. "dining table" |
| | `pose_in_room` | json, optional | same shape; **null = unplaced** |
| `todos` | `title` | text, required, max 200 | |
| | `description` | text, optional, max 5000 | |
| | `priority` | number, integer 0–3, required | 0 low, 1 normal, 2 high, 3 urgent (numeric so it sorts) |
| | `tags` | json | array of free-form strings |
| | `status` | select `open` \| `done`, required | |
| | `photos` | file, multiple, max 3, image/*, ≤10 MB each, thumb `320x320` | |
| | `room` | relation → rooms, required | |
| | `thing` | relation → things, optional | null = room-level todo |
| | `created_by` | relation → users, required | create rule also requires `@request.body.created_by = @request.auth.id` |
| | `done_by` | relation → users, optional | set by hook |
| | `done_at` | date, optional | set by hook |

Indexes: `todos(status, priority)`, `todos(room)`, `things(room)`,
`room_anchors(room)`.

Hook (`pb_hooks/`): when a todo's `status` changes to `done`, set `done_by` to
the requesting user and `done_at` to now; when it changes back to `open`, clear
both.

Realtime: clients subscribe over SSE (`/api/realtime`) to `todos`, `things`,
`rooms` and `room_anchors` and update their state from create/update/delete
events.

## Android app

- Kotlin + Jetpack Compose + Material 3, single `app` module, package
  `dev.co508.ticketspace`. Follow the Gradle conventions of the sibling 508 apps
  (`~/src/soundboard`, `~/src/emotion-tracker`): version catalog, AGP built-in
  Kotlin, ktlint, no DI framework (a manual `AppContainer`), DataStore for
  settings and the auth token.
- `minSdk 31`, `targetSdk 36`, `compileSdk` as in soundboard.
- Networking: OkHttp + kotlinx.serialization, with `okhttp-sse` for PocketBase
  realtime. Image loading: Coil. P0 photos: `ActivityResultContracts.TakePicture`
  or CameraX; photos are downscaled to about 1600 px on the long edge, JPEG
  quality 85, before upload.
- Backend base URL comes from a Gradle property (`ticketspace.baseUrl`, default
  `https://ticketspace.508.dev`). Debug builds may override it from
  `local.properties`, e.g. `http://127.0.0.1:8090` with `adb reverse tcp:8090
  tcp:8090`. Cleartext is allowed only for `127.0.0.1` and only in debug.
- Package layout: `data/` (API client, models, repositories, realtime),
  `ui/` (screens, navigation, theme), `ar/` (AR view, anchor providers,
  room-frame math), `AppContainer.kt`.
- No offline cache: the app needs a live connection and shows a clear error
  state without one.

### Screens

1. **Sign in** — email + password against the `users` collection.
2. **Master list (home)** — open todos sorted by priority (urgent first), then
   oldest first. Filter chips for room and tag; a toggle shows done todos.
3. **Rooms** — list with panorama thumbnail and open-todo count. Room detail
   shows that room's todos and an **Open AR** button.
4. **New todo** — from the list (pick a room, take up to 3 photos, no Thing) or
   from AR (tap a spot; a photo is grabbed from the AR camera frame). Fields:
   title, description, priority, tags (free-form with autocomplete from tags in
   use), photos.
5. **Todo detail** — edit fields, view photos, mark done or reopen, **Show in
   AR** when it has a Thing.
6. **AR view** (P1) — "Locating room…" state, beacons, edge arrows, tap a beacon
   to open its card and mark it done; place a new Thing; move a Thing; a "show
   things" toggle reveals Things with no open todos.
7. **Register room** (P1) — enter a name, choose an anchor provider, then a
   guided scan.
8. **Settings** — signed-in user, sign out, default anchor provider, backend URL
   (debug only).

Changes from other users appear live while the app is open.

## AR and spatial model (P1)

Rendering uses SceneView (`io.github.sceneview:arsceneview`, pinned) on top of
ARCore. Placement uses ARCore hit tests, with Depth when the device supports it.

### Anchor providers

```kotlin
interface AnchorProvider {
    val id: String // "local" or "cloud"; stored in room_anchors.provider
    /** Host [anchor]; returns the provider's anchor ID and optional expiry. */
    suspend fun host(anchor: Anchor, ttlDays: Int): HostedAnchor
    /** Resolve a previously hosted anchor; null on failure or timeout. */
    suspend fun resolve(anchorId: String): Anchor?
}
```

- **`local`** — session-only. `host` stores the live ARCore `Anchor` under a new
  UUID in memory; `resolve` succeeds only within the same app session. For
  development and side-by-side comparison with `cloud`.
- **`cloud`** — ARCore Cloud Anchors (`hostCloudAnchorAsync` /
  `resolveCloudAnchorAsync`). Long-term auth is keyless (Android OAuth client,
  TTL up to 365 days). Until that OAuth client exists, an API key restricted to
  this package and signing certificate is used, which caps TTL at 1 day. The
  key comes from `local.properties` (`ARCORE_API_KEY`) through a manifest
  placeholder and is never committed. Record `expires_at`. Check
  `estimateFeatureMapQualityForHosting` and guide the user to move until the
  quality is sufficient.
- The provider is chosen per room at registration; resolution always uses the
  provider recorded on each `room_anchors` row.

### Room frame

Room registration hosts about three anchors spread around the room in one
session. The first anchor `A0` defines the room frame (identity pose). Each
other anchor stores `pose_in_room = inverse(pose(A0)) * pose(Ai)`.

On a later visit, when any anchor `Ai` resolves with world pose `W_i`:

```
T_world_room = W_i * inverse(pose_in_room_i)
world(thing) = T_world_room * thing.pose_in_room
thing.pose_in_room (placing) = inverse(T_world_room) * hitPose
```

When several anchors resolve, prefer the anchor closest to the camera.

### Room detection and failure handling

- Opening the AR view resolves all `room_anchors` of the site at once (keep
  within ARCore's 40 concurrent anchor operations); the first room to resolve
  becomes the current room. A manual room picker is always available.
- If nothing resolves within about 15 seconds: show tips ("move slowly, point at
  walls and furniture"), offer the manual picker, and keep the list usable.
- **Re-register room:** new scan, new anchors. If an old anchor resolves during
  the new session, transform every Thing into the new frame. Otherwise set the
  room's Things to unplaced (`pose_in_room = null`); they appear in an
  "unplaced" list to be tapped back into position, and their todos stay
  room-level until then.
- **Move Thing:** select a Thing and tap its new spot.
- Things are place-bound: if furniture moves, its Thing stays put until moved.

### Beacons

- One glowing beacon per Thing with open todos, coloured by the highest open
  priority: urgent red and pulsing, high amber, normal teal, low grey.
- Edge-of-screen arrows point to beacons outside the view.
- Tap a beacon to open a card listing the Thing's open todos, each with a "mark
  done" action.

## Engineering rules

- Dependency cooldown: only adopt releases at least 7 days old (see
  `docs/supply-chain.md`). Pin exact versions; commit lockfiles and the Gradle
  wrapper.
- Conventional-commit PR titles (`feat:`, `fix:`, `docs:`, `chore:` and so on).
- Until CI exists, the merge gate is local:
  - Android: `./gradlew assembleDebug lint test` (from `apps/android`, with
    `JAVA_HOME=/opt/android-studio/jbr` and `ANDROID_HOME=$HOME/Android/Sdk`;
    the system JDK 21 package at `/usr/lib/jvm/java-21-openjdk` is a runtime
    only, with no `javac`), plus an install-and-launch smoke test on a connected
    device for app changes.
  - Backend: `apps/pocketbase/scripts/smoke.sh` (migrations apply on a fresh data
    dir; CRUD, rules and the hook behave).
- Secrets live in environment variables or gitignored files (`.env`,
  `local.properties`, `keystore.properties`). Never commit them.
