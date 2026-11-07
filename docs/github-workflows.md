# GitHub Workflows

## Default Files

Root `.github/` files are meant to be safe defaults for most repositories:

- `.github/PULL_REQUEST_TEMPLATE.md`: prompts for summary, validation, risk, and screenshots.
- `.github/ISSUE_TEMPLATE/bug_report.yml`: captures reproducible defects.
- `.github/ISSUE_TEMPLATE/feature_request.yml`: captures product or workflow requests.
- `.github/ISSUE_TEMPLATE/docs_request.yml`: captures documentation gaps.
- `.github/ISSUE_TEMPLATE/config.yml`: keeps blank issues allowed and documents where to add discussion links.
- `.github/workflows/ci.yml`: runs `bun install --frozen-lockfile` and Biome lint on PRs and on `main`.

Keep these templates short. They should improve issue and PR quality without making lightweight collaboration feel bureaucratic.

Workflows pin third-party actions to commit SHAs and use `harden-runner` in audit mode.

Plain checkout-and-run validation jobs should usually keep `contents: read` only.
Do not add `pull-requests: read` unless the job explicitly calls PR APIs or
posts PR comments.

## Security Extras

Secret scanning (Gitleaks) and Dependency Review are not enabled here; the
`extras/` add-ons were removed during template selection. Prefer GitHub native
secret scanning and push protection where available.
