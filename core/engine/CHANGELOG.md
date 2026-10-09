# Engine changelog

Every output-affecting change bumps `ENGINE_VERSION` and needs a line here plus regenerated golden files
(`src/test/resources/golden/<version>.properties`).

## 1.1.0
- Energy potential (model 4.6b): 0-100 score from sleep debt, potential = 100 * exp(-debt / 24 h), with
  Super/Great/Good/Fair/Low labels and a per-night trend over the debt window (SAFTE-style reservoir, Hursh 2004).
- The energy curve now carries the day's debt: level loses 0.5 * (100 - potential) points, more at low levels,
  so debt lowers peaks and deepens dips. Yesterday uses that day's debt, tomorrow the projected debt after tonight.
- Catch-up projection: following the recommended schedule, projected debt for 14 nights and nights until Great.
- Sleep-quality check-ins (`CheckIn.SleepRating`) are accepted and ignored by the energy calibration.

## 1.0.0
- First release: preprocessing, sleep need (Kalman with censored alarm nights), leaky debt, SRI/chronotype,
  circular-Kalman DLMO with re-entrainment, three-process energy curve, recommendations, explanations,
  data-sufficiency tiers, replay CLI.
- Debt is measured against the need established before the 14-day window (falls back to the current need when
  fewer than 5 informative nights precede the window).
- Nights with 60+ min of waking inside them are "disturbed" and treated as censored for need.
