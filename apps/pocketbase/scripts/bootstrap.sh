#!/usr/bin/env bash
#
# Idempotent bootstrap for Ticket Space's PocketBase `users` collection.
#
# Creates or updates the accounts listed in TICKETSPACE_USERS by authenticating
# as the superuser through the PocketBase REST API. Safe to run repeatedly:
# existing accounts are updated (name + password), missing ones are created.
#
# The superuser itself is NOT created by this script. Create it first from
# inside the running container:
#
#     docker exec <container> /pb/pocketbase superuser upsert EMAIL PASSWORD
#
# Environment:
#   PB_URL                base URL, e.g. https://ticketspace.508.dev
#   PB_SUPERUSER_EMAIL    superuser email
#   PB_SUPERUSER_PASSWORD superuser password
#   TICKETSPACE_USERS     "email:password:name;email:password:name;..."
#
# Passwords are never written to stdout or logs.

set -euo pipefail

: "${PB_URL:?PB_URL is required}"
: "${PB_SUPERUSER_EMAIL:?PB_SUPERUSER_EMAIL is required}"
: "${PB_SUPERUSER_PASSWORD:?PB_SUPERUSER_PASSWORD is required}"
: "${TICKETSPACE_USERS:?TICKETSPACE_USERS is required}"

require() {
  command -v "$1" >/dev/null 2>&1 || { echo "error: '$1' is required" >&2; exit 1; }
}
require curl
require jq

fail() {
  echo "error: $*" >&2
  exit 1
}

# ---------------------------------------------------------------------------
# Authenticate as superuser. The response JSON carries the auth token.
# ---------------------------------------------------------------------------
auth_payload=$(jq -n \
  --arg identity "$PB_SUPERUSER_EMAIL" \
  --arg password "$PB_SUPERUSER_PASSWORD" \
  '{ identity: $identity, password: $password }')

auth_response=$(curl -sS -X POST "$PB_URL/api/collections/_superusers/auth-with-password" \
  -H 'Content-Type: application/json' \
  --data "$auth_payload")

token=$(jq -r '.token // empty' <<<"$auth_response")
if [ -z "$token" ]; then
  fail "superuser authentication failed: $(jq -r '.message // "unknown error"' <<<"$auth_response")"
fi

# ---------------------------------------------------------------------------
# Create or update each user.
# ---------------------------------------------------------------------------
IFS=';' read -r -a entries <<<"$TICKETSPACE_USERS"

for entry in "${entries[@]}"; do
  [ -z "$entry" ] && continue

  IFS=':' read -r email password name <<<"$entry"
  [ -z "$email" ] && { echo "warning: skipping empty entry" >&2; continue; }
  [ -z "$password" ] && { echo "warning: skipping $email (missing password)" >&2; continue; }
  name="${name:-$email}"

  # Look up an existing account by email.
  lookup=$(curl -sS -G "$PB_URL/api/collections/users/records" \
    -H "Authorization: $token" \
    --data-urlencode "filter=(email='$email')" \
    --data-urlencode "perPage=1")

  existing_id=$(jq -r '.items[0].id // empty' <<<"$lookup")

  if [ -n "$existing_id" ]; then
    payload=$(jq -n \
      --arg password "$password" \
      --arg name "$name" \
      '{ password: $password, passwordConfirm: $password, name: $name }')

    response=$(curl -sS -X PATCH "$PB_URL/api/collections/users/records/$existing_id" \
      -H "Authorization: $token" \
      -H 'Content-Type: application/json' \
      --data "$payload")

    if ! jq -e '.id' >/dev/null 2>&1 <<<"$response"; then
      fail "failed to update user $email: $(jq -r '.message // "unknown error"' <<<"$response")"
    fi
    echo "updated user: $email"
  else
    payload=$(jq -n \
      --arg email "$email" \
      --arg password "$password" \
      --arg name "$name" \
      '{ email: $email, password: $password, passwordConfirm: $password, name: $name }')

    response=$(curl -sS -X POST "$PB_URL/api/collections/users/records" \
      -H "Authorization: $token" \
      -H 'Content-Type: application/json' \
      --data "$payload")

    if ! jq -e '.id' >/dev/null 2>&1 <<<"$response"; then
      fail "failed to create user $email: $(jq -r '.message // "unknown error"' <<<"$response")"
    fi
    echo "created user: $email"
  fi
done

echo "bootstrap complete"
