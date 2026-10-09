# Settings

Grouped `DriftCard` sections of `DriftListRow` / `DriftSwitchRow`, separated by `DriftDivider`. Top bar "Settings".

## Sections
1. **Sources and health**: row per `SourceAdapter` with status subtitle (Connected, Needs permission, Not installed), last sync time, and for non-available statuses a trailing text button with the fix label (`SourceFix.actionLabel()`). Row "Phone detection" switch. Row "Import Sleep as Android backup" opens file picker.
2. **Schedule**: usual wake and bed time, work days, alarm usage, shift-work switch, chronotype questionnaire.
3. **Reminders**: one `DriftSwitchRow` per `ReminderKind` with the lead time in the subtitle.
4. **Appearance**: `SegmentedControl` System / Light / Dark (maps to `ThemeMode`), 24-hour clock three-way (System / 12 h / 24 h).
5. **Data**: Export (file), Import, Backup and restore, "Reset all data" (error colour, confirm dialog that requires typing nothing but shows what will be lost).
6. **Methodology**: expandable list of metrics, each with plain-language explanation, parameters used, and citations (text; no links that need network).
7. About: version, licenses (Manrope OFL text), "No account, no network, nothing leaves your phone."

## States
- Revoked permission: source row shows warning icon + "Access removed" + fix button; SourceProblemPanel at top of the section.
- Failed import: ErrorPanel in a sheet showing the reason and "Try another file".
- Loading skeleton for rows; Error with retry. FirstUse/Insufficient: not used.

## Accessibility
Switch rows toggle on the whole row (Role.Switch). Destructive rows need confirmation dialogs. Segmented controls are radio groups.
