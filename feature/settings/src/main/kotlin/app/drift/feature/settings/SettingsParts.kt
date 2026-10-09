package app.drift.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftTheme
import app.drift.model.engine.AgeBand
import app.drift.model.engine.ChronotypeAnswer
import app.drift.model.repository.ImportReport
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun StatusLine(text: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (ok) DriftIcons.Check else DriftIcons.Warning, null,
            tint = if (ok) DriftTheme.colors.success else DriftTheme.colors.warning,
            modifier = Modifier.size(20.dp).padding(top = 2.dp),
        )
        Text(text, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurface)
    }
}

@Composable
internal fun ChoiceRow(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = DriftTheme.spacing.minTouch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = DriftTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
    ) {
        RadioButton(
            selected = selected, onClick = null, modifier = Modifier.clearAndSetSemantics {},
            colors = RadioButtonDefaults.colors(selectedColor = DriftTheme.colors.primary, unselectedColor = DriftTheme.colors.outline),
        )
        Text(text, style = DriftTheme.typography.body, color = DriftTheme.colors.onSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun <T> ChoiceGroup(label: String, options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xxs)) {
        Text(
            label, style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface,
            modifier = Modifier.padding(bottom = DriftTheme.spacing.xs).semantics { heading() },
        )
        options.forEach { (value, text) -> ChoiceRow(text, value == selected) { onSelect(value) } }
    }
}

@Composable
internal fun WorkdayChips(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val locale = Locale.getDefault()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm)) {
        DayOfWeek.entries.forEach { day ->
            val on = day in selected
            val full = day.getDisplayName(TextStyle.FULL, locale)
            val state = stringResource(if (on) R.string.st_day_selected else R.string.st_day_not_selected, full)
            androidx.compose.foundation.layout.Box(
                Modifier
                    .widthIn(min = DriftTheme.spacing.minTouch)
                    .heightIn(min = DriftTheme.spacing.minTouch)
                    .clip(RoundedCornerShape(DriftTheme.radii.pill))
                    .background(if (on) DriftTheme.colors.primaryContainer else DriftTheme.colors.surfaceRaised)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(day) })
                    .semantics { contentDescription = state }
                    .padding(horizontal = DriftTheme.spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.getDisplayName(TextStyle.SHORT, locale), style = DriftTheme.typography.label,
                    color = if (on) DriftTheme.colors.onPrimaryContainer else DriftTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
internal fun TimeStepper(label: String, minutes: Int, use24h: Boolean, onChange: (Int) -> Unit, stepMinutes: Int = 15) {
    val value = DriftFormat.clockMinutes(minutes, use24h)
    val earlier = stringResource(R.string.st_time_earlier_desc, label)
    val later = stringResource(R.string.st_time_later_desc, label)
    val valueDesc = stringResource(R.string.st_time_value_desc, label, value)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs)) {
        Text(label, style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            DriftButton(
                stringResource(R.string.st_earlier), { onChange(minutes - stepMinutes) }, style = ButtonStyle.Secondary,
                modifier = Modifier.semantics { contentDescription = earlier },
            )
            Text(
                value, style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface,
                modifier = Modifier.semantics { contentDescription = valueDesc }.widthIn(min = 72.dp),
            )
            DriftButton(
                stringResource(R.string.st_later), { onChange(minutes + stepMinutes) }, style = ButtonStyle.Secondary,
                modifier = Modifier.semantics { contentDescription = later },
            )
        }
    }
}

@Composable
internal fun ageBandLabel(band: AgeBand): String = stringResource(
    when (band) {
        AgeBand.AGE_18_25 -> R.string.st_age_18_25
        AgeBand.AGE_26_35 -> R.string.st_age_26_35
        AgeBand.AGE_36_50 -> R.string.st_age_36_50
        AgeBand.AGE_51_64 -> R.string.st_age_51_64
        AgeBand.AGE_65_PLUS -> R.string.st_age_65_plus
    },
)

@Composable
internal fun chronotypeLabel(answer: ChronotypeAnswer?): String = stringResource(
    when (answer) {
        ChronotypeAnswer.DEFINITELY_MORNING -> R.string.st_chrono_definitely_morning
        ChronotypeAnswer.MODERATELY_MORNING -> R.string.st_chrono_moderately_morning
        ChronotypeAnswer.NEITHER -> R.string.st_chrono_neither
        ChronotypeAnswer.MODERATELY_EVENING -> R.string.st_chrono_moderately_evening
        ChronotypeAnswer.DEFINITELY_EVENING -> R.string.st_chrono_definitely_evening
        null -> R.string.st_chrono_unsure
    },
)

@Composable
internal fun ImportReportLines(report: ImportReport) {
    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs)) {
        Text(
            pluralStringResource(R.plurals.st_report_nights, report.nightsImported, report.nightsImported),
            style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface,
        )
        val first = report.earliest
        val last = report.latest
        if (first != null && last != null) {
            Text(stringResource(R.string.st_report_range, first.toString(), last.toString()), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
        }
        Text(stringResource(R.string.st_report_duplicates, report.duplicatesSkipped), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
        if (report.rowsRejected > 0) {
            Text(stringResource(R.string.st_report_rejected, report.rowsRejected), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.warning)
        }
        report.warnings.forEach { Text(it, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.warning) }
    }
}
