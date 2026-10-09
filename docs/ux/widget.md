# Widget (Glance)

Sizes: 2x2 (curve only), 4x2 (curve + text). Glance cannot host Compose Canvas, so the widget draws a bitmap with the same geometry:
use `EnergyGeometry.normalizedPoints(dayCurve, width, height)` and `EnergyGeometry.levelAt` from `:core:charts`, colours from `DriftDarkColors` / `DriftLightColors`
(selected by the system theme).

## Layout
- Rounded 24 dp container, `surface` colour, 12 dp padding.
- 4x2: left column phase name (`title`, e.g. "Morning peak") and next change line ("Dip in 2 h 10 min", `bodySmall` muted); right 60% is the mini curve (line 2 dp, soft fill, now dot 5 dp).
- 2x2: curve plus a one-line phase name below.
- Tap anywhere opens the Energy tab.

## States
- Fresh: as above.
- Stale (> 12 h since last snapshot): curve drawn at 40% alpha, caption "Open Drift to refresh" in warning colour, no now-dot, phase line replaced by "Updated 14 h ago".
- No data yet: caption "Open Drift to set up".
- No animation (widgets cannot animate); refresh on `AppWidgetManager` update and each time the phase changes.

## Accessibility
Widget content description = `miniEnergyDescription`. Minimum text 12 sp; tap target the full widget.
