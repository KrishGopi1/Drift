# A2: CSV import seam between :core:data and :source:sleepasandroid

**Problem.** `SourceRepository.importSleepAsAndroidBackup(InputStream)` lives in `:core:data`, but the CSV parser
lives in `:source:sleepasandroid`. Dependency rules forbid `:core:data` -> `:source:*` and `:source:*` -> `:core:data`,
and `:core:model` has no interface for "an adapter that can import a file".

**Workaround (implemented).** `:core:data` declares `app.drift.data.repo.BackupImporter` (sourceId + `parse(InputStream): ImportBatch`)
and takes a multibound `Set<BackupImporter>` (`@Multibinds`, so empty is fine; with no importer the import call returns an
`ImportReport` with a warning instead of crashing). `:source:sleepasandroid` exposes
`SleepAsAndroidSourceAdapter.importBackup(InputStream): SleepAsAndroidImport` using only model types.

**What `:app` must add (about ten lines):**

```kotlin
class SleepAsAndroidBackupImporter @Inject constructor(private val adapter: SleepAsAndroidSourceAdapter) : BackupImporter {
    override val sourceId = SourceId.SLEEP_AS_ANDROID
    override suspend fun parse(input: InputStream) =
        adapter.importBackup(input).let { ImportBatch(it.episodes, it.signals, it.rowsRejected, it.warnings) }
}
// in a Hilt module: @Binds @IntoSet abstract fun saaImporter(i: SleepAsAndroidBackupImporter): BackupImporter
```

**Proposed contract change (optional, for A0).** Add to `:core:model/source` an `interface BackupImportable { suspend fun importBackup(input: InputStream): PullResult-like }`
so `:core:data` can find it on the `SourceAdapter` set without glue.

**Blocks.** Nothing; the Settings "Import Sleep as Android backup" button works once `:app` binds the importer above.

# A2: Sleep as Android broadcast action names are unverified

The vendor developer pages (docs.sleep.urbandroid.org) could not be fetched from this environment (TLS error). The action
`com.urbandroid.sleep.alarmclock.SLEEP_TRACKING_STOPPED` comes from a third-party reproduction of the Tasker event list;
`com.urbandroid.sleep.alarmclock.ALARM_ALERT_DISMISS` is the documented alarm-dismissed event. Verify against the current
Sleep as Android docs on a device (`adb shell dumpsys activity broadcasts` while stopping tracking) and edit
`SleepAsAndroidActions` plus the manifest `intent-filter`. Also note Android 8+ drops most implicit broadcasts for
manifest receivers; `:app` should call `SleepAsAndroidBroadcastReceiver.register(context)` while the process is alive.
