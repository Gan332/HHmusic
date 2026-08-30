---
name: config-maintain
description: "Use when: updating Opencode config, validating agent settings, or maintaining the config workspace without adding app code or build steps."
---

# Config Maintain

Keep this workspace stable as a configuration-focused Opencode environment.

## Scope

This skill applies when working on:

- [opencode.json](../../opencode.json)
- [tui.json](../../tui.json)
- [package.json](../../package.json)
- [AGENTS.md](../../AGENTS.md)
- documentation and repo maintenance around the Opencode environment

Do not create product app structure, frontend code, backend services, or unrelated framework scaffolding unless the user explicitly asks for it.

## Required behavior

- Prefer minimal, reversible edits.
- Preserve existing JSON structure and ordering when possible.
- Keep dependencies small and avoid dependency churn.
- Avoid installing extra applications or plugins just to validate a change.
- Avoid local build or compile workflows unless the task explicitly requires them.

## Validation

Run the repo validation command before finishing a change:

```bash
npm run validate:config
```

This is the default verification step for configuration-only work in this repo.

## Preferred workflow

1. Read the relevant config file before editing.
2. Decide whether the change is truly config-related.
3. Make the smallest possible patch.
4. Re-run `npm run validate:config`.
5. Summarize the impact and confirm no extra app or installer steps were introduced.

## Red flags

- Creating new app modules or business logic
- Adding framework scaffolding not asked for
- Running build commands for validation
- Installing extra tools just to verify a config change
