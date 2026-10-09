# Night detail and edit

Reached from History chart or list, or the morning notification.

## Layout
1. `DriftTopBar` title = date ("Thursday 8 October"), Back, action "Edit" (text button).
2. **Summary card**: `DurationHero` total sleep, chips: Measured/Estimated by quality tier, "User-edited" badge (User-entered chip) when `userEdited`, plus source row "Health Connect, wrist device" (from `SourceRef`).
3. **Times row**: bedtime and wake time in `display` style; "In bed" row if `inBed` present.
4. **Stage card** (when `stages != null`): `StageChart` 180 dp and a 4-row legend list with minutes and percentage per stage. No stages: a short note "This source did not record stages" (no chart).
5. **Quality card**: completeness and detection confidence as plain words ("Complete", "Some gaps", "Best guess from phone use") plus flags: IMPLAUSIBLE_DURATION ("Unusually short or long, not used in your numbers unless you confirm"), SOURCE_CONFLICT.
6. **Conflicting sources card** (flag `SOURCE_CONFLICT`): list of the competing sources with times, the chosen one marked "Used", button "Use this one instead".
7. **Actions list** (`DriftListRow`s): "Add a nap", "Mark as free day" (switch row), "Delete this night" (error colour text, confirm dialog).

## Edit mode
Same screen: times become two tappable rows opening a time picker (Material time picker, 5-minute step), validation text under the row (end after start, under 20 h). "Save" primary button pinned at the bottom; "Cancel" text button. Saving shows the User-entered chip and sets `userEdited`. Delete confirm: dialog "Delete this night? Your sleep debt and need will be recalculated." with Cancel / Delete.

## States
Loading skeleton; Error with retry; Insufficient not used; SourceProblem not used (data is local). Conflicting-sources and user-edited are variants of Ready.

## Motion
Stage chart draw-in 1.1 s; shared-element transition from the tapped list row/bar to the summary card, 280 ms standard (no transition under reduced motion).

## Accessibility
Stage chart description lists totals and awakenings. Time pickers announce the selected value. Destructive action needs an explicit confirm.
