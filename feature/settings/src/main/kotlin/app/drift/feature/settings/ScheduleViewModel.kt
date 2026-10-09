package app.drift.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.engine.Schedule
import app.drift.model.engine.UserProfile
import app.drift.model.repository.ProfileRepository
import app.drift.model.ui.ScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

/** Edits the saved [UserProfile]. Goals are carried over untouched. */
@HiltViewModel
class ScheduleViewModel @Inject constructor(private val profiles: ProfileRepository) : ViewModel() {
    private val _state = MutableStateFlow<ScreenState<ScheduleModel>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<ScheduleModel>> = _state

    private val savedEvents = Channel<Unit>(Channel.BUFFERED)
    /** Fires once after the profile was saved. */
    val saved: Flow<Unit> = savedEvents.receiveAsFlow()

    private var original: UserProfile? = null

    init { load() }

    fun load() {
        _state.value = ScreenState.Loading
        viewModelScope.launch {
            try {
                val p = profiles.observeProfile().first()
                original = p
                _state.value = ScreenState.Ready(ScheduleModel(p?.toForm() ?: ScheduleForm.Default, saving = false, saveError = null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ScreenState.Error(e.message.orEmpty(), retryable = true)
            }
        }
    }

    private fun edit(transform: (ScheduleForm) -> ScheduleForm) {
        val s = _state.value as? ScreenState.Ready ?: return
        _state.value = ScreenState.Ready(s.model.copy(form = transform(s.model.form), saveError = null))
    }

    fun toggleWorkday(day: DayOfWeek) = edit { f -> f.copy(workdays = if (day in f.workdays) f.workdays - day else f.workdays + day) }
    fun setWake(minutes: Int) = edit { it.copy(wakeMinutes = minutes.mod(MINUTES_PER_DAY)) }
    fun setFreeBed(minutes: Int) = edit { it.copy(freeBedMinutes = minutes.mod(MINUTES_PER_DAY)) }
    fun setFreeWake(minutes: Int) = edit { it.copy(freeWakeMinutes = minutes.mod(MINUTES_PER_DAY)) }
    fun setAgeBand(band: AgeBand) = edit { it.copy(ageBand = band) }
    fun setChronotype(answer: ChronotypeAnswer?) = edit { it.copy(chronotype = answer) }

    fun save() {
        val s = _state.value as? ScreenState.Ready ?: return
        if (s.model.saving) return
        _state.value = ScreenState.Ready(s.model.copy(saving = true, saveError = null))
        viewModelScope.launch {
            try {
                profiles.saveProfile(s.model.form.toProfile(original))
                _state.value = ScreenState.Ready(s.model.copy(saving = false))
                savedEvents.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ScreenState.Ready(s.model.copy(saving = false, saveError = e.message.orEmpty()))
            }
        }
    }

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60

        private fun LocalTime?.minutes(default: Int) = this?.let { it.hour * 60 + it.minute } ?: default

        internal fun UserProfile.toForm() = ScheduleForm(
            workdays = schedule.workdays,
            wakeMinutes = schedule.wakeTime.minutes(ScheduleForm.Default.wakeMinutes),
            freeBedMinutes = schedule.freeDayBedtime.minutes(ScheduleForm.Default.freeBedMinutes),
            freeWakeMinutes = schedule.freeDayWakeTime.minutes(ScheduleForm.Default.freeWakeMinutes),
            ageBand = ageBand, chronotype = chronotype,
        )

        internal fun ScheduleForm.toProfile(base: UserProfile?) = UserProfile(
            ageBand = ageBand,
            chronotype = chronotype,
            schedule = Schedule(
                workdays = workdays,
                wakeTime = if (workdays.isEmpty()) null else LocalTime.of(wakeMinutes / 60, wakeMinutes % 60),
                freeDayBedtime = LocalTime.of(freeBedMinutes / 60, freeBedMinutes % 60),
                freeDayWakeTime = LocalTime.of(freeWakeMinutes / 60, freeWakeMinutes % 60),
            ),
            goals = base?.goals ?: app.drift.model.engine.Goals(),
        )
    }
}
