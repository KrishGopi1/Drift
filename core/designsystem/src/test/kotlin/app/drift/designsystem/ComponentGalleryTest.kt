package app.drift.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.drift.model.Confidence
import app.drift.model.ConfidenceLevel
import app.drift.model.Provenance
import app.drift.model.Quantity
import app.drift.model.repository.SourceFix
import app.drift.model.ui.InsufficientReason
import app.drift.model.ui.ScreenState
import java.time.Duration

private val sleepNeed = Quantity(
    Duration.ofMinutes(465), Provenance.ESTIMATED,
    Confidence(Duration.ofMinutes(440), Duration.ofMinutes(495), 0.9, ConfidenceLevel.MEDIUM),
)

@Composable private fun Stack(content: @Composable () -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }

class CardsGallery : GalleryCase("cards") {
    @Composable override fun Content() = Stack {
        SectionHeader("Today")
        DriftCard { Text("Default card", style = DriftTheme.typography.titleSmall); Text("Body text inside a card.", style = DriftTheme.typography.body) }
        DriftCard(tone = CardTone.Raised, onClick = {}, onClickLabel = "Open") { Text("Raised clickable card", style = DriftTheme.typography.titleSmall) }
        DriftCard(tone = CardTone.Accent) { Text("Accent card", style = DriftTheme.typography.titleSmall) }
    }
}

class MetricHeroGallery : GalleryCase("metric_hero") {
    @Composable override fun Content() = Stack {
        DriftCard {
            DurationHero(Duration.ofMinutes(465), "Sleep need", provenance = Provenance.ESTIMATED, supporting = "Based on 21 nights")
        }
        DriftCard { MetricHero(78.0, "SRI", "Regularity", provenance = Provenance.DERIVED) }
        DriftCard { MetricHero(7.8, "h", "Average sleep", decimals = 1, provenance = Provenance.MEASURED) }
    }
}

class ConfidenceBandGallery : GalleryCase("confidence_band") {
    @Composable override fun Content() = Stack {
        DriftCard { DurationConfidenceBand(sleepNeed) }
        DriftCard {
            ConfidenceBand(
                value = 6.0, lower = 4.0, upper = 9.0, scaleMin = 0.0, scaleMax = 10.0,
                valueText = "23:40", bandText = confidenceBandText("1 h 15 min", "22:25", "00:55", 0.8), level = ConfidenceLevel.LOW,
            )
        }
    }
}

class ProvenanceChipGallery : GalleryCase("provenance_chip") {
    @Composable override fun Content() = Stack {
        Provenance.entries.forEach { ProvenanceChip(it) }
        ProvenanceChip(Provenance.DERIVED, onClick = {})
    }
}

class SegmentedControlGallery : GalleryCase("segmented_control") {
    @Composable override fun Content() = Stack {
        SegmentedControl(listOf("Week", "Month", "3 months"), 0, {})
        SegmentedControl(listOf("Yesterday", "Today", "Tomorrow"), 1, {})
    }
}

class ButtonsGallery : GalleryCase("buttons") {
    @Composable override fun Content() = Stack {
        DriftButton("Continue", {})
        DriftButton("Not now", {}, style = ButtonStyle.Secondary)
        DriftButton("Learn more", {}, style = ButtonStyle.Text)
        DriftButton("Unavailable", {}, enabled = false)
    }
}

class BottomSheetGallery : GalleryCase("bottom_sheet") {
    @Composable override fun Content() = DriftCard(tone = CardTone.Raised) {
        SheetHandle()
        SheetBody("Why this changed", onClose = {}) {
            Text("Your sleep debt rose by 45 min because Tuesday and Wednesday were both under 6 h.", style = DriftTheme.typography.body)
            ProvenanceChip(Provenance.DERIVED)
        }
    }
}

class PermissionPanelGallery : GalleryCase("permission_panel") {
    @Composable override fun Content() = PermissionPanel(
        title = "Read sleep from Health Connect",
        body = "Drift reads sleep sessions and stays on your phone. There is no account and no network access.",
        actionLabel = "Grant access", onAction = {},
        points = listOf("Sleep sessions and stages", "Used only to compute your schedule"),
        secondaryLabel = "Not now", onSecondary = {},
    )
}

class InsufficientPanelGallery : GalleryCase("insufficient_panel") {
    @Composable override fun Content() = Stack {
        InsufficientDataPanel(InsufficientReason.TOO_FEW_NIGHTS, 3)
        InsufficientDataPanel(InsufficientReason.NO_NIGHTS_YET, 0)
    }
}

class ErrorPanelGallery : GalleryCase("error_panel") {
    @Composable override fun Content() = Stack {
        ErrorPanel("The sleep database could not be opened.", onRetry = {})
        ErrorPanel("This cannot be retried right now.")
    }
}

class SourceProblemPanelGallery : GalleryCase("source_problem_panel") {
    @Composable override fun Content() = SourceProblemPanel("Health Connect has not shared any sleep for 3 days.", SourceFix.OpenAppSettings, {})
}

class SkeletonGallery : GalleryCase("skeleton") {
    @Composable override fun Content() = LoadingSkeleton()
}

class ListRowGallery : GalleryCase("list_rows") {
    @Composable override fun Content() = DriftCard {
        DriftListRow("Wake time", subtitle = "07:10 from 12 nights", provenance = Provenance.DERIVED, onClick = {})
        DriftDivider()
        DriftListRow("Source", subtitle = "Health Connect")
        DriftDivider()
        DriftSwitchRow("Wind-down reminder", checked = true, onCheckedChange = {}, subtitle = "45 min before the melatonin window")
        DriftDivider()
        DriftSwitchRow("Caffeine cutoff", checked = false, onCheckedChange = {})
    }
}

class TopBarGallery : GalleryCase("top_bar") {
    @Composable override fun Content() = Stack {
        DriftTopBar("Sleep", subtitle = "Last 14 nights")
        DriftTopBar("Night detail", onBack = {}, actions = { DriftIconButton(DriftIcons.Refresh, "Refresh", {}) })
    }
}

class BottomNavGallery : GalleryCase("bottom_nav") {
    @Composable override fun Content() = Stack {
        DriftBottomNavBar(driftDefaultNavItems(), 0, {})
        DriftBottomNavBar(driftDefaultNavItems(), 2, {})
    }
}

class IconsGallery : GalleryCase("icons") {
    @Composable override fun Content() = androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            DriftIcons.Back, DriftIcons.Close, DriftIcons.ChevronRight, DriftIcons.Check, DriftIcons.Refresh, DriftIcons.Info,
            DriftIcons.Warning, DriftIcons.Energy, DriftIcons.Moon, DriftIcons.BodyClock, DriftIcons.Settings, DriftIcons.Shield,
        ).forEach { androidx.compose.material3.Icon(it, null, tint = DriftTheme.colors.onSurface, modifier = Modifier.then(Modifier)) }
    }
}

class ScreenStateLoadingGallery : GalleryCase("state_loading") {
    @Composable override fun Content() = ScreenStateHost<Unit>(ScreenState.Loading) {}
}

class ScreenStateFirstUseGallery : GalleryCase("state_first_use") {
    @Composable override fun Content() = ScreenStateHost<Unit>(ScreenState.FirstUse(2)) {}
}

class ScreenStateInsufficientGallery : GalleryCase("state_insufficient") {
    @Composable override fun Content() = ScreenStateHost<Unit>(ScreenState.Insufficient(InsufficientReason.TOO_FEW_NIGHTS, 4)) {}
}

class ScreenStateReadyGallery : GalleryCase("state_ready") {
    @Composable override fun Content() = ScreenStateHost(ScreenState.Ready(sleepNeed)) {
        DriftCard { DurationHero(it.value, "Sleep need", provenance = it.provenance) }
    }
}

class ScreenStateSourceProblemGallery : GalleryCase("state_source_problem") {
    @Composable override fun Content() = ScreenStateHost<Unit>(
        ScreenState.SourceProblem(SourceFix.GrantHealthConnectPermissions(setOf("sleep")), "Drift lost access to Health Connect sleep data."),
    ) {}
}

class ScreenStateErrorGallery : GalleryCase("state_error") {
    @Composable override fun Content() = ScreenStateHost<Unit>(ScreenState.Error("The analysis could not run.", retryable = true)) {}
}
