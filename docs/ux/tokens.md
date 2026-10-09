# Drift design tokens

Source of truth in code: `core/designsystem` (`DriftTheme.colors / typography / spacing / radii / elevation / strokes / motion`).
Palette and copy are original. Studied for behaviour and mood only (calm, near-black, dark-first); no RISE assets used.
The October 2026 visual refresh is described in visual-refresh.md.

## Colour roles
Dark is the primary design. Light is a full equal.

| Role | Dark | Light | Use |
|---|---|---|---|
| background | #06070C | #F6F5FB | Screen |
| surface | #15161F | #FFFFFF | Cards, nav bar |
| surfaceRaised | #20212C | #EFEDF7 | Sheets, segmented track, nested panels |
| outline | #6E7187 | #7A84A3 | Controls and zero lines (3:1 non-text) |
| outlineSubtle | #2A2B38 | #DDDAEA | Card borders, gridlines, dividers |
| onSurface | #F2F3F8 | #12182B | Primary text |
| onSurfaceMuted | #A4A7BC | #4B5472 | Secondary text |
| primary / onPrimary | #B09BFF / #140A33 | #5B3FD6 / #FFFFFF | Buttons, links, selection, regularity line |
| primaryContainer / on | #5B3FD6 / #FFFFFF | #E6E0FF / #24137A | Selected segment/tab, Peak/Dip flags |
| accentSurface | #251C4A | #EFEBFF | Accent cards |
| energy (warm accent) | #FFB84D | #9A5200 | Energy line, peaks, now dot (text-safe) |
| energyFill | #FFB84D | #F5A524 | Chart fills and bands (not text) |
| melatonin (cool accent) | #55D1DE | #006C7A | Melatonin window text |
| melatoninFill | #55D1DE | #2BB5C6 | Melatonin arcs and bands |
| windDown / Fill | #C3A6FF | #6A3FC4 / #9B7BE6 | Wind-down, Estimated chip |
| success / warning / error | #6FD9A0 / #FFB84D / #FF8A8A | #1B7A48 / #9A5200 / #B3261E | Status |
| debt / repaid | #FF9272 / #6FD9A0 | #B8481F / #1B7A48 | Debt bars |
| stageAwake / Rem / Light / Deep | #FFB07A / #C3A6FF / #7FA1FF / #4A63D6 | #D9822B / #8A63D8 / #6F8CF0 / #2E3FA8 | Hypnogram |

Rules: warm = energy, cool teal = melatonin, violet = wind-down/estimated. Never use colour alone: every colour-coded thing also has a
text label, a glyph, or a position (provenance chips have a glyph, stage rows are labelled, charts have legends).

### Contrast
Measured ratios for the current palette are enforced by `DesignSystemTest.textColoursMeetWcagAa` (every text colour on background,
surface, surfaceRaised and accentSurface at least 4.5:1; outline at least 3:1). Dark lowest: onSurfaceMuted on accentSurface 6.6:1.

All text pairs are at least 4.5:1 (enforced by `DesignSystemTest.textColoursMeetWcagAa`). Fill-only colours (energyFill, melatoninFill,
stage colours) are used for graphics, never for text.

## Typography
Font: **Manrope** (SIL OFL 1.1, `core/designsystem/OFL.txt`), static instances at 400/500/600/700 generated from the official variable font.
Sizes in sp; line heights in sp so they scale. Numbers in hero/display use tabular figures (`tnum`).

| Style | Size/line | Weight | Use |
|---|---|---|---|
| hero | 60/64 | 700, tnum, -1 tracking | Metric hero number |
| display | 40/46 | 700, tnum | Counters ("3 more nights") |
| headline | 28/34 | 600 | Top bar title |
| title | 20/26 | 600 | Section header, panel title, sheet title |
| titleSmall | 16/22 | 600 | Row title, value text |
| body | 16/24 | 400 | Paragraphs |
| bodySmall | 14/20 | 400 | Row subtitle, helper text |
| label | 14/20 | 500 | Buttons, segment text, hero label |
| caption | 12/16 | 500, +0.2 | Chart axes, chip text, nav label |
| unit | 20/26 | 500 | Unit next to hero |

Font scale policy: everything scales to 200%. Three deliberate exceptions to keep layouts intact: bottom-nav labels cap at 1.3x (the icon and
TalkBack name carry the meaning), segmented controls switch to a stacked list above 1.3x, and chart axis text scales but skips labels that no longer fit.

## Spacing (dp)
2 xxs, 4 xs, 8 sm, 12 md, 16 lg, 24 xl, 32 xxl, 48 xxxl. Screen gutter 20. Card padding 16. Gap between cards 16. Min touch target 48 (rows 56).

## Radii (dp)
xs 6, sm 10, md 16 (cards), lg 24, sheet 28 (top corners), pill 999 (buttons, chips, segments).

## Elevation
Dark: no shadows, cards are tonal with a 1 dp `outlineSubtle` border. Light: card shadow 1 dp plus the same border. Sheet 8 dp. Raised 3 dp.

## Strokes (dp)
Hairline/outline 1, chart line 2.5, now-marker 1.5 dashed (6 on / 4 off), focus ring 2, icons 2 (24 dp grid).

## Motion
All animations snap to their end state when reduced motion is on (`DriftTheme.reducedMotion`, from the system "Remove animations"
= animator duration scale 0, observed live; tests may force it).

| Token | ms | Easing | Use |
|---|---|---|---|
| instant | 90 | standard | Press feedback |
| short | 160 | standard | Selection fade (segments, tabs) |
| medium | 280 | standard | Sheet content, expand/collapse |
| long | 480 | decelerate | Card entrance, bar growth, dial fade |
| drawIn | 1100 | emphasized | Curve and line draw-in (left to right clip) |
| countUp | 900 | emphasized | Hero number from 0 |
| pulse | 1800 loop | decelerate | Now-marker ring (radius 7 to 16 dp, alpha 0.45 to 0) |
| shimmer | 1400 loop | linear | Skeleton highlight sweep |
| stagger | 45 per item, max 8 | | Card entrance delay |

Easings (cubic-bezier): standard (0.2, 0, 0, 1), decelerate (0, 0, 0, 1), accelerate (0.3, 0, 1, 1), emphasized (0.05, 0.7, 0.1, 1).
Card entrance: fade 0 to 1 and rise 16 dp. Shared-element card to detail: 280 ms standard, container transform; skipped when reduced.

## Provenance chips
Measured (filled circle, success colour), Derived (diamond, primary), Estimated (dashed ring, windDown), User-entered (square, muted). Pill, 12 sp caption text,
tinted fill 14% and border 55%. Spoken as "Measured: recorded directly by ..." etc. Always shown next to a hero and in any row that shows a number.
