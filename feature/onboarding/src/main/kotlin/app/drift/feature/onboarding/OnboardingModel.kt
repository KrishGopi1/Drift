package app.drift.feature.onboarding

import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.engine.DayCurve
import app.drift.model.engine.SufficiencyTier
import app.drift.model.repository.ImportReport
import app.drift.model.repository.SourceInfo
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** The eight screens of the flow, in order. */
enum class OnboardingStep { WELCOME, HOW_IT_WORKS, SCHEDULE, ABOUT_YOU, SOURCES, PERMISSIONS, IMPORT, REVEAL }

/** One permission request at a time. Declaration order is the order they are shown in. */
enum class PermissionPage { HEALTH_CONNECT, ACTIVITY_RECOGNITION, USAGE_ACCESS, NOTIFICATIONS }

/** Answers to the schedule and "about you" questions. Times are minutes after local midnight. */
data class ProfileForm(
    val workdays: Set<DayOfWeek> = DEFAULT_WORKDAYS,
    val wakeMinutes: Int = DEFAULT_WAKE,
    val freeBedMinutes: Int = DEFAULT_FREE_BED,
    val freeWakeMinutes: Int = DEFAULT_FREE_WAKE,
    val ageBand: AgeBand = AgeBand.AGE_26_35,
    val chronotype: ChronotypeAnswer? = null,
) {
    companion object {
        /** Starting values shown in the question controls. They are inputs the user edits, not measurements. */
        val DEFAULT_WORKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        const val DEFAULT_WAKE = 7 * 60
        const val DEFAULT_FREE_BED = 23 * 60 + 30
        const val DEFAULT_FREE_WAKE = 8 * 60
    }
}

/** Progress of the first history read (step 7). */
sealed interface HistoryImport {
    data object Idle : HistoryImport
    /** [progress] is 0..1, or null while the repository has not reported any yet. */
    data class Running(val progress: Float?) : HistoryImport
    data class Done(val newSessions: Int, val errors: Map<String, String>) : HistoryImport
    data class Failed(val message: String) : HistoryImport
    data object Cancelled : HistoryImport
}

enum class ErrorKind { SAVE, FINISH, GENERIC }

/** A failure shown above the current step. [detail] is the exception message and may be empty. */
data class StepError(val kind: ErrorKind, val detail: String)

/** The optional Sleep as Android backup import inside step 7. */
sealed interface BackupImport {
    data object Idle : BackupImport
    data object Running : BackupImport
    data class Done(val report: ImportReport) : BackupImport
    /** [message] is null when the file could not be opened at all. */
    data class Failed(val message: String?) : BackupImport
}

/** The first energy-curve reveal (step 8). */
sealed interface Reveal {
    data object Idle : Reveal
    data object Loading : Reveal
    data class Ready(
        /** Null when the snapshot holds no curve at all. */
        val day: DayCurve?,
        val tier: SufficiencyTier,
        val nightsNeeded: Int,
        val now: Instant,
        val zone: ZoneId,
    ) : Reveal
    data class Failed(val message: String) : Reveal
}

/** Ready model for the onboarding screen. */
data class OnboardingModel(
    val step: OnboardingStep,
    val form: ProfileForm,
    val sources: List<SourceInfo>,
    val phoneDetection: Boolean,
    val use24h: Boolean?,
    val permissionPage: PermissionPage,
    /** Pages that apply to the current choices, in order. */
    val permissionPages: List<PermissionPage>,
    val notificationsGranted: Boolean?,
    val history: HistoryImport,
    val backup: BackupImport,
    val reveal: Reveal,
    val error: StepError?,
    val busy: Boolean,
) {
    val stepNumber: Int get() = step.ordinal + 1
    val stepCount: Int get() = OnboardingStep.entries.size
    val canGoBack: Boolean get() = step != OnboardingStep.WELCOME

    /** Sessions read from sources plus nights from an imported backup. */
    val nightsFound: Int
        get() = ((history as? HistoryImport.Done)?.newSessions ?: 0) + ((backup as? BackupImport.Done)?.report?.nightsImported ?: 0)
}
