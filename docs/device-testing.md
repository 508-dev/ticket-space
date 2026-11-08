# Android device testing

Build from `apps/android` with a full JDK and the local SDK:

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew assembleDebug lint test ktlintCheck
```

The workstation's `/usr/lib/jvm/java-21-openjdk` has no Java compiler; use the full
Android Studio JDK above (or install/configure a full Java 21 JDK separately).

## ASUS Zenfone 9 / Android 14

Enable USB debugging and approve this workstation. Device serial:
`NAAIB700H902PD8`. Commands below run from `apps/android`:

```bash
ADB="$ANDROID_HOME/platform-tools/adb"
"$ADB" -s NAAIB700H902PD8 get-state
"$ADB" -s NAAIB700H902PD8 install -r app/build/outputs/apk/debug/*.apk
"$ADB" -s NAAIB700H902PD8 shell am start -W -n dev.co508.ticketspace/.MainActivity
"$ADB" -s NAAIB700H902PD8 exec-out screencap -p > /tmp/ts-signin.png
"$ADB" -s NAAIB700H902PD8 logcat -d -b crash -v threadtime
"$ADB" -s NAAIB700H902PD8 logcat -d -v threadtime AndroidRuntime:E ActivityManager:I '*:S'
```

Inspect the screenshot: “Ticket Space”, email/password fields, Sign in button and
administrator-created-account hint should render without clipping. No backend is
needed for this smoke test; **do not try to sign in** before it exists. Compare
logcat timestamps/package names to distinguish unrelated historical crashes.
For focused live logs after launch:

```bash
PID=$("$ADB" -s NAAIB700H902PD8 shell pidof dev.co508.ticketspace)
"$ADB" -s NAAIB700H902PD8 logcat --pid="$PID" -v threadtime
```

The USB port is flaky. If adb drops, reconnect/retry once; record the failure and
continue host checks rather than repeatedly resetting the device. If streamed
installation reports a corrupt APK despite a valid host ZIP, retry once with
`install --no-streaming -r` and record which transport succeeded.

## Local backend

Set the **debug-only** override in gitignored `apps/android/local.properties`:

```properties
ticketspace.baseUrl=http://127.0.0.1:8090
```

Forward the device port to the host, rebuild and reinstall:

```bash
"$ADB" -s NAAIB700H902PD8 reverse tcp:8090 tcp:8090
./gradlew assembleDebug
"$ADB" -s NAAIB700H902PD8 install -r app/build/outputs/apk/debug/*.apk
```

Do not use a LAN IP or `localhost`: debug cleartext is restricted to `127.0.0.1`;
release prohibits cleartext altogether. Release uses the Gradle property/default,
not this local URL override. For production remove the override and rebuild.

## Backend-ready acceptance pass

Use administrator-created test accounts, never hardcoded credentials. Verify:

- Sign in, restart/refresh session, settings user and sign out.
- Empty/error/loading states; flight mode produces a clear connection error.
- Create a room with/without its reference photo; room counts update live.
- Create/edit todos, all four priorities, free-form/autocomplete tags, 0–3 camera
  photos; verify portrait/landscape orientation and 1600 px / JPEG 85 uploads.
- Sort urgent first/oldest first; room/tag filters and show-done toggle.
- Detail photos, remove/add photos while editing, mark done/reopen. Completion
  user/time must be assigned/cleared by the server, not by the Android client.
- With a second device/account, CRUD/delete events and room counts update live;
  disconnect/reconnect and background/foreground reload and resubscribe.
- Deny camera permission: rooms/lists still work. Allow it and verify P0 camera
  capture still works after visiting AR (the AR camera must be released).

Repeat on Sony Xperia 10 VII / Android 16 when available.

## AR test script

Use an unlocked device, good indoor lighting and textured walls/furniture. Test
against the deployed backend using administrator-created accounts. Never bypass a
secure lock for unattended testing. AR tracking, Cloud authorization and visual
alignment cannot be validated while the phone is locked or fixed in place.

### Local room (ASUS Zenfone 9)

1. In Settings choose **Local (session-only, dev)**. Open Rooms → **Register room
   in AR**, name a test room and confirm that Local is preselected.
2. Allow CAMERA. If prompted, install/update Google Play Services for AR. On an
   unsupported device, verify the explanation and Back to working P0 lists.
3. Slowly scan walls/furniture. Tap a surface for **Anchor 1 of 3** and host it.
   Walk at least 75 cm to each of two more spots, tap and host them. Verify the
   progress labels, surface dots and guidance. Save the room and camera photo.
4. Verify the room reference photo is upright, downscaled to at most 1600 px,
   and that the room is located. **+ Thing** → centre reticle → confirm places a
   Thing and opens New todo with room/Thing and an AR camera photo prefilled.
   Create two Things with different-priority todos. Add another todo to one Thing.
5. There must be one beacon per Thing, using its highest *open* priority: urgent
   red/pulsing, high amber, normal teal, low grey. Turn around and tilt the camera:
   edge arrows must stay visible, including for Things behind the camera. Check
   that a right-behind Thing points right, not left; return it to view.
6. Tap a beacon: inspect open todos, **Mark done**, **Add todo here**. Complete the
   highest priority and verify the colour drops immediately. Complete every todo:
   beacon disappears. Toggle **Show things** and verify a dim marker can receive
   a new todo. Long-press a beacon → **Move** → reticle → confirm; it moves without
   losing its todos. Cancel a move and verify it stays in its original location.
7. Return to Room detail → **Open AR**, and Todo detail → **Show in AR**. Local
   anchors should remain valid in the retained session; the selected Thing has a
   halo/edge-arrow highlight, even if all its todos are done. Check top-level AR.
   Camera use must stop on P0 screens and in the background.
8. **Re-register** in the same session using a different first anchor. Wait for
   “Old anchor located”; save and verify Things do not move physically. Restart
   the app: Local anchors cannot resolve. After 15 s, tips/manual room picker must
   appear and room-level todos remain usable. Re-register without an old anchor;
   saved Things must appear in **Unplaced** with their todos preserved. Tap one and
   place it again. Activity recreation also ends a Local session.

### Cloud room and restart

1. Set default provider to **Google Cloud** and register a distinct room. On first
   Cloud use verify the exact sensor-data disclosure and its Google privacy link.
   Local-only use must not transmit Cloud Anchor requests.
2. Tap a textured spot, then **walk around the spot, keep it in view**. Watch the
   live quality meter. Host must remain disabled until quality is **good**. Repeat
   for all three anchors; inspect logs for host success and elapsed duration.
3. Place two Things/todos, note their physical spots, then kill the app:
   `adb -s NAAIB700H902PD8 shell am force-stop dev.co508.ticketspace`.
   Reopen, open top-level **AR**, and scan the room. It should auto-detect the room
   and show both beacons at the original spots. Approach different anchors: the
   closest tracked anchor should supply alignment without a large jump.
4. Test a different room and the manual picker. Manual selection alone must say
   “not located” and disable spatial placement, while permitting room-level todos.
   Try low light, an untextured wall and network loss; check guidance/errors and
   Retry. Cloud operations time out after 30 s once admitted, with at most 40 active.
5. Current API-key builds expire Cloud anchors after **one day**. Complete restart
   and second-phone checks before expiry. `keyless` / 365-day TTL requires issue #13
   OAuth setup; changing the build flag alone does not configure authorization.

### Sony Xperia 10 VII (Android 16)

Install the same build signed with the authorized certificate. Sign in to the same
site, accept Cloud disclosure/permission, open AR in the Cloud room and scan the
three anchor areas. Verify automatic room detection and the same physical Thing
positions. On the ASUS, add/edit/complete a todo, move a Thing, then unplace/re-place
one by re-registering. Verify the Xperia updates live. Also test the reverse direction.
Check Depth-supported hits on both phones and plane fallback when depth is unavailable.

### Logs and failure notes

```bash
ADB="$ANDROID_HOME/platform-tools/adb"
"$ADB" -s NAAIB700H902PD8 logcat -v threadtime -s TicketSpaceAR
# Save a run; Ctrl-C when finished:
"$ADB" -s NAAIB700H902PD8 logcat -v threadtime -s TicketSpaceAR > /tmp/ts-ar.log
"$ADB" -s NAAIB700H902PD8 logcat -d -b crash -v threadtime
```

Look for provider, host/resolve start/result/duration, feature quality and tracking
state changes. API keys must never appear in logs. Record room/provider, device,
lighting, approximate displacement, action and timestamp for alignment failures.
Distinguish `ERROR_NOT_AUTHORIZED` (key restriction/API configuration) from poor
feature quality, expired IDs, network failure and tracking loss.

Room registration spans ordinary PocketBase record requests. It compensates
confirmed writes if a later request fails, but cannot guarantee atomicity after an
ambiguous network failure or concurrent writes. Avoid simultaneous re-registration;
after a partial-save warning, reload and re-register before placing more Things.
The contract has no registration generation/transaction mechanism; this remains a
backend follow-up rather than a silent schema change.

### P1 implementation verification (2026-11-08)

- `./gradlew assembleDebug lint test ktlintCheck`: **BUILD SUCCESSFUL in 39s**,
  with dependency locks enforced. 36 JVM tests passed, including 19 new spatial,
  projection, provider-policy/concurrency, beacon and spatial-API tests.
- ASUS `NAAIB700H902PD8`: streamed `adb install -r` succeeded on the first attempt.
  Force-stop followed by `am start -W` returned `Status: ok`, `WaitTime: 87` ms.
  The app process remained alive; PID-filtered AndroidRuntime and crash-buffer
  queries returned no output. Historical crashes from unrelated system apps were
  excluded from the result.
- Keyguard reported `showing=true`, `inputRestricted=true`, `secure=true`. No
  unlock was attempted. AR UI screenshots, tracking, quality/hosting, physical
  alignment, Depth hits, camera-photo orientation and second-phone Cloud resolution
  remain untested on hardware; run the script above when the owner is available.
