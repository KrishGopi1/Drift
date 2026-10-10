# Data sources

Drift reads sleep from several sources, merges them on the device and analyses one combined timeline.
Nothing is uploaded anywhere.

| Source | Module | What it provides |
| --- | --- | --- |
| Health Connect | `:source:healthconnect` | Sleep sessions with stages, heart rate, resting HR, HRV (RMSSD), steps, exercise |
| Sleep as Android | `:source:sleepasandroid` | Its Health Connect sync, CSV backup import, and a broadcast when tracking finishes |
| Phone detection | `:source:phone` | Sleep reconstructed from phone signals when you have no wearable |
| Manual entry and edits | `:core:data` | Nights you add, confirm or drag to adjust in the app |

## Merging and priority

Overlapping episodes from different sources are resolved per time span:

1. **Your edits always win.**
2. Then the higher **quality tier** (ring > wrist wearable > phone app > phone-inferred).
3. Then the most recent record.

Fragments of one night separated by less than 45 minutes awake are merged into one night. Each night also gets
a quality weight from its tier and completeness; the engine uses it as observation precision (see
[science.md](science.md#data-quality)).

## Health Connect

- The first run backfills up to 90 days. After that Drift reads only changes, using Health Connect change tokens.
  If a token expires, it re-reads the last 30 days.
- Each record's data origin (the package that wrote it) maps to a quality tier through the origin table in
  `:source:healthconnect`. Unknown packages get a conservative default tier.
- On Android 9-13, Health Connect is a separate app from the Play Store; Drift prompts you to install it.
  On Android 14+ it's built in.

### Permissions

| Permission | Used for | If you deny it |
| --- | --- | --- |
| Read sleep | Sleep sessions and stages | Phone detection and manual entry still work |
| Read heart rate, resting HR, HRV | Sleep quality signals | Analysis uses sleep timing only |
| Read steps, exercise | Activity context, phone detection | Lower confidence in phone detection |
| Read health data history | Backfill older than 30 days | Only the last 30 days are read |
| Read health data in background | Overnight sync | Drift syncs when you open it; a banner explains |

Every denial leads to a working screen with a "fix" action.

## Sleep as Android

Three paths, all deduplicated against each other by overlap:

1. **Health Connect sync:** if Sleep as Android writes to Health Connect, its nights arrive through the
   Health Connect source, tagged by origin.
2. **CSV import:** pick a Sleep as Android backup CSV with the system file picker. The parser is tolerant of
   format differences.
3. **Tracking-finished broadcast:** Drift listens for Sleep as Android's broadcast so a night appears minutes
   after tracking stops.

## Phone detection

For people without a wearable. Each night is reconstructed after the fact from OS signals, so it costs almost
no battery.

- **Signals:** screen-on history (needs usage access, optional), activity recognition sleep segments, steps,
  charging changes, the next alarm, and alarm dismissal.
- **Model:** a two-state hidden Markov model over 10-minute bins, decoded with Viterbi into onset, wake and a
  confidence. A long gap without phone use isn't counted as sleep without other evidence.
- **Morning confirmation:** "Looks like you slept 11:40 pm to 7:05 am", which you can confirm or drag to adjust.
  Confirmed nights count as user-entered and carry more weight.

| Permission | Used for | If you deny it |
| --- | --- | --- |
| Usage access | Screen-on history | Activity recognition and steps only; lower confidence |
| Activity recognition | Sleep segments, steps | Lower confidence |
| Notifications | Morning confirmation, reminders | In-app cards only |

## Wearables through Health Connect

Any wearable whose companion app writes sleep to Health Connect works without extra code. To give it the right
quality tier, its package name needs an entry in the origin table. For example, boAt rings work through Health
Connect once the boAt app writes data there.

## Adding a new source

A source that can't go through Health Connect (for example a direct Bluetooth connection) is a new
`:source:<name>` module that implements `SourceAdapter` from `:core:model` and is registered in `:app`. It may
depend only on `:core:model`. Open a feature request first so the design can be agreed.
