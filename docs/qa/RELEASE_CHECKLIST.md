# Release checklist (plan 9.3), living document

Copy the "Release candidate" block at the bottom for each candidate, tick items, and paste the evidence (command output,
file path, or link). An item is ticked only with evidence. All commands run from the repo root after `source scripts/env.sh`.
Device items need the reference phone (open decision 3) and `adb` (`$ANDROID_HOME/platform-tools`).

| # | Item | How to verify (command) | Evidence to keep | Automated |
| --- | --- | --- | --- | --- |
| 1 | Manifest has no INTERNET permission; CI fails if one appears | `./gradlew checkNoInternetPermission` then, on the signed APK, `$ANDROID_HOME/build-tools/36.0.0/aapt2 dump permissions app/build/outputs/apk/release/app-release.apk` (INTERNET must not be listed) | Both outputs | Gate: `driftChecks` |
| 2 | No TODO/FIXME/"placeholder" and no hard-coded metric values in feature modules | `./gradlew checkNoPlaceholders`; hard-coded numbers: `grep -rnE '"[^"]*[0-9]+ ?(h\|min\|%)' feature/*/src/main --include=*.kt` and review every hit (hits must be format strings, not data) | Output of both, reviewer initials | Partly |
| 3 | Every screen x state x theme x font scale has a screenshot test | `./gradlew verifyRoborazziDebug`; count: `find feature -path '*screenshots*' -name '*.png' \| wc -l` must equal screens x 6 states x 2 themes x 2 font scales (1.0 and 2.0); naming `<Screen>_<state>_<theme>_<fontScale>.png`; list screens from plan 6.2 | Count, expected count, verify output | Gate |
| 4 | Every clickable element covered by a UI test asserting an effect | Audit: `grep -rnE 'clickable\|onClick\|Button\(' feature/*/src/main --include=*.kt \| wc -l` vs. a table of tests in `feature/*/src/test` that assert an effect through `Fake*Repository.callsTo(...)` or a state change; every row of the table names the test | Audit table (click target -> test name) | Partly |
| 5 | Cold start to first frame < 500 ms on a mid-range phone with baseline profiles | `./gradlew :app:generateBaselineProfile` then `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest` and `python3 benchmark/check_results.py` (see `benchmark-module.md`) | `benchmarkData.json`, checker output (median `timeToInitialDisplayMs`) | Device |
| 6 | < 1% janky frames scrolling Energy, Sleep, History | Same run; `check_results.py` computes janky frames per scroll benchmark from `frameOverrunMs` | Checker output for the three scroll benchmarks | Device |
| 7 | Overnight battery < 1% with phone detection on | Charge to 100%, unplug, `adb shell dumpsys batterystats --reset`, enable phone detection, sleep phone (screen off, at least 7 h), then `adb shell dumpsys batterystats --charged app.drift` and read "Estimated power use (mAh)" for the `app.drift` UID; divide by battery capacity (`adb shell dumpsys battery` / spec sheet). Cross-check the battery level drop of an identical night without the app running | `batterystats` excerpt, capacity, calculation | Manual |
| 8 | Reminders and sync survive reboot, Doze, standby buckets, force-stop then reopen, zone change | Matrix below, one row per condition, each ends with "reminder fired / sync ran" evidence (`adb logcat -s Drift` or `adb shell dumpsys alarm \| grep app.drift`, `adb shell dumpsys jobscheduler \| grep app.drift`) | Filled matrix | Partly |
| 9 | Clean recovery from permission revoke, HC uninstall, full storage, corrupt DB (restore from backup) | Matrix below; each ends with the right ScreenState (SourceProblem / Error with retry) and no crash | Filled matrix, screenshots | Partly |
| 10 | DB migrates from every earlier schema version shipped in betas | `./gradlew :core:data:test` (Room `MigrationTestHelper`, one test per consecutive version, schemas in `core/data/schemas`); list shipped versions in the table below | Test names and schema file list | Gate |
| 11 | Export, wipe and re-import round-trips all data exactly | `./gradlew :core:data:test --tests '*BackupRoundTrip*'` (A2) over `Scenarios.all(seed)` histories; on device: Settings > Export, Reset, Import, then compare the second export byte-for-byte (`cmp export1.json export2.json`) | Test output, `cmp` result | Gate + device |
| 12 | R8 full mode, signed with v2+v3 schemes, version code bumped | Commands in `release-signing.md` (`apksigner verify --verbose`, `aapt2 dump badging`, mapping file present) | Command outputs, new versionCode vs. previous tag | Partly |
| 13 | Two-week dogfood on 3+ phones, no crash, no data loss | Install release APK on 3+ phones; daily check of Settings > export count vs. nights; there is no crash reporting by design, so collect crashes from `adb logcat -b crash` / `adb bugreport` and compare the in-app export with the expected nights | Dogfood log below | Manual |

## Item 8 matrix (device)
| Condition | How | Reminder fires | Sync runs | Evidence |
| --- | --- | --- | --- | --- |
| Reboot | `adb reboot`, unlock once | [ ] | [ ] | |
| Doze | `adb shell dumpsys battery unplug && adb shell dumpsys deviceidle force-idle`, wait for the next due time, then `adb shell dumpsys deviceidle unforce` | [ ] | [ ] | |
| Standby bucket: rare | `adb shell am set-standby-bucket app.drift rare` | [ ] | [ ] | |
| Standby bucket: restricted | `adb shell am set-standby-bucket app.drift restricted` | [ ] | [ ] | |
| Force-stop then reopen | `adb shell am force-stop app.drift`, then launch once | [ ] | [ ] | |
| Zone change | Settings > Date & time (turn off automatic zone), pick a zone 5+ hours away; alarms rebuild for local times | [ ] | [ ] | |
| Daylight-saving change | Set the date to the evening before a transition in a DST zone | [ ] | [ ] | |

## Item 9 matrix (device)
| Failure | How | Expected | Pass |
| --- | --- | --- | --- |
| Permission revoked | `adb shell pm revoke app.drift <health permission>` (names from the manifest) while app is open | SourceProblem state with a fix button; other data still shown | [ ] |
| Health Connect missing | Disable/uninstall HC (`adb shell pm disable-user --user 0 com.google.android.apps.healthdata`) | `SourceFix.InstallHealthConnect`; history kept | [ ] |
| Full storage | Fill the device (`adb shell "dd if=/dev/zero of=/sdcard/fill bs=1M count=N"` until about 20 MB free), then add a nap | Error with retry, no corruption; after freeing space, retry works | [ ] |
| Corrupt database | Debug build only: `adb shell run-as app.drift sh -c 'printf garbage > databases/drift.db'`, restart | App offers restore from the latest local backup; after restore data matches | [ ] |
| Corrupt backup | Truncate the newest backup, restore | Falls back to the previous backup or reports clearly | [ ] |

## Item 10: schema versions shipped in betas
| Version | Shipped in | Schema file | Migration test |
| --- | --- | --- | --- |
| 1 | | `core/data/schemas/.../1.json` | n/a (initial) |

## Item 13: dogfood log
| Phone (model, Android) | Build (versionName/Code) | Start | End | Crashes | Missing/wrong nights | Notes |
| --- | --- | --- | --- | --- | --- | --- |

## Release candidate block (copy per RC)
```
RC: versionName ____ versionCode ____   commit ____   date ____   engine version ____
[ ] 1  [ ] 2  [ ] 3  [ ] 4  [ ] 5  [ ] 6  [ ] 7  [ ] 8  [ ] 9  [ ] 10  [ ] 11  [ ] 12  [ ] 13
Gate run:   scripts/ci.sh  -> PASS (log: ____)
Validation: docs/qa/VALIDATION_REPORT_TEMPLATE.md filled in -> ____
Signed off by: ____
```
