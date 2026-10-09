package app.drift.feature.sleep.common

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.staggeredEntrance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The 24-hour flag: the user's setting, or the system's when the setting is "follow system" (null). */
@Composable
fun rememberUse24h(setting: Boolean?): Boolean = setting ?: DateFormat.is24HourFormat(LocalContext.current)

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/** "Thu 8 Oct". */
@Composable
fun LocalDate.shortLabel(): String = DateTimeFormatter.ofPattern("EEE d MMM", currentLocale()).format(this)

/** "Thursday 8 October". */
@Composable
fun LocalDate.longLabel(): String = DateTimeFormatter.ofPattern("EEEE d MMMM", currentLocale()).format(this)

fun Instant.clockIn(zone: ZoneId, use24h: Boolean): String = DriftFormat.clock(this, zone, use24h)

/**
 * Standard screen body: a top bar and a gutter-padded column with 16 dp between cards. [scrollable] is turned off by
 * screenshot tests so the whole screen is captured at its natural height.
 */
@Composable
fun ScreenBody(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.then(if (scrollable) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).background(DriftTheme.colors.background)) {
        topBar()
        Column(
            Modifier
                .then(if (scrollable) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier)
                .fillMaxWidth()
                .padding(horizontal = DriftTheme.spacing.gutter, vertical = DriftTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sectionGap),
            content = content,
        )
        if (bottomBar != null) {
            Box(Modifier.fillMaxWidth().background(DriftTheme.colors.surface).padding(DriftTheme.spacing.gutter, DriftTheme.spacing.md)) { bottomBar() }
        }
    }
}

/** Entrance animation index helper so cards stagger in order. */
@Composable
fun Modifier.entrance(index: Int): Modifier = this.staggeredEntrance(index)
