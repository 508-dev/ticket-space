# Security Policy

## Reporting Vulnerabilities

Do not open public issues for vulnerabilities or leaked secrets.

Report security concerns to the project maintainers through the private channel
configured for the repository.

## Secret Handling

- Keep secrets in environment variables or gitignored files (`.env`,
  `local.properties`, `keystore.properties`).
- Never commit real `.env` files, tokens, private keys, credentials, or
  production data.
- Use `.env.example` for documented configuration only.

## Dependency Policy

This project uses dependency cooldowns and locked installs:

- Bun: `minimumReleaseAge = 604800` (see `bunfig.toml`).
- Renovate: `minimumReleaseAge = "7 days"`.
- CI should use frozen or locked installs (`bun install --frozen-lockfile`).

## GitHub Actions

Workflows should use least-privilege permissions, pinned action SHAs,
`persist-credentials: false` where practical, and `harden-runner` in audit or
block mode.

## Agent Notes

- Prefer GitHub native secret scanning and push protection where available.
- Do not commit plaintext secrets.
