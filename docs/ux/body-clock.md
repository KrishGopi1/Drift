# Body clock

Purpose: circadian phase, melatonin window, chronotype, social jetlag, regularity trend.

## Layout
1. `DriftTopBar` "Body clock".
2. **Dial card**: `CircadianRhythmChart` (max 300 dp wide, centred) with `now` and planned sleep window; title above "Your melatonin window"; chips Estimated + confidence.
   Beneath: row "Melatonin onset (DLMO)" 21:15, "give or take 30 min" from its confidence band; row "Wind-down starts" 20:45.
3. **Chronotype card**: label word (Early / Intermediate / Late) in `display`, "Mid-sleep on free days: 04:10", `ConfidenceBand` for MSFsc, caption "From N free nights".
4. **Social jetlag card**: `DurationHero` ("1 h 10 min", label "Social jetlag") with one-sentence meaning.
5. **Regularity card**: `MetricHero` SRI 7 and 14 day; `RegularityTrendChart` 170 dp; onset/wake/midpoint spread rows (min).
6. Travel / shift banner as in Energy.

## States
- Insufficient `NOT_ENOUGH_FREE_NIGHTS`: chronotype and social jetlag cards replaced by the panel with counter; dial and regularity still render when available.
- `circadian.melatoninWindow == null`: dial shows ring only with "No estimate yet" and a `InsufficientDataPanel`.
- Regularity unavailable (`regularityAvailable == false`): regularity card replaced by the panel.
- Loading/FirstUse/SourceProblem/Error as README.

## Motion
Dial arcs fade in 480 ms; DLMO tick appears after; stagger 45 ms between cards.

## Accessibility
Dial speaks `circadianDescription` (onset, wind-down, window, planned sleep, travel/shift state); the rows below repeat the times as text.
