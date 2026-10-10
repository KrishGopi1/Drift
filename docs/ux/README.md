# Drift UX specs

Specs written from public material about the reference app (store text, help centre, marketing) plus the plan. Implementers build from these pages and from
`core/designsystem` and `core/charts`; they must not look at the reference app itself. Start with [tokens.md](tokens.md).

| Page | Screen |
|---|---|
| [onboarding.md](onboarding.md) | Onboarding |
| [energy-home.md](energy-home.md) | Energy (home) |
| [sleep.md](sleep.md) | Sleep |
| [history.md](history.md) | History |
| [night-detail.md](night-detail.md) | Night detail and edit |
| [body-clock.md](body-clock.md) | Body clock |
| [why-sheet.md](why-sheet.md) | "Why this changed" sheet |
| [settings.md](settings.md) | Settings |
| [widget.md](widget.md) | Widget |
| [measurement-todo.md](measurement-todo.md) | Items needing on-device measurement against the reference app |

## Rules shared by every screen
- Wrap the content in `ScreenStateHost(state, onRetry, onFix, onStartSetup) { model -> ... }`. All six C5 states are rendered by it identically:
  Loading (3 skeleton cards, announced "Loading"), FirstUse (panel with "Continue setup"), Insufficient (title, explanation, big "N more nights needed" counter),
  Ready (the screen), SourceProblem (message + one fix button named by the `SourceFix`), Error (message + "Try again" when retryable, polite live region).
- Screen layout: `Column` scrolling vertically, `DriftTopBar` on top, gutter 20 dp, cards 16 dp apart, `DriftBottomNavBar` at the bottom on the four main screens.
- Ready content enters with `Modifier.staggeredEntrance(index)` on each card (first load of the screen only, not on refresh).
- Every number sits next to a `ProvenanceChip` or inside a hero/row that shows one. Estimated numbers also show `DurationConfidenceBand` or a "+/-" text.
- Tapping any metric opens the "Why this changed" sheet (see why-sheet.md).
- 24 h vs 12 h follows `AppSettings.use24HourClock` (null = system) and is passed to charts as `use24h`.
- TalkBack: one focus stop per card (merged), cards with an action announce "button"; charts expose their description string and the data is also
  available as a list nearby (night list, habit cards).
- Back stack: the four tabs are peers (no back between them); Back from the first tab exits. Detail screens have a top-bar Back.
- Font scale 200%: no truncation of essential text, rows grow, hero numbers may wrap onto two lines.
