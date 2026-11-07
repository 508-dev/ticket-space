# Dependency Supply-Chain Policy

## Bun

`bunfig.toml`:

```toml
[install]
minimumReleaseAge = 604800
linker = "isolated"
```

`minimumReleaseAge` is in seconds.

## Gradle (Android)

Use this when a project selects the Android stack (`stacks/android`).

Gradle has no package-manager-level cooldown flag comparable to Bun. Renovate is
the whole mechanism: it natively understands Gradle version catalogs and opens
PRs against `gradle/libs.versions.toml` on the repo's normal
`minimumReleaseAge`, no extra Renovate config required. Don't hand-edit
dependency versions in that file opportunistically outside of a Renovate PR
unless fixing something broken.

Verify Gradle wrapper integrity via `distributionSha256Sum` in
`gradle/wrapper/gradle-wrapper.properties`, and update both together when
bumping the wrapper version. CI should build against the committed wrapper
only — never a floating Gradle version.

## CI

Use frozen or locked installs:

```bash
bun install --frozen-lockfile
```

Renovate should use `minimumReleaseAge = "7 days"` so dependency PRs do not fight
package-manager cooldowns.

Cooldowns, locked installs, committed lockfiles, and least-privilege CI remain
the default supply-chain defenses.
