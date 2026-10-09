# Drift test plan

How every item in plan section 9 is verified, by which test or module, and by whom. Section numbers refer to
`docs/plan/PLAN.md`. "Gate" = runs in `scripts/ci.sh` (the merge-queue check) and blocks a merge.

## 0. Tooling in `:testing` (A6)
`:testing` is a pure Kotlin/JVM library used only from test configurations (`checkDependencyRules` lets `:feature:*`
and `:app` depend on it; nothing in `src/main` may). Contents:

| Package | Use | Who |
| --- | --- | --- |
| `app.drift.testing.fakes` | `Fake*Repository` for every repository interface, `FakeSourceAdapter`; seed state, inject faults (`failNext`), record calls (`callsTo`) | A5 UI tests, A3/A2 tests, A1 none |
| `app.drift.testing.fixtures` | `SnapshotFixtures` (15 named `AnalysisSnapshot`s, IST and US zone), `ScreenStateFixtures` (all six C5 states) | A5/A4 screenshots, A5 UI tests |
| `app.drift.testing.synthetic` | `Scenarios` (plan 9.1 histories with ground truth), `ReplayJson` (file format, `docs/qa/REPLAY_FORMAT.md`) | A1 golden/property/replay, A2 merge tests |

Fixed clock: `FixtureContext.IST` = 2026-10-14 07:30Z (13:00 Asia/Kolkata); `FixtureContext.US_EASTERN` = 2026-10-14 14:00Z (10:00 New York).
`:testing` has its own tests (`./gradlew :testing:test`) so fixtures are trusted: debt trend sums to the debt, segments tile each day,
ESTIMATED values carry bands, generators are deterministic per seed, scenarios have their advertised properties, fakes behave.

## 1. Section 9.1 Engine correctness
| Item | Verified by | Where | Owner | Gate |
| --- | --- | --- | --- | --- |
| Golden tests: fixed inputs, pinned outputs per engine version; change needs version bump + changelog | Golden files per `EngineVersion`, inputs from `Scenarios.*(ScenarioConfig(seed = N))` or `ReplayJson` files | `:core:engine` `src/test` | A1 (inputs by A6) | `:core:engine:test` |
| Property: debt never negative | kotest-property over generated `EngineInput`s | `:core:engine` | A1 | yes |
| Property: less sleep never lowers debt | same, monotonicity over perturbed episodes | `:core:engine` | A1 | yes |
| Property: need within physiological bounds | same, over all `Scenarios.all(seed)` for seeds 1..20 | `:core:engine` | A1 | yes |
| Property: same input, same output | run twice, compare snapshots | `:core:engine` | A1 | yes |
| Property: time-zone shift leaves local-time outputs unchanged | `SyntheticHistory.reZoned(zone)` then compare local-time outputs | `:core:engine` using `:testing` | A1 (helper by A6) | yes |
| Scenario: steady sleeper | `Scenarios.steadySleeper`: need estimate approaches 8 h, debt about 0, SRI high | `:core:engine` | A1 | yes |
| Scenario: chronic short sleeper | `chronicShortSleeper`: debt HIGH, nap advice offered, need not dragged down to the observed 6 h | `:core:engine` | A1 | yes |
| Scenario: weekend catch-up | `weekendCatchUp`: social jetlag above 1.5 h, debt repaid at about half rate | `:core:engine` | A1 | yes |
| Scenario: new parent, fragmented nights | `newParent`: sleep totals use asleep time not in-bed; naps credited | `:core:engine` | A1 | yes |
| Scenario: rotating night shift | `rotatingNightShift`: shift-work mode on, no spurious chronotype, DLMO within 3 h of `truth` | `:core:engine` | A1 | yes |
| Scenario: eastbound / westbound travel | `easternTravel` / `westernTravel`: re-entrainment reported, direction right, remaining shift within 1.5 h of `truth.dlmoMinutesByDate` | `:core:engine` | A1 | yes |
| Scenario: a missing week | `missingWeek`: debt unavailable during the gap, recovers after 2 nights, imputed rows flagged | `:core:engine` | A1 | yes |
| Scenario: duplicate sources | `duplicateSources.rawEpisodes` through A2's merge: exactly one episode per night (`truth.nights.size`); the engine on `episodes` equals the engine on a single-source history | `:core:data` merge tests, `:core:engine` | A2 + A1 | yes |
| Scenario: DST both directions | `dstSpringForward` / `dstFallBack`: the 7 h / 9 h night is measured in elapsed time; bedtime and regularity in local time do not jump | `:core:engine` | A1 | yes |
| Data-quality handling | `noisyPhoneInferred`: implausible episodes excluded, low-quality nights down-weighted | `:core:engine`, `:core:data` | A1, A2 | yes |

Fixtures stay engine-independent on purpose: a fixture snapshot is for UI; engine behaviour is checked with `Scenarios` + `truth`.

## 2. Section 9.2 Real-world validation
Procedure and report in `VALIDATION_REPORT_TEMPLATE.md`. Not a CI gate; run before each release candidate on Krish's exported
histories (open decision 5) plus the synthetic corpus as a baseline.

| Criterion | Method | Owner |
| --- | --- | --- |
| Need changes <= 15 min/week except after a real lifestyle change | Replay CLI week by week with `toEngineInput(at)`, tabulate weekly need | A1 runs, A6 reports |
| Phone detection median onset/wake error <= 30 min vs Sleep as Android / wearable | Phone detector over nights that also have a reference tier; error = median absolute difference | A3 supplies detector output, A6 reports |
| SRI within 1 point of a reference Phillips et al. implementation | Independent implementation (script in the report) on the same episodes vs the engine's `sri7`/`sri14` | A6 |
| Sleep-science review of methodology screen + 20 random explanations | Seeded random sample of explanation strings from replays; reviewer signs the report | Krish arranges reviewer |

## 3. Section 9.3 Release checklist
Each line, with its command or evidence, is in `RELEASE_CHECKLIST.md`. Summary of automation:

| Item | Automated | Verified by |
| --- | --- | --- |
| No INTERNET permission | yes | `checkNoInternetPermission` (in `driftChecks`) plus `aapt2 dump permissions` on the release APK |
| No TODO/FIXME/placeholder, no hard-coded metrics | partly | `checkNoPlaceholders`; hard-coded values by review plus screenshot tests that render fixtures with changed numbers |
| Screen x state x theme x font scale screenshots | yes | Roborazzi `verifyRoborazziDebug` per feature module; test names follow `<Screen>_<state>_<theme>_<fontScale>`; states from `ScreenStateFixtures.all`, models from `SnapshotFixtures` |
| Every clickable has a UI test asserting an effect | yes | Compose tests in `feature/*` using `Fake*Repository.callsTo(...)` / state assertions; audit command in the checklist |
| Cold start < 500 ms | yes (device) | `:benchmark` `StartupBenchmark` + `check_results.py` (`benchmark-module.md`) |
| < 1% janky frames | yes (device) | `:benchmark` `ScrollBenchmark` for Energy, Sleep, History |
| Overnight battery < 1% | manual (device) | batterystats procedure in the checklist |
| Reminders/sync survive reboot, Doze, standby, force-stop, zone change | partly | WorkManager/alarm tests in `:core:background` (Robolectric, A3) plus the adb matrix on device |
| Clean recovery (revoke, HC uninstall, full storage, corrupt DB) | partly | Unit tests with `FakeSourceAdapter` states and storage-failure injection (A2/A3), plus the device matrix |
| DB migration from every shipped schema | yes | Room `MigrationTestHelper` per consecutive version, exported schemas checked in (A2) |
| Export, wipe, re-import exact round trip | yes | Round-trip test over `Scenarios` histories in `:core:data` (A2); UI flow with `FakeBackupRepository` (A5) |
| R8 full mode, v2+v3 signing, version code bumped | yes | `release-signing.md` commands; `apksigner verify` |
| Two-week dogfood on 3+ phones | manual | Dogfood log in the checklist |

## 4. Merge-queue check (`scripts/ci.sh`)
`driftChecks` -> unit tests for all modules -> `:app:assembleDebug` -> Roborazzi verify (modules that have screenshots) -> lint.
It stops at the first failure and prints which stage failed. Locally the same commands can be run one stage at a time:
`scripts/ci.sh --list` prints them; `scripts/ci.sh --only <stage>` runs one.

## 5. Conventions for tests written against `:testing`
- UI tests: build the screen's ready model from a `SnapshotFixtures` entry; wrap with `ScreenStateFixtures.all(model)` for the six states.
- Assert effects through fakes: `assertThat(repo.callsTo("saveProfile")).hasSize(1)`, not through implementation classes.
- Error paths: `repo.failNext("sync", IOException())` then check the Error/SourceProblem state and the retry button.
- Never read the system clock; use `FixtureContext.clock` or pass `now` explicitly.
