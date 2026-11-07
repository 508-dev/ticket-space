# Contributing

Changes should be small and focused on the product.

## Principles

- Prefer small, composable changes.
- Preserve supply-chain cooldowns and committed lockfiles.
- Update agent-facing guidance (`AGENTS.md`) when conventions change.

## Local Checks

Run the narrowest relevant checks while iterating:

```bash
./scripts/lint.sh
./scripts/test.sh
```

Before opening or updating a PR, run:

```bash
./scripts/check-all.sh
```

## Pull Requests

Use the PR template. Include what changed, why, and how it was validated.

Avoid committing local state such as `node_modules`, caches, raw logs,
screenshots, and `.context/`.

## Agent Notes

- Keep convention changes paired with docs updates.
- Validate the local merge gate before marking work complete (see
  `docs/architecture.md` "Engineering rules").
