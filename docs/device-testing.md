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
- Open AR / Show in AR route to “AR coming soon”, without AR permissions.

Repeat on Sony Xperia 10 VII / Android 16 when available. P1 AR registration,
localization, anchors and depth testing are intentionally deferred.
