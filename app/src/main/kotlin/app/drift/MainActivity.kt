package app.drift

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drift.designsystem.DriftTheme
import app.drift.navigation.DeepLink
import app.drift.navigation.DriftNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val deepLink = mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.onboardingComplete.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = DeepLink.from(intent)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val onboarded by viewModel.onboardingComplete.collectAsStateWithLifecycle()
            DriftTheme(themeMode = settings.theme) {
                onboarded?.let { done ->
                    DriftNavHost(
                        onboardingComplete = done,
                        deepLink = deepLink.value,
                        onDeepLinkHandled = { deepLink.value = null },
                        onOnboardingFinished = viewModel::onOnboardingFinished,
                        onResetComplete = viewModel::onResetComplete,
                        onSourcesMayHaveChanged = viewModel::recheckSources,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions can be changed in system settings while Drift is in the background.
        viewModel.recheckSources()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLink.value = DeepLink.from(intent)
    }
}
