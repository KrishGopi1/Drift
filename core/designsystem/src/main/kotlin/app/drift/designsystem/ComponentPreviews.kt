package app.drift.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.drift.model.Confidence
import app.drift.model.ConfidenceLevel
import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.repository.SourceFix
import app.drift.model.repository.ThemeMode
import app.drift.model.ui.InsufficientReason
import app.drift.model.ui.ScreenState
import java.time.Duration

// Preview-only sample values for the Android Studio preview pane. Not used by the app.

@Composable
private fun PreviewFrame(dark: Boolean, content: @Composable () -> Unit) {
    DriftTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, reducedMotion = true) {
        Column(
            Modifier.background(DriftTheme.colors.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun CardsAndHeroes() {
    DriftCard {
        DurationHero(Duration.ofMinutes(465), "Sleep need", provenance = Provenance.ESTIMATED)
        DurationConfidenceBand(
            Quantity(
                Duration.ofMinutes(465), Provenance.ESTIMATED,
                Confidence(Duration.ofMinutes(440), Duration.ofMinutes(490), 0.9, ConfidenceLevel.MEDIUM),
            ),
        )
    }
    DriftCard { MetricHero(value = 7.8, unit = "h", label = "Average sleep", decimals = 1, provenance = Provenance.DERIVED) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Provenance.entries.forEach { ProvenanceChip(it) }
    }
}

@Composable
private fun Controls() {
    var selected by remember { mutableIntStateOf(0) }
    SegmentedControl(listOf("Week", "Month", "3 months"), selected, { selected = it })
    DriftButton("Continue", {})
    DriftButton("Not now", {}, style = ButtonStyle.Secondary)
    DriftListRow("Wake time", subtitle = "Based on 12 nights", provenance = Provenance.DERIVED, onClick = {})
    DriftSwitchRow("Wind-down reminder", checked = true, onCheckedChange = {})
    DriftTopBar("Sleep", subtitle = "Last 14 nights", onBack = {})
    DriftBottomNavBar(driftDefaultNavItems(), 0, {})
}

@Composable
private fun Panels() {
    PermissionPanel("Read your sleep", "Drift reads sleep sessions from Health Connect. Nothing leaves your phone.", "Grant access", {}, points = listOf("Sleep sessions", "Heart rate during sleep"))
    InsufficientDataPanel(InsufficientReason.TOO_FEW_NIGHTS, 3)
    ErrorPanel("The database could not be opened.", onRetry = {})
    SourceProblemPanel("Health Connect has not shared any sleep in 3 days.", SourceFix.OpenAppSettings, {})
    SheetBody("Why this changed", onClose = {}) { Text("Two short nights this week raised your debt by 45 min.") }
    LoadingSkeleton()
}

@Preview(name = "Cards dark", widthDp = 360, heightDp = 640) @Composable private fun CardsDarkPreview() = PreviewFrame(true) { CardsAndHeroes() }
@Preview(name = "Cards light", widthDp = 360, heightDp = 640) @Composable private fun CardsLightPreview() = PreviewFrame(false) { CardsAndHeroes() }
@Preview(name = "Controls dark", widthDp = 360, heightDp = 800) @Composable private fun ControlsDarkPreview() = PreviewFrame(true) { Controls() }
@Preview(name = "Controls light", widthDp = 360, heightDp = 800) @Composable private fun ControlsLightPreview() = PreviewFrame(false) { Controls() }
@Preview(name = "Panels dark", widthDp = 360, heightDp = 1400) @Composable private fun PanelsDarkPreview() = PreviewFrame(true) { Panels() }
@Preview(name = "Panels light", widthDp = 360, heightDp = 1400) @Composable private fun PanelsLightPreview() = PreviewFrame(false) { Panels() }
@Preview(name = "Panels 200% font", widthDp = 360, heightDp = 2400, fontScale = 2f) @Composable private fun PanelsLargeFontPreview() = PreviewFrame(true) { Panels() }

@Preview(name = "State host insufficient", widthDp = 360) @Composable private fun HostPreview() = PreviewFrame(true) {
    ScreenStateHost<Unit>(ScreenState.Insufficient(InsufficientReason.NO_NIGHTS_YET, 3)) {}
}
