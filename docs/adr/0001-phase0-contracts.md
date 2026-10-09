# ADR 0001: Phase 0 contracts and build skeleton

- Status: Accepted (frozen 9 Oct 2026)
- Owner: A0

## Decision
- Contracts C1–C5 live in `:core:model` (pure Kotlin) as specified in plan section 3, plus repository
  interfaces (`model/repository`) so features and background code never depend on `:core:data` internals.
- Additions beyond the plan text: `EngineInput.annotations` (free-day / alarm marks) and
  `EngineInput.checkIns` (calibration, 4.6); `AnalysisSnapshot` carries `engineVersion`, `inputHash`,
  `computedAt`, `debtBand`; `sleepDebt` is nullable for the "not enough data" case (4.3);
  `EnergyCurve` holds yesterday/today/tomorrow `DayCurve`s for the day switcher (6.2);
  `EngineState` is an opaque versioned `Map<String, Double>` so storage need not know engine internals.
- `SourceFix` (sealed) describes user-actionable fixes; `:app` maps each to an intent/permission request,
  so features stay free of source-module and permission-API dependencies.
- Toolchain: AGP 8.13.2, Kotlin 2.2.21, KSP 2.2.21-2.0.5, Gradle 8.14.3, JDK 21, compile/target SDK 36.

## Consequences
Changes to anything in `:core:model` need a new ADR reviewed by affected owners.
