# `:benchmark` module: Macrobenchmark + Baseline Profile (for A0 to add)

Design for a `com.android.test` module that (a) generates the baseline profile for `:app`, (b) asserts cold start < 500 ms,
(c) measures scroll jank on Energy, Sleep and History (< 1% janky frames). Nothing here is applied by A6.

Already in `gradle/libs.versions.toml` and the root `build.gradle.kts`: plugins `android-test`, `kotlin-android`, `baselineprofile`
(version `benchmark` = 1.4.1), libraries `androidx-benchmark-macro-junit4`, `androidx-test-uiautomator`, `androidx-test-ext-junit`,
and `androidx-profileinstaller` is already a dependency of `:app`. A0 only has to add the module and wire `:app`.

The dependency checker has no rule for `:benchmark`, so it is not constrained; keep it depending on nothing but `:app` (via `targetProjectPath`).

## 1. `settings.gradle.kts`
Add `":benchmark"` to the `include(...)` list.

## 2. `app/build.gradle.kts`
```kotlin
plugins {
    // existing aliases ...
    alias(libs.plugins.baselineprofile)
}

baselineProfile {
    // Generate on the connected reference phone or emulator; no Gradle-managed device needed.
    automaticGenerationDuringBuild = false
    saveInSrc = true            // writes app/src/main/generated/baselineProfiles/baseline-prof.txt, which is committed
}

dependencies {
    // existing ...
    baselineProfile(project(":benchmark"))
}
```
`profileinstaller` is already present, which installs the profile on first launch for sideloaded builds.

Release builds used for benchmarking: the plugin adds `benchmarkRelease` and `nonMinifiedRelease` variants. They inherit `release`, and when
`keystore.properties` is absent they are signed with the debug key, so benchmarking never needs the release key.

## 3. `benchmark/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.drift.benchmark"
    compileSdk = 36
    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
    // Animations off makes frame metrics comparable between runs.
    testOptions.animationsDisabled = true
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
```

## 4. `benchmark/src/main/AndroidManifest.xml`
Not needed (the plugin generates the instrumentation manifest). Do not add any permission; `checkNoInternetPermission` scans manifests.

## 5. `benchmark/src/main/java/app/drift/benchmark/DriftTargets.kt`
```kotlin
package app.drift.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** Package under test and the visible bottom-navigation labels. Keep in sync with the labels :app shows (A4/A5). */
object DriftTargets {
    const val PACKAGE = "app.drift"
    const val TAB_ENERGY = "Energy"
    const val TAB_SLEEP = "Sleep"
    const val TAB_HISTORY = "History"
    const val FIRST_FRAME_TIMEOUT_MS = 10_000L
}

fun UiDevice.openTab(label: String) {
    val tab = wait(Until.findObject(By.text(label)), DriftTargets.FIRST_FRAME_TIMEOUT_MS)
        ?: error("Tab '$label' not found; update DriftTargets to the app's labels")
    tab.click()
    waitForIdle()
}

/** Scrolls the main scrollable container of the current screen down and back up. */
fun UiDevice.scrollCurrentScreen() {
    val list = wait(Until.findObject(By.scrollable(true)), DriftTargets.FIRST_FRAME_TIMEOUT_MS)
        ?: error("No scrollable content on this screen")
    list.setGestureMargin(displayWidth / 10)
    repeat(3) { list.fling(androidx.test.uiautomator.Direction.DOWN); waitForIdle() }
    repeat(3) { list.fling(androidx.test.uiautomator.Direction.UP); waitForIdle() }
}
```
The benchmark requires the app to be past onboarding with data. Seed this with the debug-only import path or pre-load a device snapshot;
the simplest repeatable way is `adb shell run-as`-free: install the release candidate, complete onboarding manually once on the benchmark
phone with a `Scenarios.steadySleeper` replay import (Settings > Import), then run the benchmarks (they use `StartupMode.COLD`, which does not clear app data).

## 6. `benchmark/src/main/java/app/drift/benchmark/StartupBenchmark.kt`
```kotlin
package app.drift.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start to first frame. The pass criterion (median timeToInitialDisplayMs < 500) is asserted by
 * benchmark/check_results.py against the benchmarkData.json this run produces.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    /** With the baseline profile (what users get). This is the number that must be under 500 ms. */
    @Test fun coldStartWithBaselineProfile() = start(CompilationMode.Partial())

    /** Without a profile, to quantify what the profile buys. Informational only. */
    @Test fun coldStartNoCompilation() = start(CompilationMode.None())

    private fun start(mode: CompilationMode) = rule.measureRepeated(
        packageName = DriftTargets.PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = mode,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
```

## 7. `benchmark/src/main/java/app/drift/benchmark/ScrollBenchmark.kt`
```kotlin
package app.drift.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Jank while scrolling Energy, Sleep and History. Criterion (< 1% janky frames) is asserted by check_results.py. */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun scrollEnergy() = scroll(DriftTargets.TAB_ENERGY)
    @Test fun scrollSleep() = scroll(DriftTargets.TAB_SLEEP)
    @Test fun scrollHistory() = scroll(DriftTargets.TAB_HISTORY)

    private fun scroll(tab: String) = rule.measureRepeated(
        packageName = DriftTargets.PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.openTab(tab)
        },
    ) {
        device.scrollCurrentScreen()
    }
}
```

## 8. `benchmark/src/main/java/app/drift/benchmark/BaselineProfileGenerator.kt`
```kotlin
package app.drift.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Critical user journeys: launch, each main tab, scrolling. Run with `./gradlew :app:generateBaselineProfile`. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = DriftTargets.PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.scrollCurrentScreen()                       // Energy (home)
        device.openTab(DriftTargets.TAB_SLEEP); device.scrollCurrentScreen()
        device.openTab(DriftTargets.TAB_HISTORY); device.scrollCurrentScreen()
        device.openTab(DriftTargets.TAB_ENERGY)
    }
}
```

## 9. `benchmark/check_results.py` (the pass/fail gate for checklist items 5 and 6)
Reads the JSON that AGP pulls from the device and exits non-zero on failure.
```python
#!/usr/bin/env python3
"""Fail if cold start >= 500 ms (median) or any scroll benchmark has >= 1% janky frames."""
import glob, json, statistics, sys

START_LIMIT_MS = 500.0
JANK_LIMIT = 0.01

def flatten(x):
    for v in x:
        if isinstance(v, list):
            yield from flatten(v)
        else:
            yield v

paths = sorted(glob.glob("benchmark/build/outputs/**/*benchmarkData.json", recursive=True))
if not paths:
    sys.exit("no benchmarkData.json found; run :benchmark:connectedBenchmarkReleaseAndroidTest first")

failed = False
for path in paths:
    for b in json.load(open(path))["benchmarks"]:
        name = b["name"]
        if name == "coldStartWithBaselineProfile":
            median = b["metrics"]["timeToInitialDisplayMs"]["median"]
            ok = median < START_LIMIT_MS
            print(f"{'PASS' if ok else 'FAIL'} {name}: median timeToInitialDisplayMs = {median:.0f} ms (< {START_LIMIT_MS:.0f})")
            failed |= not ok
        elif name.startswith("scroll"):
            overruns = list(flatten(b["sampledMetrics"]["frameOverrunMs"]["runs"]))
            janky = sum(1 for o in overruns if o > 0) / max(1, len(overruns))
            ok = janky < JANK_LIMIT
            print(f"{'PASS' if ok else 'FAIL'} {name}: {janky:.2%} janky of {len(overruns)} frames (< {JANK_LIMIT:.0%})")
            failed |= not ok
sys.exit(1 if failed else 0)
```
Field names (`metrics.timeToInitialDisplayMs.median`, `sampledMetrics.frameOverrunMs.runs`) match Macrobenchmark 1.4; if a future version
renames them, the script fails loudly on the `KeyError` instead of passing silently.

## 10. Running
```
source scripts/env.sh
./gradlew :app:generateBaselineProfile                    # writes app/src/main/generated/baselineProfiles/ (commit it)
./gradlew :benchmark:tasks --all | grep -i connected      # find the exact connected-test task name for the benchmarkRelease variant
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
python3 benchmark/check_results.py
```
Needs a physical device (emulator numbers are not valid for the checklist) with USB debugging; the reference phone is open decision 3.
Keep the result JSON with the release candidate evidence.

## 11. Why these thresholds hold up
- 500 ms is measured with `CompilationMode.Partial()` (profile applied), which is what installed users see after profile install.
- Janky frame = frame with `frameOverrunMs > 0` (missed its deadline), a stricter definition than "over 16.7 ms" on 90/120 Hz phones.
- 10 iterations per benchmark; the median is used for startup because first-iteration outliers (disk cache) are expected.
