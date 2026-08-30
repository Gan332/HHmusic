# Opencode configuration workspace

This repository is a small configuration workspace for the Opencode AI environment and related editor tooling. It is intentionally not a product application repository.

## Scope

Keep changes focused on:

- Opencode configuration files
- editor and agent configuration
- dependency and plugin metadata
- lightweight validation and housekeeping

Avoid unrelated app code, feature scaffolding, or framework setup unless explicitly requested.

## Main files

- [opencode.json](opencode.json) — main Opencode configuration
- [tui.json](tui.json) — terminal UI configuration
- [package.json](package.json) — dependency and script metadata
- [AGENTS.md](AGENTS.md) — AI agent operating guidance for this repo

## Validation

Use the repo's built-in config validation:

```bash
npm run validate:config
```

This avoids adding build pipelines or extra install steps for routine configuration work.

## Conventions

- Prefer small, reversible edits.
- Preserve existing JSON structure and formatting when possible.
- Avoid unnecessary dependency churn.
- Do not add application architecture or app source folders without explicit instruction.
