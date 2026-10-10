# Drift Science

The README says **what** Drift calculates. This document explains **why** it calculates it that way.

Everything here lives in `:core:engine`, a pure Kotlin library with no Android or I/O dependencies. Given the
same input and engine version it produces the same output. Every constant below comes from one versioned
`ParameterSet` (`core/engine/src/main/kotlin/app/drift/engine/ParameterSet.kt`, version 1.1.1 at the time of
writing) where each value carries a citation. Values marked *design default* have no direct source; they're
engineering choices to be tuned against real data. The app shows the same material under
**Learn → How Drift calculates**.

Found something wrong? Open a
[scientific issue](https://github.com/KrishGopi1/Sleep-Project/issues/new?template=scientific_issue.yml).

- [Data quality](#data-quality)
- [Energy model](#energy-model)
- [Sleep need](#sleep-need)
- [Sleep debt](#sleep-debt)
- [Circadian phase](#circadian-phase)
- [Sleep Regularity Index and chronotype](#sleep-regularity-index-and-chronotype)
- [Recommendations](#recommendations)
- [How much data each feature needs](#how-much-data-each-feature-needs)
- [Known limitations](#known-limitations)
- [References](#references)

## Data quality

Before any model runs, the timeline is cleaned up:

- **Merge:** fragments of one night separated by less than **45 min** awake become one night.
- **Main sleep vs nap:** the longest episode overlapping the habitual night window (20:00 to 12:00) is the main
  sleep; others are naps. Each episode belongs to the date of its wake time.
- **Shift work:** if the main sleep falls mostly between 08:00 and 18:00 on 3 or more of the last 7 days,
  shift-work mode switches on and the circadian estimate anchors to wherever the main sleep falls.
- **Never zero-fill:** missing nights stay missing. Main sleeps under **2 h** or over **16 h** are flagged and
  excluded unless you confirmed them.
- **Weighting:** each night gets a weight from its source (ring 1.0, wrist 0.9, phone app 0.7, phone-inferred 0.5)
  times its completeness, and ×1.25 if you confirmed or edited it. Models use this as observation precision.

All of these are *design defaults*.

## Energy model

Drift's energy curve is a three-process model in the tradition of Borbély's two-process model and
Åkerstedt & Folkard's alertness model:

```text
alertness(t) = 0.55 · S(t) + C(t) − W(t)
```

The raw value is mapped to a 0-100 level, computed every 5 minutes over 14 days of your real sleep, then split
into phases: grogginess, morning peak, afternoon dip, evening peak, wind-down and melatonin window. If the
curve is too flat for a real dip (prominence under 5 points), Drift says so instead of inventing one.

### Homeostatic component

`S` is sleep pressure, simulated over the last 14 days. It decays while you're awake with a time constant of
**18.2 h** and recovers during sleep with **4.2 h**, the values from Daan, Beersma & Borbély (1984). Sleep
minutes are scaled by 8 h / your need, so someone who needs 9 h recovers a little slower per minute. Because the
simulation runs over real history, today's curve already reflects recent short nights.

### Circadian component

`C` is a 24 h cosine (amplitude 0.20) plus a 12 h harmonic (amplitude 0.10) that produces the post-lunch dip.
Its phase is tied to your estimated DLMO (see [Circadian phase](#circadian-phase)): the 24 h peak sits 4 h before
DLMO, and the harmonic's trough lands around 14:00 for a DLMO of 21:00. Amplitudes and phase offsets are
*design defaults*.

### Sleep inertia

`W` is the grogginess after waking: an exponential decay with a **40 min** time constant. It starts at 0.22
after a full night and grows by 20% per hour of sleep shortfall and 4% per hour of debt, capped at 2×. Naps
under an hour scale it down. Grogginess is considered over when inertia falls below 0.04. Based on
Tassi & Muzet (2000) and Hilditch & McHill (2019); exact constants are *design defaults*.

### Energy potential

Separately from the hour-by-hour curve, Drift shows how much of your full capacity is available today, a
reservoir idea taken from the SAFTE fatigue model (Hursh et al., 2004):

```text
potential = 100 · exp(−debt / 24 h)
```

Bands: Super ≥ 92 (debt under about 2 h), Great ≥ 80 (under about 5.3 h), Good ≥ 68, Fair ≥ 55, Low below.
Low potential also pulls the curve down, more in the dips than at the peaks. The mapping and constants are
*design defaults*.

### Calibration

You can log 1-5 energy check-ins or 3-minute reaction-time tests. After **10** check-ins, a bounded,
ridge-penalised least-squares fit adjusts the circadian amplitude (0.6× to 1.6×) and phase (±3 h). The app shows
how many check-ins shaped the curve.

## Sleep need

How much sleep *you* need, estimated with a one-dimensional Kalman filter (local-level model).

### Initial prior

A population mean for your age band from extended-sleep-opportunity research (Klerman & Dijk, 2008) and the
National Sleep Foundation recommendations (Hirshkowitz et al., 2015):

| Age | Prior |
| --- | --- |
| 18-25 | 8 h 15 min |
| 26-35 | 8 h 00 min |
| 36-50 | 7 h 45 min |
| 51-64 | 7 h 30 min |
| 65+ | 7 h 15 min |

Standard deviation 36 min. Until you have **5** informative nights, Drift shows the prior and says it's based on
people like you.

### Free-night detection

Only nights that tell us something about natural sleep length update the estimate:

- **Free nights** (no alarm within 45 min of waking, or you woke more than 45 min later than your workday median)
  are full observations.
- **Alarm nights** are censored: you might have slept longer without the alarm. They get a one-sided
  (Tobit-style, Tobin 1958) update that can only push need up, with a wider observation SD of 60 min.
- **Disturbed nights** (60+ min awake after sleep onset) don't count as evidence of being satisfied.

### Kalman filter

Each free night is an observation with SD 45 min; need is allowed to drift by about 10 min per month. The output
is a 90% band. Estimates are bounded to 5-11 h.

### Rebound correction

After a run of short nights, people sleep longer than they need on the next free night. So each observation is
corrected:

```text
observation = total sleep − 0.3 × previous debt   (correction capped at 2 h)
```

The idea that free-night excess tracks accumulated debt comes from Kitamura et al. (2016); the 0.3 coefficient is
a *design default*.

## Sleep debt

### Accumulation

A leaky accumulator over the last **14** sleep days:

```text
if slept ≤ need:  D(t) = λ · D(t−1) + (need − slept)
if slept > need:  D(t) = λ · D(t−1) − η · (slept − need)
D(t) ≥ 0
```

λ gives unrepaid debt a **7-day half-life**, so a bad night two weeks ago matters much less than last night.
Bands: Low < 5 h, Moderate 5-10 h, High > 10 h. The debt screen lists which nights built or repaid debt.

Cumulative restriction effects follow Van Dongen et al. (2003) and Belenky et al. (2003).

### Recovery

Extra sleep doesn't repay debt minute-for-minute. η starts at **0.6** (each surplus minute repays 0.6 min) and is
fitted to you from how quickly your free-night rebound shrinks, bounded to 0.3-1.0 and shrunk toward the default
until at least 4 pairs of consecutive free nights are available. Slow recovery after restriction is described
by Banks et al. (2010) and Belenky et al. (2003).

### Missing nights

A missing night is filled with the median of the same weekday over the last 8 weeks, which widens the debt
band by 25 min. With **3 or more** missing nights in a row, Drift shows "not enough data" instead of guessing.

### Naps

Naps count toward sleep: the first **90 min** at full credit, anything beyond at half rate. Recommended naps are
20-30 min (Brooks & Lack, 2006).

## Circadian phase

### DLMO

Dim-light melatonin onset (DLMO) is the standard marker of the body clock. Drift can't measure melatonin, so it
estimates DLMO from sleep timing: **2.5 h before habitual sleep onset** and **6.5 h before mid-sleep**, in line with
Burgess & Fogg (2008). A circular Kalman filter tracks DLMO clock time night by night; alarm nights get 2× the
variance and shift nights 3×. The reported band adds 30 min for individual differences in the sleep-to-DLMO
mapping.

### Re-entrainment

After travel across time zones (a zone change of 30+ min), the estimate moves at most **1 h/day eastward** and
**1.5 h/day westward**, a commonly used rule of thumb (Eastman & Burgess, 2009; Waterhouse et al., 2007). While the
clock is adjusting, nightly observations get 8× variance so a single night doesn't snap it to the new zone.

### Melatonin window

The melatonin window is the hour starting **2 h after DLMO**. Wind-down starts 1 h before it. Evening room light
delays and suppresses melatonin (Gooley et al., 2011), so the dim-lights habit starts at wind-down.

## Sleep Regularity Index and chronotype

### SRI

The Sleep Regularity Index (Phillips et al., 2017) is the probability of being in the same state (asleep or
awake) at two moments 24 h apart, scaled 0-100, computed on 1-minute epochs over 7 and 14 days. It needs at least
5 paired days.

### Timing spread and social jetlag

Drift also reports the circular standard deviation of sleep onset, wake and mid-sleep (14 days, 5+ nights), and
social jetlag: the gap between free-day and workday mid-sleep (Wittmann et al., 2006).

### Chronotype

Chronotype uses mid-sleep on free days corrected for sleep debt (MSFsc, Roenneberg et al., 2007). Early is MSFsc
before 03:30, Late after 05:00. Until you have 4 free nights it's blended with the onboarding questionnaire.

## Recommendations

### Tonight's sleep opportunity

```text
time in bed = (need + min(0.25 × debt, 1 h)) / sleep efficiency
```

Sleep efficiency is your median asleep/in-bed ratio once 5 nights have in-bed data, otherwise 0.88. Bedtime is
wake time minus time in bed, then checked against the melatonin window. If bedtime would be more than 45 min
before the window opens, Drift shows the trade-off instead of silently picking one.

### Habits

| Habit | Timing | Basis |
| --- | --- | --- |
| Last caffeine | 8 h before bed | Drake et al. (2013); Clark & Landolt (2017) |
| Last large meal | 3 h before bed | *design default* |
| Alcohol | 3 h before bed | Ebrahim et al. (2013) |
| Morning daylight | Within 1 h of waking | Wright et al. (2013) |
| Intense exercise | From 1 h after waking until wind-down | Youngstedt et al. (2019) |
| Dim lights | From wind-down | Gooley et al. (2011) |

### Naps

Suggested only when debt is at least Moderate (5 h) and there's a real afternoon dip: 20-30 min, ending at least
7 h before bedtime.

## How much data each feature needs

| Nights of data | What you get |
| --- | --- |
| 0 | Energy curve from the questionnaire and population priors only, marked Estimated |
| 1-4 | Sleep debt with a wide band; sleep need is still the prior |
| 5-13 | Personal sleep need starts; regularity becomes available |
| 14+ | Full model; calibration from check-ins if you have 10+ |

## Known limitations

- **Phone detection is an estimate.** Without a wearable, sleep onset and wake come from phone signals and
  can be off, especially if you use your phone in bed or leave it on the nightstand untouched before sleeping.
- **DLMO is inferred, not measured.** Sleep timing is a reasonable proxy for most people but not for everyone.
- **Population priors.** Until Drift has enough of your nights, numbers reflect typical people your age.
- **Design defaults.** Many constants are engineering choices with no direct source (marked above and in
  `ParameterSet.kt`). They should be validated against real replayed data. Contributions here are very welcome.
- **Not medical advice.** Drift is a wellness tool. It doesn't diagnose sleep disorders.

## References

- Banks S, Van Dongen HPA, Maislin G, Dinges DF (2010). Neurobehavioral dynamics following chronic sleep restriction: dose-response effects of one night for recovery. *Sleep* 33(8):1013-1026.
- Belenky G, et al. (2003). Patterns of performance degradation and restoration during sleep restriction and subsequent recovery. *Journal of Sleep Research* 12(1):1-12. doi:10.1046/j.1365-2869.2003.00337.x
- Borbély AA (1982). A two process model of sleep regulation. *Human Neurobiology* 1(3):195-204.
- Brooks A, Lack L (2006). A brief afternoon nap following nocturnal sleep restriction: which nap duration is most recuperative? *Sleep* 29(6):831-840. doi:10.1093/sleep/29.6.831
- Burgess HJ, Fogg LF (2008). Individual differences in the amount and timing of salivary melatonin secretion. *PLoS ONE* 3(8):e3055.
- Clark I, Landolt HP (2017). Coffee, caffeine, and sleep: a systematic review of epidemiological studies and randomized controlled trials. *Sleep Medicine Reviews* 31:70-78. doi:10.1016/j.smrv.2016.01.006
- Daan S, Beersma DGM, Borbély AA (1984). Timing of human sleep: recovery process gated by a circadian pacemaker. *American Journal of Physiology* 246(2):R161-R183. doi:10.1152/ajpregu.1984.246.2.R161
- Drake C, Roehrs T, Shambroom J, Roth T (2013). Caffeine effects on sleep taken 0, 3, or 6 hours before going to bed. *Journal of Clinical Sleep Medicine* 9(11):1195-1200. doi:10.5664/jcsm.3170
- Eastman CI, Burgess HJ (2009). How to travel the world without jet lag. *Sleep Medicine Clinics* 4(2):241-255. doi:10.1016/j.jsmc.2009.02.006
- Ebrahim IO, Shapiro CM, Williams AJ, Fenwick PB (2013). Alcohol and sleep I: effects on normal sleep. *Alcoholism: Clinical and Experimental Research* 37(4):539-549. doi:10.1111/acer.12006
- Gooley JJ, et al. (2011). Exposure to room light before bedtime suppresses melatonin onset and shortens melatonin duration in humans. *Journal of Clinical Endocrinology & Metabolism* 96(3):E463-E472. doi:10.1210/jc.2010-2098
- Hilditch CJ, McHill AW (2019). Sleep inertia: current insights. *Nature and Science of Sleep* 11:155-165. doi:10.2147/NSS.S188911
- Hirshkowitz M, et al. (2015). National Sleep Foundation's sleep time duration recommendations: methodology and results summary. *Sleep Health* 1(1):40-43. doi:10.1016/j.sleh.2014.12.010
- Hursh SR, Redmond DP, Johnson ML, et al. (2004). Fatigue models for applied research in warfighting. *Aviation, Space, and Environmental Medicine* 75(3 Suppl):A44-A53.
- Kalman RE (1960). A new approach to linear filtering and prediction problems. *Journal of Basic Engineering* 82(1):35-45. doi:10.1115/1.3662552
- Kitamura S, et al. (2016). Estimating individual optimal sleep duration and potential sleep debt. *Scientific Reports* 6:35812. doi:10.1038/srep35812
- Klerman EB, Dijk DJ (2008). Age-related reduction in the maximal capacity for sleep: implications for insomnia. *Current Biology* 18(15):1118-1123. doi:10.1016/j.cub.2008.06.047
- Ohayon M, et al. (2017). National Sleep Foundation's sleep quality recommendations: first report. *Sleep Health* 3(1):6-19. doi:10.1016/j.sleh.2016.11.006
- Phillips AJK, et al. (2017). Irregular sleep/wake patterns are associated with poorer academic performance and delayed circadian and sleep/wake timing. *Scientific Reports* 7:3216. doi:10.1038/s41598-017-03171-4
- Roenneberg T, Kuehnle T, Juda M, et al. (2007). Epidemiology of the human circadian clock. *Sleep Medicine Reviews* 11(6):429-438. doi:10.1016/j.smrv.2007.07.005
- Tassi P, Muzet A (2000). Sleep inertia. *Sleep Medicine Reviews* 4(4):341-353. doi:10.1053/smrv.2000.0098
- Tobin J (1958). Estimation of relationships for limited dependent variables. *Econometrica* 26(1):24-36. doi:10.2307/1907382
- Van Dongen HPA, Maislin G, Mullington JM, Dinges DF (2003). The cumulative cost of additional wakefulness. *Sleep* 26(2):117-126. doi:10.1093/sleep/26.2.117
- Waterhouse J, Reilly T, Atkinson G, Edwards B (2007). Jet lag: trends and coping strategies. *The Lancet* 369(9567):1117-1129.
- Wittmann M, Dinich J, Merrow M, Roenneberg T (2006). Social jetlag: misalignment of biological and social time. *Chronobiology International* 23(1-2):497-509. doi:10.1080/07420520500545979
- Wright KP Jr, et al. (2013). Entrainment of the human circadian clock to the natural light-dark cycle. *Current Biology* 23(16):1554-1558. doi:10.1016/j.cub.2013.06.039
- Youngstedt SD, Elliott JA, Kripke DF (2019). Human circadian phase-response curves for exercise. *The Journal of Physiology* 597(8):2253-2268. doi:10.1113/JP276943

Several citations in `ParameterSet.kt` carry a verification note where the exact number or bibliographic detail
still needs checking against the source. Help confirming them is welcome.
