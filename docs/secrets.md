# Secrets

Ticket Space keeps secrets out of git. Nothing in tracked docs, migrations, or
workflows contains credentials.

Where the values live:

- `.env` (repo root, gitignored, copied into worktrees via `.worktreeinclude`):
  PocketBase superuser credentials and other backend environment values.
- `local.properties` (gitignored): Android SDK path and the optional debug
  `ticketspace.baseUrl` override. Cloud Anchors use keyless auth, so the app
  holds no ARCore key.
- `keystore.properties` (gitignored): release signing material; see
  `stacks/android/README.md`.
- Coolify environment variables: production values for the PocketBase service.

Rules:

- Read secrets from the environment; never hard-code them or put them in a
  migration.
- Never echo a secret in a workflow step or log.
- If a real secret is committed, rotate it. Removing it from git history is not
  enough.

No encrypted-file tooling (SOPS or similar) is in use. Add it only if the
project ever needs encrypted config checked into Git.
