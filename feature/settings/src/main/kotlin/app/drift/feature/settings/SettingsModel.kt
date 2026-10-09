package app.drift.feature.settings

import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.repository.AppSettings
import app.drift.model.repository.ImportReport
import app.drift.model.repository.LocalBackup
import app.drift.model.repository.SourceInfo
import java.time.DayOfWeek
import java.time.ZoneId

enum class NoticeKind {
    SYNCED, SYNC_FAILED, SYNC_SOURCE_ERRORS, EXPORTED, EXPORT_FAILED, BACKUP_CREATED, BACKUP_FAILED, RESTORED, RESTORE_FAILED, RESET_FAILED,
}

/** A short status message shown above the settings list until dismissed. [detail] is a count or an error message. */
data class Notice(val kind: NoticeKind, val detail: String = "")

/** Where an import came from, so the failure dialog can offer the right picker again. */
enum class ImportKind { SLEEP_AS_ANDROID, DRIFT_EXPORT }

/** Result of an import, shown in a dialog. [message] is null when the file could not be opened at all. */
sealed interface ImportOutcome {
    val kind: ImportKind
    data class Success(override val kind: ImportKind, val report: ImportReport) : ImportOutcome
    data class Failure(override val kind: ImportKind, val message: String?) : ImportOutcome
}

data class SettingsModel(
    val sources: List<SourceInfo>,
    val settings: AppSettings,
    val backups: List<LocalBackup>,
    val syncing: Boolean,
    /** True while an export, import, backup, restore or reset is running. */
    val busy: Boolean,
    val notice: Notice?,
    val importOutcome: ImportOutcome?,
    val zone: ZoneId,
)

/** Editable copy of the profile. Times are minutes after local midnight. */
data class ScheduleForm(
    val workdays: Set<DayOfWeek>,
    val wakeMinutes: Int,
    val freeBedMinutes: Int,
    val freeWakeMinutes: Int,
    val ageBand: AgeBand,
    val chronotype: ChronotypeAnswer?,
) {
    companion object {
        /** Starting answers when no profile has been saved yet. */
        val Default = ScheduleForm(
            workdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            wakeMinutes = 7 * 60, freeBedMinutes = 23 * 60 + 30, freeWakeMinutes = 8 * 60,
            ageBand = AgeBand.AGE_26_35, chronotype = null,
        )
    }
}

data class ScheduleModel(val form: ScheduleForm, val saving: Boolean, val saveError: String?)
