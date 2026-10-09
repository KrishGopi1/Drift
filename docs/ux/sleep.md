# Sleep

Purpose: sleep debt, sleep need, regularity and tonight's recommended times.

## Layout
1. `DriftTopBar` "Sleep", subtitle "Last 14 nights".
2. **Debt hero card**: `DurationHero` (label "Sleep debt", value `sleepDebt.value`, chip from `sleepDebt.provenance`), supporting text = band word ("Moderate") from `DebtBand` with matching colour chip: LOW success, MODERATE warning, HIGH error (always with text). If estimated, `DurationConfidenceBand` underneath. Whole card is a button opening the why sheet (`SLEEP_DEBT`).
3. **Contribution card**: title "Last 14 nights", `DebtBarsChart` (180 dp, legend on). Caption "Up adds to debt, down repays it".
4. **Need card**: `DurationHero` "Sleep need" with `DurationConfidenceBand(sleepNeed)`; caption "Personalised from N nights" or "Typical for your age until we have N more nights" (`nightsUntilPersonalNeed`).
5. **Regularity card**: `MetricHero` SRI (7-day) value, unit "of 100"; second row 14-day SRI; `RegularityTrendChart` (compact, 120 dp) under it when trend has 7+ points; tapping opens the Body clock tab scrolled to regularity.
6. **Tonight card** (`Recommendations`): two columns "Bedtime" and "Wake" in `display` style times with chips; below "Sleep opportunity 8 h". Trade-off message when present (primaryContainer strip).

## Motion
Hero count-up 900 ms; bars grow 480 ms staggered; cards stagger 45 ms.

## States
- Loading: skeleton.
- FirstUse: panel.
- Insufficient `TOO_FEW_NIGHTS` (< 5 nights): need card shows prior value (Estimated, low confidence); debt card and bars replaced by `InsufficientDataPanel` with counter "N more nights needed". Tonight card still shows prior-based times.
- Insufficient `TOO_MANY_MISSING_NIGHTS` (`sleepDebt == null`, debt unavailable after 3+ missing nights): debt hero replaced by the panel; bars still show with gaps (rings) so the user sees which nights are missing, each gap a tappable "Add a night" via History.
- SourceProblem / Error: panels, as in README.
- Imputed nights: dashed bars and legend entry "Estimated night".

## Accessibility
Debt hero: "Sleep debt: 3 hours 20 minutes, Derived. Moderate." Bars chart description lists net change, biggest increase and repayment, imputed and missing counts.
