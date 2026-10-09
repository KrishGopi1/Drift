# A5b: Energy screen needs navigation callbacks beyond ADR 0002

`energyScreen(onExplain, onOpenNight, onFix)` has no way to reach onboarding or Settings, but the UX spec needs both.

- First-use panel "Continue setup" needs a route into onboarding. Workaround: optional `onStartSetup: () -> Unit = {}` on `energyScreen`.
- Prior-only banner "Improve it" should open Settings > Sources. Workaround: optional `onOpenSources: (() -> Unit)? = null`; when null it opens the energy why-sheet instead.
- The top-bar "How this works" action (methodology in Settings) is omitted for the same reason.
- `ScreenState.SourceProblem` carries no last-known model, so the spec's "dimmed curve below the panel" is not possible; the panel is shown alone. The view model never emits SourceProblem (none of its three repositories reports source status).
- Spec says Energy never shows Insufficient (priors always give a curve), while the task asked for sufficiency mapping to FirstUse/Insufficient. Implemented: every tier with a curve is Ready; only an empty curve maps to FirstUse (priors tier) or Insufficient.

Proposal for A0: add the two optional callbacks to ADR 0002 and have `:app` wire them.
