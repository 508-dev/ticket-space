# Secrets

Ticket Space keeps secrets out of git. Nothing in tracked docs, migrations, or
workflows contains credentials.

Where the values live:

- `.env` (repo root, gitignored, copied into worktrees via `.worktreeinclude`):
  PocketBase superuser credentials and other backend environment values.
- `apps/android/local.properties` (gitignored): Android SDK path and the optional debug
  `ticketspace.baseUrl` override. Cloud Anchors use keyless auth, so the app
  holds no ARCore key.
- `apps/android/keystore.properties` (gitignored), or exported `TICKETSPACE_KEYSTORE_*`
  / `TICKETSPACE_KEY_*` values: local release signing material; see
  `docs/deployment.md` and `apps/android/keystore.properties.example`.
- GitHub Environment `Prod`: `RELEASE_KEYSTORE_BASE64`,
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`;
  optional `RELEASE_PLEASE_TOKEN` (falls back to `GITHUB_TOKEN`).
- Coolify environment variables: production values for the PocketBase service.

Rules:

- Read secrets from the environment; never hard-code them or put them in a
  migration.
- Never echo a secret in a workflow step or log.
- If a real secret is committed, rotate it. Removing it from git history is not
  enough.

No encrypted-file tooling (SOPS or similar) is in use. Add it only if the
project ever needs encrypted config checked into Git.
