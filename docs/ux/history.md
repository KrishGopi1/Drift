# History

Purpose: review duration and timing across week, month or 3 months, and open any night.

## Layout
1. `DriftTopBar` "History".
2. `SegmentedControl` ["Week", "Month", "3 months"] (stacked at 130%+ font scale).
3. **Summary card**: three `MetricHero`-style small stats in a row (wraps to a column at large font): "Average sleep" (h min), "Average bedtime", "Average wake". Each has a Derived chip. Under it caption "N of M nights recorded".
4. **Duration card**: `SleepDurationChart` with `need` line, 180 dp. Bars below the need are `debt` coloured.
5. **Timing card**: `SleepTimingChart` 220 dp, `onNightClick` opens Night detail; selected night highlights (energy colour).
6. **Night list**: `DriftListRow` per night, newest first: title "Thu 8 Oct", subtitle "23:05 to 07:12, 8 h 7 min", provenance chip (Measured, or User-entered when `userEdited`), chevron. Nights with no data are rows reading "No data" with action "Add a night" (opens edit with empty form). This list is the keyboard and TalkBack route to every night.

Range switch: charts cross-fade 160 ms then bars grow 480 ms.

## States
- Insufficient `NO_NIGHTS_YET`: panel only. Fewer nights than the range: chart shows only what exists with gap rings for the rest; no panel.
- Gaps shown as gaps (rings), never interpolated; summary averages exclude them and say so.
- SourceProblem: panel above the (cached) charts.

## Accessibility
Charts speak `sleepTimingDescription` / `sleepDurationDescription`. Tap on the chart is a convenience; the list is the accessible equivalent. Segmented control is a radio group.
