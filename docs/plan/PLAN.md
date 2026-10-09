# Offline Sleep & Energy App — Parallel Build Plan (Drift)

Source: plan by Krish Gopi, 9 Oct 2026. This copy is the reference every agent builds from.
Section numbers are referenced from code and AGENTS.md.

## 1. Goal, scope and ground rules

Ship a signed, offline-only Android APK (working name **Drift**) that matches RISE's feature set and
analytical depth, built by 7 agents working in parallel against contracts frozen in Phase 0.

**In scope for v1.0:** daily energy schedule, sleep debt, personalized sleep need, sleep history,
sleep/wake regularity, circadian phase and melatonin window, recommended sleep/wake times, habit-timing
cards, notifications, home-screen widget, onboarding, data editing, export/backup, dark and light themes.

**Data sources:** Android Health Connect (read), Sleep as Android (via Health Connect plus CSV backup
import), and on-device phone detection (screen, usage, motion, charging, optional ambient light). A boAt
ring plugs in later through the same adapter interface with zero engine changes.

### How we use RISE as the reference (amended 9 Oct 2026, owner decision)
The owner asked for Drift to look and behave like a close clone of RISE, with more focus on the core
features and better output. So RISE is now a direct reference for layout, navigation, screen structure,
interaction patterns and chart forms, and anyone may build straight from RISE screenshots, store listings,
help centre, blog and marketing pages (the earlier two-room process is retired).

Still not allowed, because they would make the app legally unshippable rather than better:
1. Decompiling or disassembling the RISE APK, sniffing its private APIs, or extracting any file from it.
2. Using RISE's name, logo, app icon, trademarks or brand phrases in the product, or copying its
   illustrations, icon files, fonts, animation files or long-form text. Short generic UI words
   ("Sleep debt", "Peak", "Dip", "Progress") are fine; we write our own wording for everything else.

RISE says its energy schedule builds on the SAFTE fatigue model (Hursh et al. 2004, patent US 6,579,233,
expired) and that its debt weights last night ~15% within a 14-night window. We implement from the
published papers ourselves (no GPL code is copied) and aim to match or beat RISE on usefulness and
stability on real data.

### Non-negotiables
- No network permission in the manifest. No account, no analytics SDK, no crash reporter that phones home.
- Every number carries a provenance in the data: **Measured**, **Derived** (deterministic from measured
  data), **Estimated** (model output with a confidence band) or **User-entered**. Amended 9 Oct 2026 (owner
  decision): screens no longer show provenance tags or confidence bands, as RISE does not; the provenance stays in
  TalkBack descriptions and in the "how this is calculated" explanations, which open only from a "?" icon.
- No placeholders, TODOs, mock data or dead buttons in the release build; enforced by CI (section 9).
- The analysis engine is a pure, deterministic Kotlin library: same inputs + engine version = same
  outputs, and every change can be explained.

## 2. Architecture and module map

Data flows one way: source adapters → merged timeline (:core:data) → engine → AnalysisSnapshot →
screens, reminders and widget (which only read snapshots).

### Stack
| Concern | Choice |
| --- | --- |
| Language / build | Kotlin 2.2, Gradle version catalog, convention plugins (build-logic/) |
| SDK | minSdk 28, compile/target 36 |
| UI | Jetpack Compose, custom theme on Material 3 primitives, Navigation Compose |
| Charts | Hand-written Compose Canvas charts in :core:charts (no chart libraries) |
| DI | Hilt |
| Storage | Room on SQLCipher (key in Android Keystore); DataStore for preferences |
| Background | WorkManager; one short-lived foreground service only during active overnight phone tracking |
| Health data | androidx.health.connect:connect-client |
| Time | java.time; every timestamp stored as UTC instant + zone |
| Widget | Jetpack Glance |
| Performance | Baseline profiles, Macrobenchmark, R8 full mode |
| Tests | JUnit 5, kotest property tests, Robolectric, Compose UI tests, Roborazzi screenshots |

### Modules
| Module | Kind | Owner | Holds |
| --- | --- | --- | --- |
| :core:model | Pure Kotlin | A0 | Domain types, provenance, contracts C1–C5, repository interfaces |
| :core:engine | Pure Kotlin | A1 | All analysis models, explanations, versioning, replay CLI |
| :core:data | Android lib | A2 | Room schema, DAOs, repositories, merge/dedupe, import/export |
| :source:healthconnect | Android lib | A2 | Health Connect adapter, permissions, change tokens |
| :source:sleepasandroid | Android lib | A2 | CSV backup parser, HC origin mapping, broadcast |
| :source:phone | Android lib | A3 | On-device sleep detection from phone signals |
| :core:background | Android lib | A3 | Workers, scheduling, notifications, widget |
| :core:designsystem | Android lib | A4 | Tokens, type, colour, motion specs, base components |
| :core:charts | Android lib | A4 | Energy curve, debt bars, history and timing charts |
| :feature:onboarding | Android lib | A5 | Intro, permissions, first sync, first-use states |
| :feature:energy | Android lib | A5 | Today screen, energy schedule, habit cards |
| :feature:sleep | Android lib | A5 | Debt, need, history, regularity, body clock, editing |
| :feature:settings | Android lib | A5 | Sources, schedule, notifications, data, about/methodology |
| :app | Application | A0 | Wiring, nav graph, manifest, release config |
| :testing | JVM lib | A6 | Synthetic sleep histories, fake adapters/repositories (tests only) |

### Dependency rules (enforced by `./gradlew checkDependencyRules`)
- :core:engine depends only on :core:model.
- :source:* depend only on :core:model.
- :feature:* use repositories/engine through interfaces in :core:model; never import a source module.
- Only :app knows which concrete sources exist. Adding a boAt ring = one new :source:boat module registered in :app.

## 3. Phase 0 interface contracts (frozen — see core/model)
- **C1** Provenance + Quantity (core/model/.../Provenance.kt)
- **C2** SleepEpisode + Signal (Sleep.kt, Signals.kt)
- **C3** SourceAdapter (source/SourceAdapter.kt). The data layer merges all adapters into one timeline:
  overlapping episodes resolve per time span by higher quality tier, then recency; user edits always win.
  The engine never sees a source type, only canonical episodes and signals with quality tags.
- **C4** SleepEngine / EngineInput / AnalysisSnapshot (engine/). Every snapshot is stored with its engine
  version and input hash. Diffing today's snapshot against yesterday's produces "why this changed".
- **C5** ScreenState: Loading, FirstUse(stepsRemaining), Insufficient(reason, daysNeeded), Ready(model),
  SourceProblem(fix), Error (ui/ScreenState.kt). Screens are built and screenshot-tested against these
  states with fixture data from :testing, before real data exists.

Changes after freeze go through an ADR in docs/adr/ approved by A0 and affected owners.

## 4. Analysis engine specification

A chain of eight small, separately tested models run in order on every sync; each reads earlier outputs
and writes its own explanation entries. Every constant lives in one versioned `ParameterSet` with a
citation field. Constants below are starting values; A1 verifies each against the cited source.

### 4.1 Preprocessing and data quality
- **Merge:** overlapping episodes resolve per span by quality tier, then recency; user edits win. Fragments
  of one night separated by < 45 min wake merge into one main episode.
- **Classify:** the longest episode overlapping the habitual night window is MAIN; others NAP. Main sleep
  falling mostly between 08:00 and 18:00 on 3+ of the last 7 days switches on shift-work mode.
- **Assign:** each episode belongs to the date of its wake time (the "sleep day").
- **Flag, never zero-fill:** missing nights stay missing. Implausible episodes (main < 2 h or > 16 h, or
  ending in the future) are flagged and excluded unless the user confirmed them.
- **Quality weight:** each night gets a weight from source tier and completeness, used as observation precision.

### 4.2 Sleep need (personalized, adaptive)
1-D Kalman filter, local-level model.
- **Prior:** population mean by age band from extended-sleep-opportunity studies, ~8.0–8.5 h young adults,
  lower for older adults, SD ~0.6 h.
- **Informative nights:** "free" nights (no alarm, user-marked free day, or wake > 45 min later than the
  workday median) are observations, corrected for rebound: obs_t = TST_t − β·D_{t−1}, β ≈ 0.3.
- **Alarm-constrained nights** are censored: one-sided (Tobit-style) update with large variance.
- **Drift:** process noise ≈ 10 min real change per month.
- **Output:** Estimated with a 90% band. Below 5 informative nights show the prior labelled "based on
  people like you" with a counter of nights still needed.

### 4.3 Sleep debt with realistic recovery
Leaky accumulator over the last 14 sleep days:
D_t = λ·D_{t−1} + (N_t − S_t) if S_t ≤ N_t, else λ·D_{t−1} − η·(S_t − N_t); D_t ≥ 0.
- N = need that day; S = total sleep incl. naps (naps full credit up to 90 min, then half rate).
- λ gives a ~7-day half-life; η starts at 0.6, fitted per user from how fast free-night rebound shrinks.
- Displayed debt = 14-day result, bands Low < 5 h, Moderate 5–10 h, High > 10 h (tunable, basis shown).
- Missing nights imputed from same-weekday median (widens band). 3+ consecutive missing nights → "not
  enough data" instead of a guess.
- Each night's contribution is kept so the debt screen lists which nights built or repaid debt.

### 4.4 Regularity and chronotype
- **Sleep Regularity Index** (Phillips et al., 2017): probability of the same sleep/wake state at two times
  24 h apart, 0–100 over 7 and 14 days, needs 5+ paired days.
- **Timing spread:** circular SD of onset, wake and midpoint.
- **Social jetlag:** |free-day midpoint − workday midpoint|.
- **Chronotype:** MSFsc (Roenneberg), blended with the onboarding questionnaire until 4+ free nights.

### 4.5 Circadian phase
DLMO ≈ 2–3 h before habitual sleep onset.
- Circular Kalman filter tracks DLMO clock time from each night's onset and midpoint; lower weight for
  alarm-driven or shift nights.
- Re-entrainment rate-limited to ~1 h/day eastward and ~1.5 h/day westward.
- **Melatonin window:** the hour starting ~2 h after estimated DLMO. **Wind-down** starts 1 h before it.
- Shift-work mode widens the band and anchors to the main sleep wherever it falls.

### 4.6 Energy curve (three-process model, Åkerstedt & Folkard)
A(t) = S(t) + C(t) + W(t)
- **S homeostatic:** simulated over the last 14 days of real sleep; falls during wake (τ ≈ 18 h), recovers
  during sleep (τ ≈ 4 h). Debt is built into today's curve.
- **C circadian:** 24 h cosine + 12 h harmonic (post-lunch dip); phase tied to DLMO (4.5).
- **W sleep inertia:** exponential decay over the first 1–2 h after waking, larger after short/debt-heavy sleep.
- **Segmenting:** 5-min resolution, normalized 0–100, split into Grogginess, Morning peak, Afternoon dip,
  Evening peak, Wind-down, Melatonin window using extrema with minimum prominence. If too flat for a dip,
  say so rather than invent one.
- **Calibration:** optional 3-min reaction-time checks and 1–5 energy check-ins (Measured). After 10+
  check-ins a bounded least-squares fit adjusts circadian amplitude and phase offset; show how many
  check-ins shaped the curve.

### 4.7 Recommendations
- **Sleep opportunity:** TIB = (N + min(0.25·D, 1 h)) / SE, SE = personal sleep efficiency (median
  asleep/in-bed, default 0.88).
- **Bed/wake time:** wake from schedule or habitual workday wake; bedtime = wake − TIB, then checked
  against the melatonin window. If they conflict, show the trade-off instead of silently overriding.
- **Habit cards** (each labelled with its basis): caffeine cutoff (~8 h before bed), last large meal and
  alcohol (~3 h before), morning daylight (within 1 h of waking), intense exercise (not inside wind-down),
  dim lights (from wind-down).
- **Nap advice:** only when debt ≥ Moderate and an afternoon dip exists; 20–30 min, ending ≥ 7 h before bed.

### 4.8 Explanations
Diff each snapshot against the previous one and attribute every headline change: a night entered/left the
14-day window, need re-estimated, source added, user edit, circadian estimate moved. Plain language with
numbers, e.g. "Debt fell 1 h 10 min: last night's 9 h 05 min repaid 50 min, and 25 Sep dropped out of your
14-day window."

### 4.9 Data sufficiency
| Days | Shows |
| --- | --- |
| 0 | Energy curve from questionnaire + population priors only, marked Estimated |
| 1–4 | Debt with a wide band; need still the prior |
| 5–13 | Personal need begins; regularity available |
| 14+ | Full model; calibration from check-ins if present |
First launch backfills up to 90 days from Health Connect.

## 5. Data sources and background processing

### 5.1 Health Connect (primary)
- Reads sleep sessions with stages, HR, resting HR, HRV (RMSSD), steps, exercise sessions.
- First run backfills up to 90 days; then Changes API with stored change tokens. Expired token → bounded
  full re-read of the last 30 days.
- Each record's data origin (package name) maps to a quality tier through a config table.
- Required: permissions-rationale activity, history + background-read permissions where supported, install
  prompt on Android 9–13 where Health Connect is a separate app.

### 5.2 Sleep as Android
- Path 1: SaA's own Health Connect sync arrives via 5.1, tagged by origin.
- Path 2: CSV backup import via the system file picker; tolerant parser; dedupe against HC copies by overlap.
- Path 3: listen for SaA's documented tracking-finished broadcast (verify action names) to sync minutes after tracking stops.

### 5.3 Phone-based detection (no wearable)
Reconstruct each night after the fact from OS logs; near-zero battery.
- Signals: screen interactive history (usage access, optional), Google Sleep API segments/confidence
  (activity recognition), steps, charging changes, next scheduled alarm sampled each evening, alarm dismissal.
- Model: two-state HMM over 10-min bins, Viterbi decode → onset, wake, confidence. Long gaps without phone
  use are not sleep without corroboration.
- Morning confirmation: "Looks like you slept 11:40 pm to 7:05 am" with confirm or drag-to-adjust.
  Confirmed nights become User-entered with higher weight.
- Alarm set for next morning feeds the free-night classifier (4.2).

### 5.4 boAt ring readiness
If the boAt app writes to HC: add its package to the origin table at WEARABLE_RING. Otherwise a future
:source:boat implements SourceAdapter over BLE. The engine already uses HR/HRV/stages when present.

### 5.5 Background work
| Job | Trigger | Constraint | Output |
| --- | --- | --- | --- |
| Morning sync + engine run | Expected wake + 20 min, then on unlock | expedited | Snapshot, confirm card, widget |
| Day refresh | Every 3 h | Battery not low | Curve, widget |
| Reminder scheduler | After each engine run | none | Inexact alarms in 10-min windows |
| Local backup | Daily | Charging, idle | Encrypted rolling backup, 7 copies |
| Reschedule | Boot, time/zone change, app update | none | Alarms and work restored |
Engine < 50 ms for 90 days, so every UI open can recompute. Inexact alarms only (no exact-alarm permission).

### 5.6 Permissions
| Permission | For | If denied |
| --- | --- | --- |
| HC sleep, HR, HRV, steps, exercise | Measured data | Phone detection + manual entry |
| HC history + background read | Backfill > 30 days, overnight sync | Sync on open; banner explains |
| Usage access | Phone detection | Sleep API + steps; lower confidence |
| Activity recognition | Sleep API, steps | Same |
| Post notifications | Reminders | In-app cards only |
| Boot completed | Restore reminders | Restored on next open |
Every denial lands on a working screen with a "fix" action. No INTERNET permission.

## 6. UX recreation plan

### 6.1 Observation to spec
Record RISE screens/flows on a reference phone (light/dark, default and largest font), measure margins,
radii, gaps, type, icon sizes, stroke widths (dp/sp), time motion at 60 fps, write a token file + one spec
page per screen (docs/ux/). Colours are re-picked to an original palette with similar contrast/mood.
Without hands-on RISE access, A4 writes the spec from public material (store listing, help centre,
marketing pages) and the plan, and records which items still need on-device measurement.

### 6.2 Screens
| Screen | Key content | Special states |
| --- | --- | --- |
| Onboarding | Welcome, how it works, schedule + chronotype questions, choose sources, permission steps, import progress, first energy-curve reveal | HC missing, partial grants, zero nights imported |
| Energy (home) | Today's curve with live "now" marker, current phase, next peak/dip, habit cards, day switcher (yesterday/today/tomorrow) | Prior-only curve, shift-work mode, travel adjustment |
| Sleep | Debt hero + band, 14-night contribution bars, need card with confidence, regularity card, recommended bed/wake | < 5 nights, debt unavailable after missing nights |
| History | Week/month/3-month duration + timing, tap a night | Gaps shown as gaps |
| Night detail & edit | Stage chart, source + quality, edit times, add nap, mark free day, delete | Conflicting sources, user-edited badge |
| Body clock | Circadian rhythm, melatonin window, chronotype, social jetlag, regularity trend | Not enough free nights |
| "Why this changed" sheet | Explanation lines for any metric, by tapping it | First day, no prior snapshot |
| Settings | Sources + health, schedule, reminders, theme, export, import, backup, reset, methodology with citations | Revoked permission, failed import |
| Widget | Mini energy curve, now marker, next phase | Stale data > 12 h |

### 6.3 Design system and motion
Tokens (spacing, radii, elevation, colour roles both themes, type scale on an open-licensed geometric
sans), components (cards, metric heroes, confidence bands, provenance chips, segmented controls, bottom
sheets, permission/empty panels), Canvas charts (energy curve with gradient + phase bands, debt bars,
timing range bars, stage chart, regularity trend) with accessibility text describing peaks/dips. Motion:
curve draw-in, count-up, now-marker pulse, card stagger, shared-element card→detail, honouring "remove
animations". Accessibility: TalkBack labels for every chart, WCAG AA contrast, holds at 200% font scale.

### 6.4 Every screen ships with all six states
Loading, first use, insufficient, ready, source problem, error — designed, built and screenshot-tested in
both themes. A screen isn't done until all six pass.

## 7. Agent workstreams
| Agent | Owns | Done when |
| --- | --- | --- |
| A0 Architect/Integrator | :core:model, :app, build-logic, CI, docs/adr | Release build green, all gates passed |
| A1 Sleep Science | :core:engine | Golden + property tests pass; plausibility review |
| A2 Data & Sources | :core:data, :source:healthconnect, :source:sleepasandroid | 90-day backfill < 10 s; zero duplicate nights on mixed-source fixtures |
| A3 Platform | :source:phone, :core:background | Overnight battery < 1%; reminders survive reboot, Doze, zone change |
| A4 Design & UX | :core:designsystem, :core:charts, docs/ux | Component gallery screenshot-tested; review vs spec passes |
| A5 Features | :feature:* | Every screen reachable, every button works, all states tested |
| A6 QA & Release | :testing, docs/qa, release pipeline | Section 9 checklist ticked |

## 8. Workflow
Each agent works on its own branch/worktree and merges to main through the integration check (build, unit
tests, lint, dependency rules, screenshot diff). Contract changes need an ADR. Blockers go to the owner as
an issue (docs/issues/), never as a direct edit to their modules.

## 9. Testing, validation and release criteria

### 9.1 Engine correctness
- Golden tests: fixed inputs, pinned outputs per engine version; any output change needs a version bump + changelog line.
- Property tests: debt never negative; less sleep never lowers debt; need within physiological bounds; same
  input → same output; shifting all data by a timezone leaves local-time outputs unchanged.
- Scenario fixtures (A6): steady sleeper, chronic short sleeper, weekend catch-up, new parent with
  fragmented nights, rotating night shift, eastbound and westbound travel, a missing week, duplicate
  sources, DST changes both directions.

### 9.2 Real-world validation
Replay locally exported histories through the engine replay CLI. Need changes ≤ 15 min/week except after a
real lifestyle change. Phone detection median onset/wake error ≤ 30 min vs SaA/wearable. SRI within 1 point
of a reference Phillips et al. implementation. A sleep-science reviewer reads the methodology screen and 20
random explanations.

### 9.3 Release checklist
- [ ] Manifest has no INTERNET permission; CI fails if one appears
- [ ] No TODO/FIXME/"placeholder" strings and no hard-coded metric values in feature modules
- [ ] Every screen × state × theme × font scale has a screenshot test
- [ ] Every clickable element covered by a UI test asserting an effect
- [ ] Cold start to first frame < 500 ms on a mid-range phone with baseline profiles
- [ ] < 1% janky frames scrolling Energy, Sleep, History
- [ ] Overnight battery < 1% with phone detection on
- [ ] Reminders and sync survive reboot, Doze, standby buckets, force-stop then reopen, zone change
- [ ] Clean recovery from permission revoke, HC uninstall, full storage, corrupt DB (restore from backup)
- [ ] DB migrates from every earlier schema version shipped in betas
- [ ] Export, wipe and re-import round-trips all data exactly
- [ ] R8 full mode, signed with v2+v3 schemes, version code bumped
- [ ] Two-week dogfood on 3+ phones, no crash, no data loss

## 10. Open decisions (owner: Krish)
1. App name/brand (working name Drift). 2. Distribution: sideload vs Play Store later. 3. Reference phone.
4. Include the 3-min reaction-time test in v1.0 or check-ins only. 5. Validation data for replay.
6. Where builds run (currently: local WSL, smoke test passed 9 Oct 2026).
