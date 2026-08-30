# AGENTS.md

## Project purpose

This repository is a configuration workspace for the Opencode AI environment, not a full application codebase. The main project files are:

- [package.json](package.json)
- [opencode.json](opencode.json)
- [tui.json](tui.json)

Treat this repo as a repo for editor/agent configuration and dependency setup rather than feature development.

## Working conventions

- Prefer minimal, reversible edits.
- Keep JSON valid and preserve existing formatting and ordering when possible.
- Do not invent application architecture, business logic, or framework scaffolding unless the task explicitly asks for it.
- When updating dependencies, keep [package.json](package.json) and the lockfile consistent; prefer the smallest possible change.
- When editing [opencode.json](opencode.json), preserve plugin entries and the existing schema conventions.

## Common commands

This repo has no custom build or test scripts defined in [package.json](package.json). The meaningful validation action is:

- `npm run validate:config`
- `npm install` only when the dependency file explicitly needs to be refreshed

Do not invent or run build pipelines for this repository. The project is intentionally config-focused and should remain lightweight.

## What not to do

- Do not assume this is a web app, mobile app, or backend service.
- Do not add unrelated source folders or app code unless explicitly requested.
- Do not broaden the task into unrelated modernization or refactoring.
- Do not install extra applications or plugins just to validate a change.
- Do not introduce local build steps or compile flows when the work is limited to config and agent behavior.

## Preferred workflow for AI agents

1. Check the config files before making changes.
2. Keep changes scoped to the task and the existing repo structure.
3. Validate JSON correctness after editing config files with `npm run validate:config`.
4. Prefer the smallest possible patch that satisfies the request.
5. Avoid dependency churn unless the task explicitly requires it.
