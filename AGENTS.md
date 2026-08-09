# Repository Guidelines

## Project Structure & Module Organization

This repo contains an Android client and an optional Node server.

- `android/` is the Kotlin + Jetpack Compose app. Source lives under `android/app/src/main/java/com/hh/music/player/`: keep UI in `ui/`, playback in `playback/`, networking in `network/`, and persistence/data in `data/`.
- `server/` is an optional Express proxy. Keep Express setup in `server/src/app.js`, startup in `server/src/index.js`, upstream NetEase calls in `server/src/netease.js`, and crypto in `server/src/crypto.js`.
- Tests live in `android/app/src/test/` (JVM unit tests) and `server/tests/` (Node integration tests).

The app can run without the server: v1.2+ uses direct NetEase eapi calls from the Android client. Keep that default intact unless a change intentionally flips `MusicRepository.USE_BACKEND`.

## Build, Test, and Development Commands

Server:

```bash
cd server
npm install
npm start        # run server on http://localhost:3000
npm run dev      # watch mode
npm test         # run node --test tests/
```

Android:

```bash
cd android
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew assembleDebug       # debug APK
./gradlew assembleRelease     # R8/release build verification
```

On Windows use `.\gradlew.bat` if needed. Run the app from Android Studio after Gradle sync.

## Coding Style & Naming Conventions

Match surrounding code and keep patches small. Kotlin uses 4-space indentation, `PascalCase` for composables/classes, and `camelCase` for functions and properties. Node code uses 2-space indentation, semicolons, and double quotes. No formatter or linter is enforced yet.

## Testing Guidelines

Android JVM tests use JUnit 4 and Kotlin backtick descriptions, for example ``fun `blank lyrics yield no lines`() {}``. Server tests use Node’s built-in test runner and `node:assert/strict`, stubbing NetEase APIs through `createApp` so no network calls are required. Name tests by behavior. Run both suites locally; CI runs them too.

## Commit & Pull Request Guidelines

Use the conventional commit style visible in Git history: `feat(ui): ...`, `fix(ci): ...`, `perf(v1.4): ...`, or `ci: ...`. Keep the first line concise and add context in the body when needed.

Example:

```text
fix(playback): retry resolved song URLs before skipping
```

PRs target `main`. Describe the change, list tests that cover it, and include screenshots for UI or playback-facing changes. Link related issues when they exist.

## Security & Configuration Tips

Never commit keystores, NetEase cookies, or credentials. `.gitignore` already excludes `*.jks`, `*.keystore`, and `_ncrust_ref/`. Server behavior is configurable through `env` vars such as `PORT`, `HOST`, `UPSTREAM_TIMEOUT_MS`, and `UPSTREAM_RETRIES`; keep secrets out of source.
