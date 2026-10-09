# Replay file format (`drift-replay`, version 1)

One JSON file describes a complete user history so the engine replay CLI (A1), the validation procedure
(plan 9.2) and tests can run the same data. The format is defined and implemented in `:testing`:
`app.drift.testing.synthetic.ReplayJson` (`write` / `read`) and `ReplayData`. A1's CLI should depend on
`:testing` for reading (it is a pure JVM module) or follow this document exactly.

```
val text = ReplayJson.write(Scenarios.steadySleeper())     // SyntheticHistory -> JSON
val data = ReplayJson.read(text)                           // JSON -> ReplayData
val input = data.toEngineInput(at = data.now)              // EngineInput, 90-day window, only data that existed at `at`
```

`toEngineInput(at)` is the replay primitive: to replay week by week, call it once per `at` and feed each
snapshot's `EngineState` back as `priorState` (the CLI owns that loop).

## Conventions
- Instants: ISO-8601 UTC with `Z` (`2026-10-14T07:30:00Z`). Dates `yyyy-MM-dd`. Times `HH:mm`.
- Enums use their Kotlin names (`WEARABLE_WRIST`, `MAIN`, `LIGHT`, ...).
- `null` fields are written explicitly as `null`; readers also accept them missing.
- Unknown keys must be ignored (so `truth` and future additions never break a reader).

## Top level
| Key | Type | Notes |
| --- | --- | --- |
| `format` | `"drift-replay"` | required |
| `version` | `1` | required; readers reject other versions |
| `name` | string | scenario or file label |
| `zone` | IANA id | the user's zone at `now` (becomes `EngineInput.zone`) |
| `now` | instant | the "present"; default replay point |
| `profile` | object | see below |
| `episodes` | array | canonical (post-merge) `SleepEpisode`s, any order |
| `annotations` | array | `DayAnnotation`s |
| `checkIns` | array | `CheckIn`s |
| `signals` | array | optional; `Signal`s |
| `truth` | object | synthetic data only; readers ignore it |

## `profile`
```json
{"ageBand":"AGE_26_35","chronotype":"NEITHER",
 "schedule":{"workdays":["MONDAY","TUESDAY"],"wakeTime":"07:00","freeDayBedtime":"23:30","freeDayWakeTime":"08:30"},
 "goals":{"targetSleepMinutes":null,"remindersEnabled":true}}
```
`chronotype`, the three schedule times and `targetSleepMinutes` may be `null`.

## `episodes[]`
```json
{"id":"steady-healthconnect-2026-10-14","start":"2026-10-13T17:20:00Z","end":"2026-10-14T01:30:00Z",
 "zone":"Asia/Kolkata","kind":"MAIN",
 "origin":{"sourceId":"healthconnect","recordId":"...","qualityTier":"WEARABLE_WRIST","dataOrigin":null,"device":null},
 "quality":{"completeness":1.0,"detectionConfidence":1.0,"flags":["USER_CONFIRMED"]},
 "userEdited":false,
 "inBed":{"start":"...","end":"..."},
 "stages":[{"stage":"LIGHT","start":"...","end":"..."}]}
```
`inBed` and `stages` may be `null`. `zone` is the local zone at the time, which is how travel and DST appear.

## `annotations[]`
`{"date":"2026-10-14","freeDay":false,"alarmSet":true,"alarmDismissedAt":"2026-10-14T01:30:00Z"}` (the last three may be `null`).

## `checkIns[]`
- `{"type":"ENERGY_RATING","at":"...","rating":4}`
- `{"type":"REACTION_TEST","at":"...","medianRtMillis":284.5,"lapses":1,"trials":20}`

## `signals[]`
Every object has `type` (the Kotlin class name of `Signal`), `origin` (same shape as an episode origin) and:
| `type` | other keys |
| --- | --- |
| `HeartRateSample`, `RestingHeartRate` | `at`, `bpm` |
| `HrvSample` | `at`, `rmssdMillis` |
| `StepBucket` | `interval` (`{start,end}`), `count` |
| `ActivityBucket` | `interval`, `activity` (`ActivityType`) |
| `ScreenEvent` | `at`, `interactive` |
| `MotionBucket` | `interval`, `motionLevel`, `sleepConfidence` (nullable) |
| `LightBucket` | `interval`, `meanLux` |
| `ChargingEvent` | `at`, `plugged` |
| `AlarmEvent` | `at`, `kind` (`AlarmKind`) |

## `truth` (synthetic only, ignored by engine and CLI)
`sleepNeedMinutes`, `dlmoMinutesByDate` (date -> minutes after local midnight in that day's zone),
`missingDates`, `facts` (scenario-specific strings such as `dstTransitionDate`, `zoneChangeDate`) and `nights`
(`date`, `zone`, `start`, `end`, `awakenings`). Use it to score the engine: need error, DLMO error.

## Exporting real data
Real histories exported from the app (Settings > Export) are mapped to this same shape without `truth`;
A2's exporter is the source of truth for that file, and `ReplayJson.read` is the acceptance test for it.
