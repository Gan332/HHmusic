# Repository Guidelines

## Project Structure & Module Organization

This repo contains an Android client and an optional Node server.

- `android/` is the Kotlin + Jetpack Compose app. Source lives under `android/app/src/main/java/com/hh/music/player/`: keep UI in `ui/`, playback in `playback/`, networking in `network/`, and persistence/data in `data/` (with `data/local/` and `data/offline/` sub-packages). `web/` is an empty placeholder — ignore it.
- `server/` is an optional Express proxy. Keep Express setup in `server/src/app.js`, startup in `server/src/index.js`, upstream NetEase calls in `server/src/netease.js`, and crypto in `server/src/crypto.js`.
- Tests live in `android/app/src/test/java/com/hh/music/player/` (JVM unit tests mirroring production packages) and `server/tests/` (Node integration tests).

The app can run without the server: v1.2+ uses direct NetEase eapi calls from the Android client. Keep that default intact. The backend fallback is a runtime toggle, not a constant: `MusicRepository.useBackend` (instance `@Volatile var`), persisted via `LocalStore` key `"use_backend"` and switchable from the Settings screen (数据源). No static `USE_BACKEND` flag exists.

Account & cloud sync: `network/LoginClient.kt` implements QR-code login (poll codes 801/802/803, expiry 800). The session is only the `MUSIC_U` cookie, stored in `LocalStore` key `"login_cookie"` — no credentials are ever sent or saved. `data/CloudSync.kt` pushes favorite/unfavorite to the NetEase cloud best-effort: persist locally first, then fire-and-forget the cloud call and swallow all failures (offline, logged out, backend mode). Cloud state mirrors local intent and is never the source of truth; UI must never wait on a sync.

Tech stack (Android): Jetpack Compose + Material 3 (adaptive navigation suite), Navigation Compose, Media3/ExoPlayer + Media3 Session for playback, Retrofit + OkHttp with kotlinx.serialization for networking, Coil for images, DataStore Preferences for persistence, ZXing core for QR rendering. Current release: `versionName = "1.6"` (`versionCode = 6`) in `android/app/build.gradle.kts`; bump both when cutting a release. Java/Kotlin target is 17; `compileSdk`/`targetSdk` are 35, `minSdk` 24.

## Build, Test, and Development Commands

Server:

```bash
cd server
npm install
npm start        # run server on http://localhost:3000
npm run dev      # watch mode
npm test         # node --test tests/*.test.js
```

`npm test` globs all `tests/*.test.js` — new test files are picked up automatically. On Windows cmd/PowerShell there is no shell globbing, so this requires Node 22+ (Node's `--test` runner expands the glob itself). CI runs Node 20 on Linux, where bash expands it.

Android:

```bash
cd android
.\gradlew.bat testDebugUnitTest                 # JVM unit tests
.\gradlew.bat testDebugUnitTest --tests "com.hh.music.player.data.local.QueueCodecTest"   # single class
.\gradlew.bat assembleDebug                     # debug APK
.\gradlew.bat assembleRelease                   # R8/release build verification
```

On Windows use `.\gradlew.bat` (bash/macOS: `./gradlew`). Building requires JDK 17+ and Android SDK 35 (`minSdk` 24); on a fresh checkout Gradle needs a SDK location — create `android/local.properties` with `sdk.dir=...` or set `ANDROID_HOME` (CI generates `local.properties` itself).

## Lint quirk (do not "fix")

`app/build.gradle.kts` sets `lint { checkReleaseBuilds = false }` deliberately: AGP 8.7 lint is incompatible with Kotlin 2.1 (IncompatibleClassChangeError in lint analysis). The release build in CI exists only for R8 verification. Don't re-enable release lint without upgrading the toolchain.

## Coding Style & Naming Conventions

Match surrounding code and keep patches small. Kotlin uses 4-space indentation, `PascalCase` for composables/classes, and `camelCase` for functions and properties. Node code uses 2-space indentation, semicolons, and double quotes. No formatter or ktlint is enforced; Android lint is configured but lint tasks are not part of CI.

## Testing Guidelines

Android tests are JVM-only — no Robolectric or instrumented tests exist. Coverage is pure logic (`PlaybackEngine`, `LyricParser`, `QueueCodec`, repository/view-model tests), with `org.json` added as a JVM test dependency. Use JUnit 4 and Kotlin backtick descriptions, e.g. ``fun `blank lyrics yield no lines`() {}``. Coroutine tests must not rely on real dispatchers: inject test dispatchers into repositories/view-models (`StandardTestDispatcher(testScheduler)`), as existing tests do — concurrent tests went flaky in CI until scheduling was deterministic. Never call `runBlocking { delay(...) }`-style timing hacks instead.

Server tests use Node's built-in test runner and `node:assert/strict`, stubbing NetEase APIs through `createApp` so no network calls are required. Name tests by behavior. Run both suites locally; CI runs them too.

CI is `.github/workflows/build-apk.yml`: it runs the server tests (skipped on `v*` tags) and `testDebugUnitTest`, then assembles debug and (unsigned) release APKs. Pushing a `v*` tag creates a GitHub release with the APKs; pushes to `main` publish a `continuous` pre-release with the debug APK. Release APKs get signed only when the `KEYSTORE_*` secrets are set. The repo-local `ci-auto-fix` skill automates the push → monitor Actions → fix → re-push loop.

## Commit & Pull Request Guidelines

Use the conventional commit style visible in Git history: `feat(ui): ...`, `fix(playback): ...`, `fix(ci): ...`, `test(search): ...`, or `feat(v1.x): ...`. Keep the first line concise and add context in the body when needed.

Example:

```text
fix(playback): correct queue index after remove/move edge cases
```

PRs target `main`. Describe the change, list tests that cover it, and include screenshots for UI or playback-facing changes. Link related issues when they exist.

## Security & Configuration Tips

Never commit keystores, NetEase cookies, or credentials. `.gitignore` already excludes `*.jks`, `*.keystore`, `local.properties`, and `_ncrust_ref/`. Server behavior is configurable through `env` vars such as `PORT`, `HOST`, `UPSTREAM_TIMEOUT_MS`, `UPSTREAM_RETRIES`, `REQUEST_TIMEOUT_MS`, `ALLOWED_ORIGINS`, and `LOG_REQUESTS` (set `LOG_REQUESTS=0` to silence request logs); keep secrets out of source.