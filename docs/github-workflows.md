# GitHub Workflows

## Default Files

Root `.github/` files are meant to be safe defaults for most repositories:

- `.github/PULL_REQUEST_TEMPLATE.md`: prompts for summary, validation, risk, and screenshots.
- `.github/ISSUE_TEMPLATE/bug_report.yml`: captures reproducible defects.
- `.github/ISSUE_TEMPLATE/feature_request.yml`: captures product or workflow requests.
- `.github/ISSUE_TEMPLATE/docs_request.yml`: captures documentation gaps.
- `.github/ISSUE_TEMPLATE/config.yml`: keeps blank issues allowed and documents where to add discussion links.
- `.github/workflows/ci.yml`: Biome and version-sync tests, Android build/lint/unit tests/ktlint with a debug APK artifact, and the PocketBase Docker smoke test. Component jobs use path filters on PRs; main/manual runs check both. The always-running `ci-passed` aggregate fails on detection failures and unexpected skips.
- `.github/workflows/pr-title.yml`: Conventional Commit PR-title check (`conventional-commit`).
- `.github/workflows/release.yml`: release-please release PRs and signed GitHub APK publishing on `main`; secrets in Environment `Prod`. See `docs/deployment.md`, including the manual check requirement when using the `GITHUB_TOKEN` fallback.
- `.github/workflows/beta.yml`: separately signed Ticket Space Beta pre-releases for non-main branches, with older-beta pruning and branch-deletion cleanup. It uses repository-level beta secrets, never `Prod`; see `docs/beta-builds.md`.

Keep these templates short. They should improve issue and PR quality without making lightweight collaboration feel bureaucratic.

Workflows pin third-party actions to commit SHAs and use `harden-runner` in audit mode.

Plain checkout-and-run validation jobs should usually keep `contents: read` only.
Only path detection needs `pull-requests: read` to list changed PR files.
The release-please job needs PR/content write access, and release/beta
publishing needs content write access to manage releases and attach assets.
Require `ci-passed` and `conventional-commit` in branch protection; do not
require path-filtered component checks separately.

## Security Extras

Secret scanning (Gitleaks) and Dependency Review are not enabled here; the
devkit security add-ons were removed during template selection. Prefer GitHub
native secret scanning and push protection where available.
