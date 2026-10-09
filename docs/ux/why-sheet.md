# "Why this changed" sheet

Opened by tapping any metric hero, chart card or the info icon next to a number. Uses `DriftBottomSheet(title = ...)`.

## Layout
- Title: the metric name ("Sleep debt"). Close icon button top right; swipe down, scrim tap and Back also dismiss.
- **Headline** (`title` style): `Explanation.headline`, e.g. "Down 40 min since yesterday".
- **Cause list**: one row per `Cause`: small icon by `CauseKind` (moon for night entered/left window, refresh for need re-estimated, plug for source added, pencil for user edit, clock for circadian shift, bolt for check-in calibration), `Cause.text` in `body`, date caption ("Tue 6 Oct") and signed delta when present (`DriftFormat.signedDuration`). Rows are plain, not buttons, except causes with a date, which open that night.
- Provenance chip for the metric and, for Estimated, `ConfidenceBand`.
- Footer text button "How this is calculated" opens Settings > Methodology at the metric's citation.

## States
- First day (`FIRST_RUN`): single cause "This is your starting estimate based on typical sleep. It will adapt as nights are recorded." No delta.
- No prior snapshot / no explanation for the metric: headline "Nothing has changed yet" and the body "Check back after your next night's sleep." No empty list.
- Loading: 3 skeleton lines. Error: inline ErrorPanel in the sheet.

## Motion
Sheet slide 280 ms standard; rows fade in with 45 ms stagger (max 8). None under reduced motion.

## Accessibility
Sheet is announced by its title; focus starts on the title; list rows are single focus stops.
