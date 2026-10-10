# Architecture

Drift is a modular, offline-only Android app. Data flows one way:

```text
Source adapters ──> :core:data ──> :core:engine ──> AnalysisSnapshot ──> screens, reminders, widget
(Health Connect,    (Room +         (pure Kotlin,     (stored with engine
 Sleep as Android,   SQLCipher,      deterministic)     version + input hash)
 phone detection,    merge, dedupe,
 manual entry)       user edits win)
```

Screens, reminders and the widget only ever read snapshots. They never call a source or the engine directly.

## Modules

| Module | Kind | Holds |
| --- | --- | --- |
| `:core:model` | Pure Kotlin | Domain types, provenance, the frozen contracts (C1-C5), repository interfaces |
| `:core:engine` | Pure Kotlin | All analysis models, explanations, the versioned `ParameterSet`, replay CLI |
| `:core:data` | Android library | Room schema on SQLCipher, DAOs, repositories, merge and dedupe, import/export |
| `:source:healthconnect` | Android library | Health Connect adapter, permissions, change tokens, origin → quality tier table |
| `:source:sleepasandroid` | Android library | CSV backup parser, tracking-finished broadcast, install detection |
| `:source:phone` | Android library | On-device sleep detection from phone signals (two-state HMM) |
| `:core:background` | Android library | WorkManager jobs, scheduling, notifications, Glance widget |
| `:core:designsystem` | Android library | Tokens, typography, colour, motion, base components |
| `:core:charts` | Android library | Hand-written Compose Canvas charts (no chart library) |
| `:feature:onboarding` | Android library | Intro, permissions, first sync |
| `:feature:energy` | Android library | Energy timeline, drag-to-edit sleep, habit markers |
| `:feature:sleep` | Android library | Sleep home, debt, need, history, regularity, body clock, Progress |
| `:feature:settings` | Android library | Learn tab and methodology, sources, schedule, notifications, data management, about |
| `:app` | Application | Hilt wiring, navigation graph, manifest, release config |
| `:testing` | JVM library | Synthetic sleep histories, fake adapters and repositories (tests only) |

Build logic lives in `build-logic/` as convention plugins: `drift.jvm.library`, `drift.android.library`,
`drift.android.compose`, `drift.android.feature`, `drift.android.application`, `drift.hilt`. Library versions are in
`gradle/libs.versions.toml`.

## Dependency rules

Enforced by `./gradlew checkDependencyRules` (part of `driftChecks`):

- `:core:engine` depends only on `:core:model`.
- `:source:*` depend only on `:core:model`.
- `:feature:*` use repositories and the engine through interfaces in `:core:model` and never import a source module.
- Only `:app` knows which concrete sources exist. Adding a new wearable means one new `:source:<name>` module
  that implements `SourceAdapter`, registered in `:app`.

## Contracts (`:core:model`)

| Contract | What it is |
| --- | --- |
| C1 `Provenance` / `Quantity` | Every number carries where it came from (Measured, User-entered, Estimated...) and, for estimates, a confidence |
| C2 `SleepEpisode` / `Signal` | Canonical sleep episodes and signals (HR, HRV, steps, screen use...) with quality tags |
| C3 `SourceAdapter` | What a data source implements. The engine never sees a source type, only canonical episodes |
| C4 `SleepEngine` / `EngineInput` / `AnalysisSnapshot` | Engine input and output. Every snapshot stores its engine version and input hash |
| C5 `ScreenState` | Loading, FirstUse, Insufficient, Ready, SourceProblem, Error. Every screen handles all six |

Changes to a contract go through an ADR in [`docs/adr/`](adr/).

## Engine

`:core:engine` runs a chain of small models in order (preprocessing → sleep need → debt → regularity and
chronotype → circadian phase → energy curve → recommendations → explanations). Each model reads earlier
outputs and adds explanation entries. Every constant lives in one versioned `ParameterSet` with a citation.

The engine is pure: no Android imports, no I/O, no `Instant.now()`, no unseeded randomness. It's fast enough
(well under 50 ms for 90 days) that the app recomputes on every open. See [science.md](science.md) for the models.

## Storage and privacy

- Room on SQLCipher; the database key is kept in Android Keystore.
- Preferences in DataStore.
- The manifest removes `INTERNET` and `ACCESS_NETWORK_STATE` even if a library declares them, and
  `checkNoInternetPermission` fails the build if they come back.

## Background work

| Job | Trigger | Output |
| --- | --- | --- |
| Morning sync + engine run | Expected wake + 20 min, then on unlock | Snapshot, confirm card, widget |
| Day refresh | Every 3 h, battery not low | Curve, widget |
| Reminder scheduler | After each engine run | Inexact alarms (no exact-alarm permission) |
| Local backup | Daily, charging and idle | Encrypted rolling backup, 7 copies |
| Reschedule | Boot, time or zone change, app update | Alarms and work restored |
