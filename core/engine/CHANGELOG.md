# Engine changelog

Every output-affecting change bumps `ENGINE_VERSION` and needs a line here plus regenerated golden files
(`src/test/resources/golden/<version>.properties`).

## 1.0.0
- First release: preprocessing, sleep need (Kalman with censored alarm nights), leaky debt, SRI/chronotype,
  circular-Kalman DLMO with re-entrainment, three-process energy curve, recommendations, explanations,
  data-sufficiency tiers, replay CLI.
- Debt is measured against the need established before the 14-day window (falls back to the current need when
  fewer than 5 informative nights precede the window).
- Nights with 60+ min of waking inside them are "disturbed" and treated as censored for need.
