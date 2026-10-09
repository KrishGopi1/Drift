# Onboarding

Full-screen flow, no bottom nav. Progress dots at the top (8 dp dots, 8 dp apart, current is `primary` and 24 dp wide), Back arrow from step 2.
Each step: illustration-free, a 40 dp icon in a tinted circle, `headline` title, `body` text, content, a primary `DriftButton` pinned at the bottom (and optional text button).

## Steps
1. **Welcome**: "Know your energy before the day starts." Three bullet rows (energy schedule, sleep debt, body clock). Primary "Get started". Line: "Everything stays on your phone."
2. **How it works**: three short cards (Reads sleep from your phone or apps; Learns your sleep need; Plans your day), each with a `ProvenanceChip` demonstrating Measured / Estimated, and a one-line explanation of confidence.
3. **About you**: usual wake and bed time (time pickers), work or school days (chips), do you use an alarm (switch). Optional morning/evening question (chronotype, 5 options as `SegmentedControl` stacked).
4. **Choose sources**: row per source with switch (Health Connect, Sleep as Android, Phone detection) and short explanation of accuracy tier.
5. **Permissions**: one `PermissionPanel` at a time per source, with `points` listing what is read and that it never leaves the phone; "Not now" is always available.
6. **Import progress**: determinate linear progress (primary, 6 dp tall pill) with text "Reading 90 days of sleep... 34 nights found"; cancel is available.
7. **First reveal**: the energy curve draws in (1.1 s) inside a card with "This is your first energy schedule", Estimated chip; button "Open Drift".

## Special states
- **Health Connect missing**: step 5 shows `SourceProblemPanel` ("Health Connect isn't installed") with fix `InstallHealthConnect` (or Update); alternative button "Use phone detection only".
- **Partial grants**: panel lists granted vs missing permissions with a check or warning icon each; continue allowed; features that need the missing item say so on their screens.
- **Zero nights imported**: step 6 ends in an `InsufficientDataPanel` (`NO_NIGHTS_YET`, counter shows nights needed) and the reveal still shows a prior-only curve flagged "Based on typical sleep".
- Loading: progress step above. Error: ErrorPanel with retry for import failures.

## Motion
Steps slide horizontally 280 ms standard (fade only when reduced). Dots resize 160 ms. Reveal curve draw-in 1.1 s.

## Accessibility
Each step title is a heading and receives focus on entry. Progress announced "Step 3 of 7". Permission rationale readable before the system dialog. Everything usable with 200% font (content scrolls, the button stays pinned above the nav bar).
