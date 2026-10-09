# a3: how NeedsPermission strings map to SourceFix

**Problem.** `SourceStatus.NeedsPermission(missing: Set<String>)` carries free-form strings, while
`SourceInfo.fix` is a typed `SourceFix`. The phone adapter has two kinds of "missing permission" and one of
them (usage access) is not a runtime permission, so `GrantRuntimePermissions` does not fit it.

**What A3 does.** `PhoneSourceAdapter.status()` returns
`NeedsPermission(setOf("android.permission.PACKAGE_USAGE_STATS", "android.permission.ACTIVITY_RECOGNITION"))`
containing only what is missing (constant `PhoneSourceAdapter.PERMISSION_USAGE_ACCESS`).

**Proposed mapping (A2 in `SourceRepository`, wired in `:app`).**
- contains `android.permission.PACKAGE_USAGE_STATS` -> `SourceFix.GrantUsageAccess` (opens `Settings.ACTION_USAGE_ACCESS_SETTINGS`)
- contains `android.permission.ACTIVITY_RECOGNITION` -> `SourceFix.GrantRuntimePermissions(setOf(ACTIVITY_RECOGNITION))`
- both -> show usage access first, then the runtime prompt (a single `SourceFix` cannot express both).

**Blocks.** Nothing; the phone source keeps working with reduced confidence when either is missing.
A0 may want a `SourceFix` that can carry several steps, or an ordered list on `SourceInfo`.
