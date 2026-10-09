package app.drift.testing.fixtures

import app.drift.model.repository.SourceFix
import app.drift.model.ui.InsufficientReason
import app.drift.model.ui.ScreenState

/**
 * All six C5 [ScreenState] variants for any ready model.
 *
 * ```
 * ScreenStateFixtures.all(energyModel).forEach { (name, state) -> screenshot(name) { EnergyScreen(state) } }
 * ```
 */
object ScreenStateFixtures {
    /** Stable names, in the order the plan lists them (6.4). */
    val NAMES = listOf("loading", "first_use", "insufficient", "ready", "source_problem", "error")

    fun <T> loading(): ScreenState<T> = ScreenState.Loading
    fun <T> firstUse(stepsRemaining: Int = 3): ScreenState<T> = ScreenState.FirstUse(stepsRemaining)
    fun <T> insufficient(reason: InsufficientReason = InsufficientReason.TOO_FEW_NIGHTS, daysNeeded: Int = 3): ScreenState<T> =
        ScreenState.Insufficient(reason, daysNeeded)
    fun <T> ready(model: T): ScreenState<T> = ScreenState.Ready(model)
    fun <T> sourceProblem(
        fix: SourceFix = SourceFix.GrantHealthConnectPermissions(setOf("android.permission.health.READ_SLEEP")),
        message: String = "Drift can't read sleep from Health Connect yet.",
    ): ScreenState<T> = ScreenState.SourceProblem(fix, message)
    fun <T> error(message: String = "Something went wrong while loading your data.", retryable: Boolean = true): ScreenState<T> =
        ScreenState.Error(message, retryable)

    /** Name to state, one entry per variant, ordered as [NAMES]. */
    fun <T> all(model: T): Map<String, ScreenState<T>> = linkedMapOf(
        "loading" to loading(),
        "first_use" to firstUse(),
        "insufficient" to insufficient(),
        "ready" to ready(model),
        "source_problem" to sourceProblem(),
        "error" to error(),
    )

    /** Just the states, for parameterized tests. */
    fun <T> states(model: T): List<ScreenState<T>> = all(model).values.toList()
}
