# Energy (home)

Purpose: answer "how will my energy go today and what should I do next". First tab, opens by default.

## Layout (top to bottom, gutter 20 dp, 16 dp between cards)
1. `DriftTopBar` title "Energy", subtitle = long date of the selected day (e.g. "Friday 9 October"). Action: info icon button "How this works" opens methodology in Settings.
2. `SegmentedControl` ["Yesterday", "Today", "Tomorrow"], default Today. Changing it re-runs the curve draw-in (key = date); the headline card cross-fades 160 ms.
3. **Headline card** (`DriftCard`, accent tone for Today): label "Right now" (label style), phase name in `display` style (e.g. "Morning peak") coloured `energy` for peaks, `primary` for dip, `melatonin` for melatonin window; one line `body` below with the next event: "Dip starts around 13:30 (in 2 h 10 min)". Provenance chip Estimated + confidence level text. For Yesterday/Tomorrow the label is the date and the phase line is replaced by "Peak 10:30, dip 14:15".
4. **Curve card**: `EnergyCurveChart` height 220 dp, legend on. `now` = clock instant for Today, null for other days. Tapping the card opens the why sheet for `MetricKey.ENERGY`.
5. **Next up row**: two `DriftListRow`s in one card: "Next peak" 19:00, "Next dip" 14:15 (times from `EnergySegment.extremumAt`), each with Estimated chip. If `tooFlatForDip`: row says "No reliable dip today" with sub-line "Your energy stays fairly level".
6. **Habit cards** (`HabitCard` list, horizontal pager is NOT used; plain vertical cards): icon, title ("Last caffeine"), time window (`DriftFormat.clock` of window start/end), basis citation as a caption link to methodology. Max 4 shown, "Show all" text button for more. Habits already past are dimmed (onSurfaceMuted) and sorted after upcoming.
7. Optional **nap card** when `Recommendations.nap != null`: window, max duration, reason.
8. Optional **trade-off card** when `TradeOff != null` (primaryContainer): the message and a button "Show me the later bedtime".
9. Calibration footer: "Calibrated with N check-ins" (caption) with a text button "Check in now" (opens the 1 to 5 energy rating sheet). Provenance User-entered for check-ins.

## Motion
Stagger cards 45 ms; curve draws in over 1.1 s starting after card 3 enters; now-marker pulses 1.8 s forever (stops when screen is not resumed); phase text in the headline crossfades 160 ms when the phase changes while the screen is open.

## Special states
- **Prior-only curve** (`Sufficiency.tier == PRIORS_ONLY`): headline card gets a banner row "Based on typical sleep for now" with an Estimated chip and a `DriftButton` text "Improve it" leading to Settings > Sources. Confidence text is "Low confidence".
- **Shift-work mode** (`CircadianEstimate.shiftWorkMode`): banner at top of the curve card "Shift-work mode: schedule follows your last sleep". Curve axis starts at the wake time, so it may cross midnight.
- **Travel adjustment** (`Reentrainment != null`): banner "Adjusting after eastward travel, about 2 days left" with `windDown` colour and Info icon. Banner is a button opening the why sheet (`CIRCADIAN_SHIFT`).

## States (C5)
- Loading: top bar + segmented control real, then `LoadingSkeleton`.
- FirstUse: FirstUsePanel ("N steps left") with button into onboarding.
- Insufficient: not used for Energy (priors always give a curve). If it appears (`NO_NIGHTS_YET`), the panel is shown above a prior-only curve card.
- SourceProblem: panel at the top, curve of last known data below dimmed 60% with a "Last updated" caption.
- Error: ErrorPanel, retry reloads the snapshot.

## Accessibility
Headline card is one merged node: "Right now: morning peak. Next, afternoon dip around 14:15, in 2 hours 10 minutes. Estimated, medium confidence." The chart reads its full description (`energyCurveDescription`). Day switcher is a radio group. Pulse is decorative.

## Measure on device
Headline type size, banner order, how the reference app presents the day switcher and habit card density: see measurement-todo.md.
