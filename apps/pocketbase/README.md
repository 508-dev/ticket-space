# PocketBase backend

Ticket Space's backend: a single pinned [PocketBase](https://pocketbase.io)
release, deployed as one Docker image. The schema, API rules and the done/open
hook live in versioned JS migrations and hooks in this directory — never edited
by hand in the dashboard.

- **Version:** PocketBase `v0.40.6` (pinned in `Dockerfile` and here)
- **Layout:**
  - `Dockerfile` — downloads and verifies the pinned linux/amd64 release.
  - `pb_migrations/` — collections, fields, rules, indexes, seed, settings.
  - `pb_hooks/` — the todo `done`/`open` lifecycle hook.
  - `scripts/` — `bootstrap.sh` (user seeding) and `smoke.sh` (e2e test).

The data model and rules are the source of truth in
[`docs/architecture.md`](../../docs/architecture.md) → "Backend: PocketBase".

## Run locally

```bash
cd apps/pocketbase

# Build the image (context is this directory)
docker build --platform linux/amd64 -t ticketspace-pocketbase .

# Run on a free port with a throwaway data dir
docker run --rm -it \
  -p 127.0.0.1:8090:8090 \
  -v "$PWD/.pb-data:/pb/pb_data" \
  ticketspace-pocketbase
```

Migrations apply automatically on first `serve` against a fresh data dir, and
hooks are loaded from `pb_hooks/`. The health endpoint is `/api/health`.

Create the superuser from inside the container:

```bash
docker exec -it <container> /pb/pocketbase superuser upsert you@example.com 'a-strong-password'
```

Then seed the app users:

```bash
PB_URL=http://127.0.0.1:8090 \
PB_SUPERUSER_EMAIL=you@example.com \
PB_SUPERUSER_PASSWORD='a-strong-password' \
TICKETSPACE_USERS='alice@example.com:alice-password:Alice;bob@example.com:bob-password:Bob' \
  ./scripts/bootstrap.sh
```

## Adding a migration

Never edit the schema in the dashboard. Add a JS migration instead:

```bash
docker exec -it <container> /pb/pocketbase migrate create "describe_the_change"
```

Edit the generated file under `pb_migrations/`, then restart `serve` (or run
`/pb/pocketbase migrate up`) so it is applied and the collections cache is
refreshed. Every migration should have a matching `down` callback that reverses
the `up` changes. Use the current `fields`-based Collection API and
`app.save(collection)` — not the pre-0.23 `schema`/`Dao` API.

## Bootstrap

`scripts/bootstrap.sh` is idempotent. It authenticates as the superuser via the
REST API and creates or updates the accounts in `TICKETSPACE_USERS`
(`email:password:name`, `;`-separated). It never echoes passwords. The
superuser itself is created separately with `pocketbase superuser upsert EMAIL
PASSWORD` inside the container (see above).

## Smoke test

```bash
apps/pocketbase/scripts/smoke.sh
```

Builds the image, starts it on a random free host port (never 8090) with a
throwaway data dir, creates the superuser and two test users, then asserts:

- all collections exist;
- unauthenticated todo listing is empty or rejected;
- public user signup is rejected;
- a user can create a room, a thing and a todo with a multipart JPEG upload;
- a todo with someone else's `created_by` is rejected;
- `sort=-priority,created` orders correctly;
- the hook sets `done_by`/`done_at` on open→done, clears them on done→open, and
  ignores a client-supplied `done_by`;
- `/api/realtime` emits a `PB_CONNECT` event.

The container and data dir are always removed on exit, and any failure exits
non-zero.
