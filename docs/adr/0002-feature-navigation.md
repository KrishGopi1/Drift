# ADR 0002: Feature navigation and cross-feature seams

- Status: Accepted (9 Oct 2026)
- Owner: A0

Feature modules may not depend on each other, so each one exposes Navigation Compose entry points
and receives cross-feature actions as callbacks. `:app` owns the `NavHost`, the bottom bar and the
`SourceFix` handler.

| Module | Public entry point (package `app.drift.feature.<name>.navigation`) |
| --- | --- |
| onboarding | `const val ONBOARDING_ROUTE`; `fun NavGraphBuilder.onboardingGraph(onFinished: () -> Unit, onFix: (SourceFix) -> Unit)` |
| energy | `const val ENERGY_ROUTE`; `fun NavGraphBuilder.energyScreen(onExplain: (MetricKey) -> Unit, onOpenNight: (EpisodeId) -> Unit, onFix: (SourceFix) -> Unit)` |
| sleep | `SLEEP_ROUTE`, `HISTORY_ROUTE`, `BODY_CLOCK_ROUTE`; `fun NavHostController.navigateToNight(id: EpisodeId)`; `fun NavHostController.navigateToWhy(metric: MetricKey)`; `fun NavGraphBuilder.sleepGraph(navController: NavHostController, onFix: (SourceFix) -> Unit)` (registers sleep, history, night detail/edit, add-night, body clock, and the "why this changed" sheet as a dialog/bottom-sheet destination) |
| settings | `const val SETTINGS_ROUTE`; `fun NavGraphBuilder.settingsGraph(navController: NavHostController, onFix: (SourceFix) -> Unit, onResetComplete: () -> Unit)` |

- Bottom navigation (Energy, Sleep, Body clock, Settings) and top-level scaffolding live in `:app`.
  Feature screens draw their own top bar content but not the bottom bar.
- Feature screens wrap content in `ScreenStateHost` and get `DriftTheme` from `:app`.
- Notification/widget deep links (`app.drift.extra.DESTINATION` = `energy` | `edit_sleep`, plus
  `app.drift.extra.EPISODE_ID`) are resolved by `:app` into these routes.
- Calibration in v1.0: 1–5 energy check-ins only (on the Energy screen). The reaction-time test waits on
  open decision 4 in the plan.
