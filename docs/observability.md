# Observability

Ticket Space has no telemetry vendor. The app is pre-MVP and the backend is a
single self-hosted PocketBase container.

- Backend: PocketBase logs to the container's stdout; read them in Coolify's log
  view.
- Android: use `adb logcat` during development (adb lives at
  `$ANDROID_HOME/platform-tools/adb`).
- Defects are reported through GitHub issues, not a monitoring service.

Do not add Sentry or OpenTelemetry just because it seems prudent. Add one only
after the project has a real destination, release/environment naming, and a
decision recorded in `DECISIONS.md`. Keep initialization in one helper per
runtime so processes do not configure it differently by accident.
