package app.drift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drift.background.BackgroundScheduler
import app.drift.model.repository.AppSettings
import app.drift.model.repository.ProfileRepository
import app.drift.model.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Root state: theme and whether onboarding is done. Null while loading so the first frame never flashes the wrong graph. */
@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository,
    private val profiles: ProfileRepository,
    private val scheduler: BackgroundScheduler,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settings.observeSettings().stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val onboardingComplete: StateFlow<Boolean?> =
        profiles.observeOnboardingComplete().map<Boolean, Boolean?> { it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onOnboardingFinished() {
        viewModelScope.launch { scheduler.scheduleAll() }
    }

    fun onResetComplete() {
        viewModelScope.launch {
            scheduler.cancelAll()
            profiles.setOnboardingComplete(false)
        }
    }
}
