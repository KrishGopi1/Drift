# Items that need on-device measurement against the reference app

The reference app was not available; these values are chosen from public material and judgment. Measure with the reference phone (open decision 3), light and dark, default and largest font, 60 fps screen recording.

1. Screen gutters, card padding, gap between cards, card corner radius (we use 20 / 16 / 16 / 16 dp).
2. Type sizes of the hero number, phase headline and chart labels; whether the reference app uses a weight or a size change to emphasise the current phase.
3. Energy chart height relative to screen, stroke width, band opacity, and whether bands are drawn behind or in front of the curve.
4. Now-marker style and pulse timing; whether the marker shows a label.
5. Curve draw-in duration and easing; hero count-up duration; card stagger step and distance.
6. Day switcher form (segmented vs swipe) and its default.
7. Bottom navigation: item count and order, label size, indicator shape, height.
8. Habit card density, icon size, and whether they scroll horizontally.
9. Debt hero: unit formatting, band wording, colour semantics, and how unavailable debt is shown.
10. Bar chart: bar width, corner radius, zero-line treatment, how missing nights are drawn.
11. Bottom sheet corner radius, scrim opacity, drag behaviour and the "why" row layout.
12. Onboarding step count, progress indicator, and transition style.
13. Widget sizes offered, tap target, stale-state treatment.
14. Reduced-motion behaviour in the reference app (what it removes) to compare with ours (everything snaps).
15. Contrast of the reference app's accent colours (to confirm ours are at least as accessible; ours are listed in tokens.md).
16. Real-device checks for us: font rendering of Manrope at 200%, TalkBack reading order on every chart, nav label cap at 130%, performance of Canvas charts (< 1% janky frames).
