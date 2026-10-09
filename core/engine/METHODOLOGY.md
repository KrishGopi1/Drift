# Drift engine methodology (engine 1.0.0)

All numbers come from the pure Kotlin engine in `:core:engine`. Same inputs and engine version always give the
same outputs. Every constant lives in `ParameterSet` with a citation; entries marked "to verify" in the
parameter list have not been checked against the primary source by the author. Provenance: Measured (device
data), Derived (deterministic arithmetic), Estimated (model output with a 90% band), User-entered.

## 1. Preprocessing
Episodes separated by less than 45 minutes of waking are merged; sleep time counts only asleep minutes
(awake stages excluded, overlapping sources are unioned). The longest episode overlapping 20:00-12:00 around the
wake date is the main sleep, others are naps. Each episode belongs to the date it ends, in its own time zone.
Main sleeps under 2 h or over 16 h, and episodes ending in the future, are flagged and excluded unless the user
confirmed them. Missing nights stay missing. If three or more of the last seven main sleeps lie mostly between
08:00 and 18:00, shift-work mode is on. Quality weight = source tier x completeness x detection confidence,
boosted for user-confirmed or edited nights.

## 2. Sleep need
One-dimensional Kalman filter (Kalman 1960). Prior: population mean by age band (Hirshkowitz et al. 2015;
Klerman and Dijk 2008), SD 0.6 h. Process noise 10 min per month. Free nights (user-marked, no alarm, or waking
more than 45 min after the workday median) are observations corrected for rebound: TST - 0.3 x previous debt
(capped at 2 h). Alarm nights and disturbed nights (60+ min waking) are censored: a Tobit-style one-sided update
(Tobin 1958) says only that need is at least what was slept. Below 5 informative nights the prior is shown.
Bounds 5-11 h; 90% band from the filter variance.

## 3. Sleep debt
D(t) = lambda D(t-1) + (N - S) if S <= N, else lambda D(t-1) - eta (S - N), never below zero; lambda gives a
7-day half-life, eta starts at 0.6 and is refit from how fast free-night surplus shrinks (shrunk toward 0.6)
(Van Dongen et al. 2003; Banks et al. 2010; Kitamura et al. 2016). Window: 14 sleep days. Naps count in full up
to 90 min, then half. One or two missing nights are imputed from the same-weekday median with a wider band; a
run of 3+ missing nights up to now makes debt unavailable; older long gaps are left unknown (debt only leaks).
Debt is measured against the need established before the window so a short night cannot lower its own yardstick.
Bands: Low under 5 h, Moderate 5-10 h, High over 10 h.

## 4. Regularity and chronotype
Sleep Regularity Index (Phillips et al. 2017): -100 + 200 x P(same sleep/wake state 24 h apart), computed on
1-minute vectors over 7 and 14 days, shown 0-100, needs 5+ paired days. The most recent day counts only up to now
(or up to the final wake-up). Timing spread is the circular SD of onset, wake and midpoint. Social jetlag is
|free-day midpoint - workday midpoint| (Wittmann et al. 2006). Chronotype is MSFsc (Roenneberg et al. 2003, 2007)
blended with the onboarding answer until 4 free nights exist.

## 5. Circadian phase
DLMO is tracked by a circular Kalman filter from each night: 2.5 h before onset and 6.5 h before mid-sleep on free
nights, onset only (with larger variance) on alarm, disturbed or shift nights. After a time-zone change (different
zone id, offset change of 30+ min) the clock is moved with the clock time and then re-entrains at 1 h/day
eastward (advance) or 1.5 h/day westward (delay) (Eastman and Burgess 2009; rates to verify). The melatonin
window is the hour starting 2 h after DLMO; wind-down starts 1 h before it. Shift-work mode widens the band.

## 6. Energy curve
Three-process model (Akerstedt and Folkard 1995, 1997): A(t) = S + C + W. S falls while awake (tau 18.2 h) and
recovers asleep (tau 4.2 h, scaled by 8 h / your need), simulated over the last 14 days of real sleep (missing
nights imputed from habit) and tomorrow's recommended sleep. C is a 24 h cosine plus a 12 h harmonic tied to DLMO.
W is sleep inertia decaying over about an hour, larger after short or debt-heavy sleep (Tassi and Muzet 2000).
Normalised to 0-100 at 5-minute resolution; phases come from extrema with a minimum prominence, and the day is
marked "too flat for a dip" instead of inventing one. With 10+ check-ins a bounded least-squares fit adjusts
circadian amplitude (x0.6-1.6) and phase (+-3 h).

## 7. Recommendations
Sleep opportunity = (need + min(0.25 x debt, 1 h)) / sleep efficiency (personal median in-bed efficiency, default
0.88). Wake time comes from the schedule on workdays, otherwise from habit. Bedtime = wake - opportunity; if it
falls well before the melatonin window the trade-off is shown rather than overriding it. Habit cards: caffeine
cutoff 8 h before bed (Drake et al. 2013), last meal and alcohol 3 h (Ebrahim et al. 2013), morning light within
1 h of waking (Wright et al. 2013), exercise not inside wind-down (Youngstedt et al. 2019), dim lights from
wind-down (Gooley et al. 2011). Nap advice only with Moderate+ debt and an afternoon dip: 20-30 min ending 7+ h
before bed (Brooks and Lack 2006).

## 8. Explanations
Each run stores its headline numbers and per-night debt contributions; the next run diffs against them and
attributes changes to nights entering or leaving the window, need re-estimation, edits, new sources, circadian
shifts or check-ins.

## 9. Data sufficiency
0 nights: priors only. 1-4: debt with a wide band, need still the prior. 5-13: personal need once 5 free nights
exist, regularity available. 14+: full model.
