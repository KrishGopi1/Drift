package app.drift.model.ui

import app.drift.model.repository.SourceFix

/**
 * C5. Every screen's UI state. Screens render and screenshot-test all six variants in both themes.
 * [T] is the screen's own Ready model, defined in its feature module.
 */
sealed interface ScreenState<out T> {
    data object Loading : ScreenState<Nothing>
    data class FirstUse(val stepsRemaining: Int) : ScreenState<Nothing>
    data class Insufficient(val reason: InsufficientReason, val daysNeeded: Int) : ScreenState<Nothing>
    data class Ready<T>(val model: T) : ScreenState<T>
    data class SourceProblem(val fix: SourceFix, val message: String) : ScreenState<Nothing>
    data class Error(val message: String, val retryable: Boolean) : ScreenState<Nothing>
}

enum class InsufficientReason { NO_NIGHTS_YET, TOO_FEW_NIGHTS, TOO_MANY_MISSING_NIGHTS, NOT_ENOUGH_FREE_NIGHTS }
