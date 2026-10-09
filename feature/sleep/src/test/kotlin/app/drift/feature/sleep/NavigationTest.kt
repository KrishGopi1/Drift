package app.drift.feature.sleep

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.feature.sleep.navigation.BODY_CLOCK_ROUTE
import app.drift.feature.sleep.navigation.HISTORY_ROUTE
import app.drift.feature.sleep.navigation.NIGHT_PATTERN
import app.drift.feature.sleep.navigation.SLEEP_ROUTE
import app.drift.feature.sleep.navigation.WHY_PATTERN
import app.drift.feature.sleep.navigation.navigateToNight
import app.drift.feature.sleep.navigation.navigateToWhy
import app.drift.model.EpisodeId
import app.drift.model.engine.MetricKey
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NavigationTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var nav: NavHostController
    private var shownArg by mutableStateOf("")

    private fun host() = rule.setContent {
        nav = rememberNavController()
        NavHost(nav, startDestination = SLEEP_ROUTE) {
            composable(SLEEP_ROUTE) { Text("sleep") }
            composable(HISTORY_ROUTE) { Text("history") }
            composable(BODY_CLOCK_ROUTE) { Text("body") }
            composable(NIGHT_PATTERN) { shownArg = "night:" + it.arguments?.getString("episodeId"); Text(shownArg) }
            composable(WHY_PATTERN) { shownArg = "why:" + it.arguments?.getString("metric"); Text(shownArg) }
        }
    }

    @Test fun routesAreDistinct() {
        assertThat(setOf(SLEEP_ROUTE, HISTORY_ROUTE, BODY_CLOCK_ROUTE)).hasSize(3)
    }

    @Test fun navigateToNightCarriesTheId() {
        host()
        rule.runOnIdle { nav.navigateToNight(EpisodeId("hc:abc/1")) }
        rule.waitForIdle()
        assertThat(shownArg).isEqualTo("night:hc:abc/1")
    }

    @Test fun navigateToWhyCarriesTheMetric() {
        host()
        MetricKey.entries.forEach { m ->
            rule.runOnIdle { nav.navigateToWhy(m) }
            rule.waitForIdle()
            assertThat(shownArg).isEqualTo("why:${m.name}")
        }
    }
}
