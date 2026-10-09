package app.drift.feature.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftTheme
import app.drift.model.repository.ImportReport
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Progress dots: current step is a wider pill. The whole row is one focus stop announced as "Step n of N". */
@Composable
internal fun StepDots(step: Int, count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.ob_step_progress, step, count)
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val current = i + 1 == step
            Box(
                Modifier
                    .height(8.dp)
                    .width(if (current) 24.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (current) DriftTheme.colors.primary else DriftTheme.colors.outlineSubtle),
            )
        }
    }
}

/** 40 dp icon in a tinted circle, then the step title (a heading) and body text. */
@Composable
internal fun StepIntro(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(DriftTheme.colors.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = DriftTheme.colors.primary, modifier = Modifier.size(24.dp))
        }
        Text(
            title, style = DriftTheme.typography.headline, color = DriftTheme.colors.onSurface,
            modifier = titleModifier.semantics { heading() },
        )
        if (body != null) Text(body, style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted)
    }
}

@Composable
internal fun BulletRow(icon: ImageVector, title: String, text: String) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
    ) {
        Icon(icon, null, tint = DriftTheme.colors.energy, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xxs)) {
            Text(title, style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
            Text(text, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
        }
    }
}

/** One option of a single-choice question: radio button plus label, the whole row selects. */
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
            colors = RadioButtonDefaults.colors(
                selectedColor = DriftTheme.colors.primary, unselectedColor = DriftTheme.colors.outline,
            ),
        )
        Text(text, style = DriftTheme.typography.body, color = DriftTheme.colors.onSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun <T> ChoiceGroup(
    label: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xxs)) {
        Text(
            label, style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface,
            modifier = Modifier.padding(bottom = DriftTheme.spacing.xs).semantics { heading() },
        )
        options.forEach { (value, text) -> ChoiceRow(text, value == selected) { onSelect(value) } }
    }
}

/** Weekday toggles. Each chip announces its full day name and whether it is selected. */
@Composable
internal fun WorkdayChips(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val locale = Locale.getDefault()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
    ) {
        DayOfWeek.entries.forEach { day ->
            val on = day in selected
            val full = day.getDisplayName(TextStyle.FULL, locale)
            val state = stringResource(if (on) R.string.ob_day_selected else R.string.ob_day_not_selected, full)
            Box(
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
                    day.getDisplayName(TextStyle.SHORT, locale),
                    style = DriftTheme.typography.label,
                    color = if (on) DriftTheme.colors.onPrimaryContainer else DriftTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

/** A time question answered with Earlier / Later buttons (15 minute steps). */
@Composable
internal fun TimeStepper(
    label: String,
    minutes: Int,
    use24h: Boolean,
    onChange: (Int) -> Unit,
    stepMinutes: Int = 15,
) {
    val value = DriftFormat.clockMinutes(minutes, use24h)
    val earlierDescription = stringResource(R.string.ob_time_earlier_desc, label)
    val laterDescription = stringResource(R.string.ob_time_later_desc, label)
    val valueDescription = stringResource(R.string.ob_time_value_desc, label, value)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs)) {
        Text(label, style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            DriftButton(
                stringResource(R.string.ob_earlier), { onChange(minutes - stepMinutes) },
                style = ButtonStyle.Secondary,
                modifier = Modifier.semantics { contentDescription = earlierDescription },
            )
            Text(
                value, style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface,
                modifier = Modifier.semantics { contentDescription = valueDescription }.widthIn(min = 72.dp),
            )
            DriftButton(
                stringResource(R.string.ob_later), { onChange(minutes + stepMinutes) },
                style = ButtonStyle.Secondary,
                modifier = Modifier.semantics { contentDescription = laterDescription },
            )
        }
    }
}

/** Determinate or (when [progress] is null) indeterminate 6 dp pill bar. */
@Composable
internal fun ImportProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.ob_import_progress_desc)
    val reduced = DriftTheme.reducedMotion
    val shape = RoundedCornerShape(DriftTheme.radii.pill)
    val track = modifier
        .fillMaxWidth()
        .height(6.dp)
        .clip(shape)
        .background(DriftTheme.colors.surfaceRaised)
    if (progress != null) {
        val p = progress.coerceIn(0f, 1f)
        Box(track.semantics { contentDescription = description; progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f) }) {
            Box(Modifier.fillMaxWidth(p).height(6.dp).clip(shape).background(DriftTheme.colors.primary))
        }
    } else {
        val bias = if (reduced) 0f else {
            val t = rememberInfiniteTransition(label = "indeterminate")
            val v by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(DriftTheme.motion.pulse, easing = LinearEasing), RepeatMode.Reverse), label = "bias")
            v
        }
        Box(track.semantics { contentDescription = description; progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
            Box(
                Modifier.fillMaxWidth(0.3f).height(6.dp).align(BiasAlignment(bias, 0f)).clip(shape).background(DriftTheme.colors.primary),
            )
        }
    }
}

/** What an import report contains, as short lines. Used for the Sleep as Android backup. */
@Composable
internal fun ImportReportLines(report: ImportReport) {
    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs)) {
        Text(
            pluralStringResource(R.plurals.ob_report_nights, report.nightsImported, report.nightsImported),
            style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface,
        )
        val first = report.earliest
        val last = report.latest
        if (first != null && last != null) {
            Text(stringResource(R.string.ob_report_range, first.toString(), last.toString()), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
        }
        Text(stringResource(R.string.ob_report_duplicates, report.duplicatesSkipped), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
        if (report.rowsRejected > 0) {
            Text(stringResource(R.string.ob_report_rejected, report.rowsRejected), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.warning)
        }
        report.warnings.forEach {
            Text(stringResource(R.string.ob_report_warning, it), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.warning)
        }
    }
}

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
