# Visual refresh: near-black, one big thing per screen

Owner: A4. Studied from RISE's public store-listing screenshots (allowed by plan section 1, rule 1) for layout and
mood only. Colours, type (Manrope), icons, copy and every drawing below are our own; no RISE asset, string or
icon is reused. Implementers build from this file, not from the screenshots.

## What we took (behaviour and layout)
1. **Near-black canvas.** Screens sit on an almost-black background; cards are a slightly lighter charcoal with no
   border. Colour is reserved for data (the curve, the debt arc) and the one accent (violet).
2. **One hero per tab.** Each main tab leads with one large graphic that fills the width: the energy timeline on
   Energy, the debt gauge on Sleep. Supporting facts sit in compact tiles under it.
3. **Energy runs top to bottom.** The day is a vertical timeline: time goes down the left edge, energy is how far right
   the line sits. The viewport is a fixed-height window that scrolls inside the page and opens on "now".
4. **Flags, not legends.** Peaks and dips are named with a small pointed flag on the line instead of a legend.
   Habit times sit on the line as a dot with a two-line label.
5. **Big number in a disc.** Sleep debt is a large number inside a dark disc that overlaps the right edge of the gauge.

## Tokens (changes only; full table in tokens.md)
| Role | Dark | Light |
|---|---|---|
| background | #06070C | #F6F5FB |
| surface | #15161F | #FFFFFF |
| surfaceRaised | #20212C | #EFEDF7 |
| outline / outlineSubtle | #6E7187 / #2A2B38 | #7A84A3 / #DDDAEA |
| onSurface / onSurfaceMuted | #F2F3F8 / #A4A7BC | #12182B / #4B5472 |
| primary / onPrimary | #B09BFF / #140A33 | #5B3FD6 / #FFFFFF |
| primaryContainer / on | #5B3FD6 / #FFFFFF | #E6E0FF / #24137A |

Cards: radius lg (24 dp), no border in dark. Selected segment and nav item use primaryContainer (a filled violet pill).

## Energy timeline (`EnergyTimelineChart`, core/charts)
- Rounded panel (surface, radius lg), fixed viewport height 460 dp, content scrolls vertically inside it.
- Covers the waking part of the day: from the end of the first Sleep segment to the start of the last one.
  64 dp per hour. Hour labels on the left (caption, muted), a short tick at each half hour.
- Two faint guide lines (outlineSubtle) echo the curve 28 dp to the left and right, which gives the
  "lane" look; the curve itself is 3 dp, coloured by phase (energy for peaks, primary for dip, windDown, melatonin).
- X position: level 0 sits at 30% of the plot width, level 100 at 85%, so the line never touches the labels.
- Peak and dip flags: pill (primaryContainer, onPrimaryContainer text "Peak"/"Dip") with a pointed edge touching
  the line at `extremumAt`, placed on the left of the line.
- Habit markers: 10 dp dot on the line at the window start, label to the right: time range (caption, onSurface) over
  the habit name (caption, bold). Labels that would overlap the previous one are skipped (the cards below list all).
- Now: 1 dp line in onSurface at 70% from the end of the hour labels to the right edge, with a dot at its left end. Today opens scrolled so "now"
  sits one third down the viewport. Other days open at the top.
- Accessibility: one node with the existing `energyCurveDescription`; the inner scroll is not a separate stop.
- Reduced motion: no draw-in, no pulse.

## Energy screen
1. Top bar and day switcher (segmented control, selected segment filled violet).
2. Headline card (default tone, phase in headline style), unchanged content.
3. Timeline panel (above). Tapping it opens the why sheet.
4. Next peak / next dip card (unchanged).
5. Tonight, habits, nap, trade-off and check-in cards stay as they are, in that order.

## Sleep debt gauge (`DebtGauge`, core/charts)
- Panel 260 dp tall, surfaceRaised, radius lg, taking 72% of the width.
- Three thin concentric arcs bulge out of the panel's right edge (outlineSubtle) as a scale.
- The debt arc follows the innermost bulge, 6 dp, gradient from windDown (top) to energy (bottom). Its
  length is debt / 12 h of the full arc, starting at the bottom. 0 h draws a short success-coloured stub.
- The disc (background colour, 168 dp) overlaps the panel's right edge, centred vertically: the number in hero
  style with unit "h", and "Sleep debt" under it.
- Above the gauge on the left: band pill (Low / Moderate / High) and the provenance chip.

## Sleep screen
1. Top bar: "Sleep", subtitle "Last 14 nights", History action.
2. Debt gauge with the confidence range line under it.
3. "Last night" card: the newest contribution row (slept vs needed, +added / repaid in debt/repaid colours).
   Tapping it opens the night, or adds one when there is no data.
4. Contribution, need, regularity, tonight cards stay below.

## October 2026 structure (RISE as direct reference, plan section 1 amended)
Tabs: **Sleep** (home) · **Energy** · **Progress** · **Learn**. Settings opens from the profile button on every tab
header; Body clock and the methodology open from Learn and the Sleep tab.

- **Sleep (home):** "My sleep" over the 14-night window ("Oct 1 – 14"). Debt gauge with Great/Super night counters,
  band and confidence; tiles "+0.4h debt since yesterday" and "Energy potential 97% >" (opens Energy); LAST NIGHT
  card (total sleep, times, source, provenance; opens the night); TODAY'S ENERGY mini curve (opens Energy); need,
  regularity and tonight cards.
- **Energy:** "My energy" with "Great ⚡ 85% ?" (potential label, score, explain). One continuous timeline from
  yesterday 18:00 to tomorrow 12:00 at 60 dp/hour: last night's sleep is a block with drag handles on both edges
  (5-minute steps, 30 min–16 h, never past now; TalkBack gets earlier/later actions). While dragging the engine reruns
  as a preview and the curve and potential update; release saves a user edit. Tonight's recommended sleep is a dashed
  block. Habits, melatonin window and nap window sit on the line as icon badges. A pull-up "Up next" panel holds
  next peak/dip, tonight, habits, nap, trade-off and check-in.
- **Progress:** "My progress" with Times / Debt / Quality. Times: one column per night (named by the evening it
  started), time axis on the right, sleep blocks with a sleep mark and sun, moon target at the melatonin window, a
  "Debt-free schedule · GREAT IN N DAYS" card and "All sleep times". Debt: line chart with hours axis and "All sleep
  debt". Quality: 1–5 morning rating prompt, dot chart and "All sleep quality".
- **Learn:** seven short original articles plus Body clock and How Drift calculates.

Icons: Material Symbols Rounded (Apache 2.0) plus drawn tab icons (ring for Sleep, stacked lines for Energy) and the
"zz" sleep mark. Palette: background #000000, cards #1C1C23, violet #6B3CF0 container and #A98BFF text accent.
