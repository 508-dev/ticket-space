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
