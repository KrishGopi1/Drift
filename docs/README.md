# Using Drift

This guide gets Drift onto your phone, connected to your sleep data, and explains what each screen tells you.
For how the numbers are calculated, see [science.md](science.md). To build the app yourself, see
[development.md](development.md).

## Install

Drift is not on the Play Store. It ships as an APK on the
[Releases page](https://github.com/KrishGopi1/Drift/releases/latest). It needs Android 9 or newer.

1. Download `drift-vX.Y.Z.apk` and, if you want to check it, the matching `.sha256` file.
2. Optional: verify the download on a computer.

   ```bash
   sha256sum -c drift-v0.1.0.apk.sha256
   # drift-v0.1.0.apk: OK
   ```

3. Open the APK on your phone. Android asks you to allow installs from the app you opened it with (your browser
   or file manager). Allow it, install, then turn the permission back off if you like.

## Connect your sleep data

Onboarding asks where your sleep comes from. You can use more than one; overlapping nights are merged on the
phone and your own edits always win.

| You use | Pick | Notes |
| --- | --- | --- |
| A watch, ring or band whose app writes to Health Connect | Health Connect | Reads up to 90 days of history on first run |
| Sleep as Android | Sleep as Android | Arrives through Health Connect, a backup CSV import, or live when tracking stops |
| Nothing | Phone detection | Reconstructs each night from screen, activity, charging and alarm signals, then asks you to confirm in the morning |

Every permission is optional. Denying one gives a working screen with a button to fix it later. The full list of
what each permission is used for is in [data-sources.md](data-sources.md).

Until Drift has about 5 informative nights, sleep need is a population estimate for your age, and the app says so.

## What each tab shows

| Tab | What it answers |
| --- | --- |
| **Sleep** | How much sleep debt you carry over the last 14 nights, last night's sleep, your sleep need and regularity, and the bedtime and wake time that pay debt back tonight |
| **Energy** | When today's peaks and dips fall, when grogginess wears off, the last sensible time for caffeine, and when to dim the lights |
| **Progress** | Night-by-night sleep times, how your debt changed, and how you felt each morning |
| **Learn** | Plain explanations of the body clock and of how Drift calculates each number |

Tap the question mark next to a number to see what changed it and which nights were involved.

## Fix a night

Open a night from **Sleep** or **Progress** and tap **Edit** to drag the bedtime or wake time, or add a night
or nap that was missed. Marking a day without an alarm as a free day helps Drift learn your natural sleep need.

## Reminders

Settings has a switch for each reminder: wind-down, melatonin window, caffeine cutoff, morning light, morning
sleep confirmation and energy check-ins. All but energy check-ins start switched on. They are local
notifications; nothing is scheduled on a server.

## Back up and move your data

Your data lives only on your phone, in an encrypted database. In **Settings → Data**:

- **Export everything** saves all your data to a file you choose.
- **Import a Drift export** restores from that file, for example on a new phone.
- **Reset all data** deletes every night, setting and on-phone backup. Export first if you want a copy.

## Troubleshooting

**No nights appear after connecting Health Connect.** Check that the app on your wearable actually writes sleep
to Health Connect (open Health Connect → App permissions → your wearable's app). On Android 9-13, Health Connect
is a separate app from the Play Store.

**Nights only update when I open Drift.** Background reading needs the "Read health data in background"
permission. Without it Drift syncs on open and shows a banner.

**Phone detection got my night wrong.** Confirm or drag the night in the morning prompt. Confirmed nights carry
more weight, and detection is weakest if you use the phone in bed or leave it untouched before sleeping.

**The energy curve looks flat.** If the predicted afternoon dip is too small to be real, Drift says so instead of
inventing one. More nights, or energy check-ins, sharpen it.

Something else? Open an [issue](https://github.com/KrishGopi1/Drift/issues). Drift has no crash reporter, so a
report from you is the only way a bug is seen.
