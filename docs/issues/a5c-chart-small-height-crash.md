# a5c: charts crash at 200% font when the chart is short

`SleepTimingChart` (SleepTimingChart.kt:137) and `RegularityTrendChart` (line 67) call `coerceIn(0f, height - axisH - labelH)`
and throw `IllegalArgumentException: Cannot coerce value to an empty range` when the font scale makes axis labels taller than
the plot (2x font, 120 dp / 220 dp charts). Proposed: guard with `coerceAtLeast(0f)` on the upper bound. Workaround in
feature/sleep: chart heights are multiplied by the font scale (`scaledChartHeight`). Owner: A4.
