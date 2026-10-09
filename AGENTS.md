# Drift — Agent Instructions

Shared instructions for every AI agent in this repo (Antigravity, Claude Code CLI/extension, subagents).
Drift is an offline-only Android sleep & energy app. The full spec is [docs/plan/PLAN.md](docs/plan/PLAN.md);
section numbers below refer to it.

## Environment
- Code lives in WSL (Ubuntu 24.04) at `/home/krish/Sleep Project`. Run all commands in WSL, not Windows.
- Toolchain is installed per-user (no sudo): JDK 21 in `~/.jdks`, Android SDK in `~/Android/Sdk`
  (platform 36, build-tools 36). **Run `source scripts/env.sh` before any Gradle command.**
- Build: `./gradlew assembleDebug`. Gates: `./gradlew driftChecks`. Tests: `./gradlew :<module>:test`.
- Machine has ~8 GB RAM shared by several agents. Build only the modules you own
  (`./gradlew :core:engine:test`, not `./gradlew build`). Never raise heap settings in gradle.properties.
- No device or emulator is attached. Verify Android code with Robolectric/Roborazzi unit tests.

## Stack (fixed)
Kotlin 2.2 · AGP 8.13 · Gradle 8.14 · minSdk 28 / compile+target 36 · Compose + Material 3 primitives ·
Hilt · Room + SQLCipher · DataStore · WorkManager · Health Connect · Glance · JUnit 5 + kotest-property ·
Robolectric (JUnit4 via vintage engine) · Roborazzi. Versions live in `gradle/libs.versions.toml`.
Convention plugins (`build-logic/`): `drift.jvm.library`, `drift.android.library`, `drift.android.compose`,
`drift.android.feature`, `drift.android.application`, `drift.hilt`.

## Module ownership — touch only what you own
| Agent | Owns (write access) |
| --- | --- |
| A0 Architect/Integrator | `core/model`, `app`, `build-logic`, root Gradle files, `docs/adr`, `AGENTS.md` |
| A1 Sleep Science | `core/engine` |
| A2 Data & Sources | `core/data`, `source/healthconnect`, `source/sleepasandroid` |
| A3 Platform | `source/phone`, `core/background` |
| A4 Design & UX | `core/designsystem`, `core/charts`, `docs/ux` |
| A5 Features | `feature/*` |
| A6 QA & Release | `testing`, `docs/qa` |

- Need a change in a module you don't own (including a contract in `core/model`)? Write it up in
  `docs/issues/<your-agent>-<short-name>.md` (problem, proposed change, who it blocks) and work around it
  locally. A0 reviews contract changes as ADRs in `docs/adr/`.
- `gradle/libs.versions.toml` is shared: you may **append** entries at the end of a section; never edit or
  reorder existing lines.
- Each module has its own Hilt `@Module` for its bindings. Only `:app` binds the concrete `SourceAdapter`
  set and `SleepEngine` implementation.

## Contracts (frozen, in `core/model`)
C1 `Provenance`/`Quantity` · C2 `SleepEpisode`/`Signal` · C3 `SourceAdapter` · C4 `SleepEngine`/
`EngineInput`/`AnalysisSnapshot` · C5 `ScreenState` · repository interfaces in `model/repository`.
Code against these, not against another agent's implementation classes.

## Non-negotiables
- No INTERNET permission, no network code, no analytics or crash reporting. (`checkNoInternetPermission`)
- Every number shown to users carries a `Provenance`; ESTIMATED values carry a `Confidence`.
- No TODO/FIXME/"placeholder"/mock data/dead buttons in `src/main`. (`checkNoPlaceholders`)
  Fakes and fixtures belong in `testing/` or `src/test`.
- `:core:engine` is pure and deterministic: no I/O, no `Instant.now()`, no `Random` without a fixed seed,
  no Android imports.
- `java.time` everywhere; store UTC instants plus zone.
- RISE is the direct visual and interaction reference (plan section 1, amended). Never decompile it or use its
  name, logo, icon files, fonts, illustrations or long-form text.

## Definition of done (per change)
1. Your modules compile and their tests pass: `./gradlew :<module>:test` for each module you own.
2. `./gradlew driftChecks` passes.
3. New logic has tests next to it (`src/test/kotlin`, same package).
4. Commit on your own branch with a clear message. Don't push. Don't merge to `main`; A0 integrates.

## Git workflow
- Each agent works in its own git worktree on branch `agent/<id>-<name>` (e.g. `agent/a1-engine`).
- Keep commits small and focused. Never rewrite `main` or another agent's branch.
- Don't commit secrets, keystores or `local.properties` (see `.gitignore`).
