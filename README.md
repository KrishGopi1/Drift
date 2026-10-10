<div align="center">

<img src="docs/assets/banner.jpg" alt="Drift — Sleep smarter. Live better." width="100%"/>

<br/>

# Drift

**Offline-first Android sleep & energy tracker · Built with science, not surveillance**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-minSdk%2028-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-B09BFF?style=flat-square)](#license)
[![No Internet](https://img.shields.io/badge/🔒%20No%20Internet-Offline%20Only-success?style=flat-square)](#privacy)

</div>

---

## ✨ What is Drift?

Drift is a **fully offline** Android app that gives you a scientifically-grounded picture of your sleep health and daily energy — with zero data ever leaving your phone.

It combines your sleep history (from Health Connect, Sleep as Android, or on-device phone detection) with peer-reviewed sleep science to show you:

- 📊 **Your personal energy curve** throughout the day — peaks, dips, wind-down, and melatonin window
- 💤 **Sleep debt** accumulated over the last 14 nights, with nightly breakdown and recovery tracking
- 🎯 **Your personalized sleep need** — estimated via Kalman filter from your own free-night data
- 🕰️ **Circadian phase & chronotype** — estimated DLMO, ideal bed/wake times, social jetlag
- 📈 **Sleep regularity** — Sleep Regularity Index over 7 and 14 days
- 🔔 **Habit cards** — caffeine cutoff, light exposure, wind-down timing, and nap windows, all with scientific basis

> No account. No analytics. No ads. No internet permission. Your data stays on your device, always.

---

## 📱 Screens

| Tab | Key content |
|---|---|
| **Sleep** | Debt gauge · Last night card · 14-night contribution bars · Sleep need · Regularity · Tonight |
| **Energy** | Continuous 36 h timeline · Drag-to-edit sleep block · Habit markers · Melatonin window · Pull-up "Up next" |
| **Progress** | Night-by-night times · Debt line chart · Morning quality ratings |
| **Learn** | Original articles · Body clock · How Drift calculates (with citations) |

Every screen ships in six states: **Loading · First Use · Insufficient · Ready · Source Problem · Error** — tested in both dark and light themes at 100% and 200% font scale.

---

## 🔬 The Science

Every number in Drift is grounded in published research. The analysis engine is a **pure, deterministic Kotlin library** — same inputs + engine version = same outputs, every time.

### Energy Curve — Three-Process Model (Åkerstedt & Folkard)

```
A(t) = S(t) + C(t) + W(t)
```

- **S(t) — Homeostatic drive:** modelled over the last 14 days of real sleep; falls during wake (τ ≈ 18 h), recovers during sleep (τ ≈ 4 h). Sleep debt is built into today's curve.
- **C(t) — Circadian rhythm:** 24 h cosine + 12 h harmonic (post-lunch dip); phase anchored to your estimated DLMO.
- **W(t) — Sleep inertia:** exponential decay over 1–2 h after waking, larger after short or debt-heavy sleep.

### Sleep Need — Adaptive Kalman Filter

A 1-D Kalman local-level model estimates your personal sleep need from free nights (no alarm, user-marked free day), corrected for rebound from prior debt. Starts from an age-band population prior (~8.0–8.5 h); converges to your personal value after ~5 informative nights.

### Sleep Debt — Leaky Accumulator (14-night window)

```
D_t = λ·D_{t−1} + (N_t − S_t)    if S_t ≤ N_t   (accumulate)
D_t = λ·D_{t−1} − η·(S_t − N_t)  if S_t > N_t   (recover)
```

λ gives a ~7-day half-life. η (recovery rate, default 0.6) is fitted per user as free-night rebound shrinks. Naps get full credit up to 90 min, then half rate.

### Circadian Phase — Circular Kalman Filter

DLMO (Dim Light Melatonin Onset) estimated at ~2–3 h before habitual sleep onset. Re-entrainment rate-limited to ~1 h/day eastward and ~1.5 h/day westward. Melatonin window = 1 h starting ~2 h after DLMO.

### Key References

| Model | Source |
|---|---|
| Energy (three-process) | Åkerstedt & Folkard, 1995; SAFTE model (Hursh et al. 2004, US 6,579,233 — expired) |
| Sleep Regularity Index | Phillips et al., 2017 |
| Chronotype (MSFsc) | Roenneberg et al. |
| Kalman sleep need | Standard local-level DLM |

Full citations are in-app under **Learn → How Drift calculates**.

---

## 🏗️ Architecture

Drift is built as a multi-module Android project with strict one-way data flow:

```
Source Adapters  →  :core:data (merge/dedupe)  →  :core:engine  →  AnalysisSnapshot
                                                                          ↓
                                               Screens · Reminders · Widget (read-only)
```

### Module Map

| Module | Description |
|---|---|
| `:core:model` | Domain types, contracts (C1–C5), repository interfaces |
| `:core:engine` | Pure Kotlin analysis engine — no I/O, no Android imports |
| `:core:data` | Room + SQLCipher database, DAOs, repositories, merge/dedupe |
| `:source:healthconnect` | Health Connect adapter, permissions, change tokens |
| `:source:sleepasandroid` | CSV backup parser, HC origin mapping |
| `:source:phone` | On-device sleep detection (screen, Sleep API, motion, charging) |
| `:core:background` | WorkManager workers, scheduling, notifications, widget |
| `:core:designsystem` | Manrope typography, colour tokens, motion specs, base components |
| `:core:charts` | Hand-written Compose Canvas charts — no chart libraries |
| `:feature:energy` | Energy screen with vertical drag-to-edit timeline |
| `:feature:sleep` | Debt, need, history, regularity, body clock, editing |
| `:feature:onboarding` | Intro, permissions, first sync, first-use states |
| `:feature:settings` | Sources, schedule, export, backup, methodology |
| `:app` | Wiring, nav graph, manifest, release config |
| `:testing` | Synthetic sleep histories, fake adapters (test-only) |

### Tech Stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 primitives |
| DI | Hilt |
| Storage | Room on SQLCipher (Android Keystore) + DataStore |
| Background | WorkManager + overnight FGS for phone detection |
| Health data | Health Connect (`androidx.health.connect`) |
| Time | `java.time` — UTC instants + zone everywhere |
| Widget | Jetpack Glance |
| Tests | JUnit 5 · Kotest property tests · Robolectric · Roborazzi |
| Build | AGP 8.13 · Gradle 8.14 · version catalog · convention plugins |

---

## 🔒 Privacy

Drift has **no INTERNET permission** in the manifest. This is enforced by CI — the build fails if one ever appears.

There is no:
- Network call of any kind
- Analytics SDK or crash reporter
- Account or login
- Cloud sync

Your sleep data lives in a local **SQLCipher-encrypted database** (key in Android Keystore). Local rolling backups (7 copies) are also encrypted. Export produces a local file you control.

---

## 📡 Data Sources

| Source | How it works |
|---|---|
| **Health Connect** | Sleep sessions, stages, HR, HRV, steps. Backfills 90 days on first run; Changes API thereafter. |
| **Sleep as Android** | Via HC sync, CSV backup import, or tracking-finished broadcast. |
| **Phone detection** | Reconstructs each night from screen, Google Sleep API, steps, charging, alarms. Two-state HMM → Viterbi decode. Morning confirmation card to adjust. |
| **boAt ring** | Ready when the boAt app writes to Health Connect (same adapter interface, zero engine changes). |

All sources are merged and deduplicated. Overlapping episodes resolve by quality tier, then recency. User edits always win.

---

## 🚀 Building

### Prerequisites

- JDK 21 (per-user install, see `scripts/env.sh`)
- Android SDK at `~/Android/Sdk` (platform 36, build-tools 36)
- WSL / Ubuntu 24.04 recommended

```bash
# Set up the environment (required before any Gradle command)
source scripts/env.sh

# Build a debug APK
./gradlew assembleDebug

# Run all checks (lint, dependency rules, placeholder check, no-internet check)
./gradlew driftChecks

# Run tests for a specific module
./gradlew :core:engine:test
```

> 💡 Build individual modules rather than `./gradlew build` to avoid OOM on memory-constrained machines.

### Convention Plugins (`build-logic/`)

| Plugin | Use |
|---|---|
| `drift.jvm.library` | Pure JVM modules (`:core:model`, `:core:engine`) |
| `drift.android.library` | Android library modules |
| `drift.android.compose` | Compose-enabled libraries |
| `drift.android.feature` | Feature modules |
| `drift.android.application` | `:app` |
| `drift.hilt` | Hilt DI |

---

## 🧪 Testing

```bash
# Engine unit + property tests
./gradlew :core:engine:test

# Record screenshot baselines
./gradlew :core:designsystem:recordRoborazziDebug

# Verify screenshots haven't regressed
./gradlew :core:designsystem:verifyRoborazziDebug
```

### Engine Correctness
- **Golden tests:** fixed inputs → pinned outputs per engine version. Output changes require a version bump + changelog entry.
- **Property tests:** debt ≥ 0 always · less sleep never lowers debt · need within physiological bounds · same input → same output · timezone-shift invariance.
- **Scenario fixtures:** steady sleeper · chronic short sleeper · weekend catch-up · new parent (fragmented) · rotating shift worker · east/westbound travel · missing week · duplicate sources · DST transitions (both directions).

---

## 📐 Design System

Font: **Manrope** (SIL OFL 1.1). Near-black dark-first palette with violet as the single data/interaction accent.

| Role | Dark | Light |
|---|---|---|
| Background | `#06070C` | `#F6F5FB` |
| Surface (cards) | `#15161F` | `#FFFFFF` |
| Primary (violet) | `#B09BFF` | `#5B3FD6` |
| Energy (amber) | `#FFB84D` | `#9A5200` |
| Melatonin (teal) | `#55D1DE` | `#006C7A` |
| Debt (coral) | `#FF9272` | `#B8481F` |
| Success (green) | `#6FD9A0` | `#1B7A48` |

Charts are hand-written Compose Canvas — no chart libraries. All animations honour `Remove animations`. All charts have TalkBack descriptions. WCAG AA contrast enforced by automated tests.

---

## 🗂️ Data Provenance

Every number in Drift carries a provenance level:

| Level | Meaning |
|---|---|
| 🟢 **Measured** | Recorded directly by a sensor or wearable |
| 🔵 **Derived** | Computed deterministically from measured data |
| 🟣 **Estimated** | Model output (sleep need, DLMO, energy curve) |
| ⬜ **User-entered** | Manually entered or confirmed by you |

Provenance is surfaced in TalkBack descriptions and in **"How this is calculated"** explanation sheets (tap the `?` icon next to any metric). Confidence bands are not shown on-screen — they're in the explanation only.

---

## 📄 License

MIT © 2026 Krish Gopi

---

<div align="center">

**Drift** · Built offline, for humans · No account needed, ever

*"Your energy, explained."*

</div>
