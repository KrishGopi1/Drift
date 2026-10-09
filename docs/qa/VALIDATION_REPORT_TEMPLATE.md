# Real-world validation report (plan 9.2)

Copy this file to `docs/qa/reports/validation-<engine version>-<date>.md` and fill it in. Anything not measured is marked
"not measured" with the reason; a blank is a failure.

| Field | Value |
| --- | --- |
| Engine version | |
| App versionName / versionCode | |
| Commit | |
| Date | |
| Run by | |
| Data sets | list: file name, owner, nights, date range, sources, device (histories are exported from the app, replay format: `REPLAY_FORMAT.md`) |
| Open decision 5 (validation data) status | |

## 1. Need stability (target: need changes <= 15 min/week except after a real lifestyle change)
Method: for each history, replay week by week with the replay CLI (`ReplayData.toEngineInput(at)` for `at` = each Sunday 23:59 local,
feeding `EngineState` forward). Record the personal need estimate each week.

| History | Week ending | Need (min) | Change vs previous week (min) | Lifestyle change that week? (describe) | Pass |
| --- | --- | --- | --- | --- | --- |

Summary: weeks measured __, weeks over 15 min __, of which explained by a lifestyle change __. Unexplained exceedances: __ (target 0).
Synthetic baseline (must also pass): `Scenarios.all(seed)` for seeds 1..20, same procedure; the steady-sleeper need must stay within 15 min/week.

## 2. Phone detection accuracy (target: median onset/wake error <= 30 min vs Sleep as Android / wearable)
Method: nights with both a phone-inferred episode and a reference episode (WEARABLE_* or PHONE_APP tier). Error is the absolute difference in minutes.

| History | Nights compared | Median onset error (min) | Median wake error (min) | 90th percentile onset / wake (min) | Pass |
| --- | --- | --- | --- | --- | --- |

Notes on failure modes (charging off-bed, late-night screen use, shared bed, shift work): __

## 3. SRI agreement (target: within 1 point of a reference Phillips et al. implementation)
Method: an independent implementation of the Sleep Regularity Index (minute-level wake/sleep agreement 24 h apart, scaled to -100..100,
see Phillips et al. 2017, Sci Rep 7:3216) run over the same episodes the engine saw. Record the reference script's name and commit.

| History | Window | Reference SRI | Engine SRI | Difference | Pass |
| --- | --- | --- | --- | --- | --- |

## 4. Sleep-science review
Method: the reviewer reads the Settings > Methodology screen (every citation, every stated parameter) and 20 explanation strings
chosen by a seeded random sample: `shuf --random-source=<(yes <seed>) -n 20 explanations.txt` over all explanations produced by
replaying the histories above. Keep the seed and the sampled strings in an appendix.

| Reviewer | Credentials | Date |
| --- | --- | --- |

| Item | Verdict (accurate / needs wording change / wrong) | Comment |
| --- | --- | --- |
| Methodology screen: each citation supports the claim | | |
| Explanation 1..20 | | (one row each) |

Changes requested and where they were made (commit): __

## 5. Verdict
| Criterion | Pass |
| --- | --- |
| Need stability | [ ] |
| Phone detection | [ ] |
| SRI agreement | [ ] |
| Reviewer sign-off | [ ] |

Release blocked by validation? __   Follow-ups: __
