# Development

## Requirements

- JDK 21
- Android SDK with platform 36 and build-tools 36
- Linux, macOS, or WSL (Ubuntu 24.04 is what the maintainer uses)
- About 4 GB of free RAM for a full build

No device or emulator is needed for tests: UI is verified with Robolectric and Roborazzi screenshot tests on the JVM.

## Setup

```bash
git clone https://github.com/KrishGopi1/Sleep-Project.git
cd Sleep-Project
```

Point Gradle at your SDK, either with `ANDROID_HOME` or a `local.properties` file containing
`sdk.dir=/path/to/Android/Sdk` (it's gitignored). `scripts/env.sh` sets `JAVA_HOME`/`ANDROID_HOME` for the
maintainer's per-user toolchain and leaves yours alone otherwise:

```bash
source scripts/env.sh
```

## Common commands

| What | Command |
| --- | --- |
| Build the debug APK | `./gradlew assembleDebug` (output: `app/build/outputs/apk/debug/`) |
| Install on a connected phone | `./gradlew :app:installDebug` |
| Project rules | `./gradlew driftChecks` |
| Test one module | `./gradlew :core:engine:test` (JVM modules) or `./gradlew :feature:energy:testDebugUnitTest` (Android modules) |
| Verify screenshots | `./gradlew :feature:energy:verifyRoborazziDebug` |
| Re-record screenshots | `./gradlew :feature:energy:recordRoborazziDebug` |
| Lint | `./gradlew lintDebug` |
| Everything CI runs | `scripts/ci.sh` (`scripts/ci.sh --list` prints each stage) |

Build and test only the modules you changed; a full build is slow and memory-hungry.

## Project rules (`driftChecks`)

| Check | What it enforces |
| --- | --- |
| `checkNoInternetPermission` | The merged manifest has no `INTERNET` or network-state permission |
| `checkNoPlaceholders` | No TODO, FIXME, "placeholder", mock data or dead buttons in `src/main` |
| `checkDependencyRules` | Module dependency rules from [architecture.md](architecture.md#dependency-rules) |

## Where things go

See [architecture.md](architecture.md) for the module map. Rules of thumb:

- Calculations go in `:core:engine`, with their constants in `ParameterSet.kt` (with a `Citation`) and tests
  next to them in `src/test/kotlin`.
- Charts go in `:core:charts`, shared components in `:core:designsystem`, screens in `:feature:*`.
- Fakes and fixtures go in `:testing` or `src/test`, never `src/main`.
- Each module has its own Hilt `@Module`. Only `:app` binds the concrete sources and engine.

## Engine changes

The engine has golden tests (`core/engine/src/test/resources/golden/<version>.properties`), property tests and
scenario fixtures. If your change alters outputs on purpose:

1. Bump the version in `EngineVersion.kt` and `ParameterSet.kt`.
2. Add a new golden file for that version (keep the old ones).
3. Explain the change and its sources in the PR's "Scientific changes" section.

### Replaying real data

You can run the engine over an exported history without the app:

```bash
./gradlew :core:engine:run --args="path/to/export.json --now-hour=12"
```

Use a path relative to the project root (the project path may contain spaces). The file format is described in
[qa/REPLAY_FORMAT.md](qa/REPLAY_FORMAT.md). Replay files are a good way to attach reproduction data to a
scientific issue, but only share data you're happy to make public.

## Continuous integration

`.github/workflows/ci.yml` runs `scripts/ci.sh` on every pull request and push to the default branch:
project rules, unit and property tests, the debug build, screenshot verification and lint. A PR can only be
merged when it passes.

## Releases

Releases are built by `.github/workflows/release.yml` when a tag like `v0.2.0` is pushed:

1. Update `versionCode` and `versionName` in `app/build.gradle.kts` and add the release to `CHANGELOG.md`.
2. Commit, then `git tag v0.2.0 && git push origin v0.2.0`.
3. The workflow builds the APK, creates a GitHub Release and attaches `drift-v0.2.0.apk`. The Download APK link
   (`/releases/latest`) always points to the newest release.

### Signing

If these repository secrets are set, the workflow builds a signed release APK; otherwise it publishes the
debug-signed APK, which installs fine but can't be updated in place by a later signed build.

| Secret | Value |
| --- | --- |
| `DRIFT_KEYSTORE_BASE64` | `base64 -w0 drift-release.jks` |
| `DRIFT_KEYSTORE_PASSWORD` | Keystore password |
| `DRIFT_KEY_ALIAS` | Key alias |
| `DRIFT_KEY_PASSWORD` | Key password |

How to create the keystore is in [qa/release-signing.md](qa/release-signing.md). Never commit a keystore or
`keystore.properties`.
