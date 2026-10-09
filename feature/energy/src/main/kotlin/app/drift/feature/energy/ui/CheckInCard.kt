package app.drift.feature.energy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.style.TextAlign
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftTheme
import app.drift.feature.energy.R
import app.drift.model.Provenance

private const val RATINGS = 5

/**
 * Calibration footer (plan 4.6). Always says how many check-ins shaped the curve; shows the 1 to 5 rating row when
 * a check-in is due, or after "Check in now".
 */
@Composable
internal fun CheckInCard(
    calibrationCheckIns: Int,
    due: Boolean,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var logged by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(due) { if (due) logged = false }
    val showRatings = (due || expanded) && !logged
    DriftCard(modifier = modifier.testTag("energy_checkin_card")) {
        Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (calibrationCheckIns > 0) {
                    pluralStringResource(R.plurals.energy_checkin_count, calibrationCheckIns, calibrationCheckIns)
                } else {
                    stringResource(R.string.energy_checkin_none)
                },
                style = DriftTheme.typography.bodySmall,
                color = DriftTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f).testTag("energy_checkin_count"),
            )
        }
        when {
            logged -> Text(stringResource(R.string.energy_checkin_thanks), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.success)
            showRatings -> {
                Text(stringResource(R.string.energy_checkin_title), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
                RatingRow(onRate = { rating ->
                    logged = true
                    expanded = false
                    onRate(rating)
                })
            }
            else -> DriftButton(stringResource(R.string.energy_checkin_action), { expanded = true }, style = ButtonStyle.Text)
        }
    }
}

@Composable
private fun RatingRow(onRate: (Int) -> Unit) {
    val group = stringResource(R.string.energy_checkin_group)
    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs)) {
        Row(
            Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = group },
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
        ) {
            for (rating in 1..RATINGS) {
                val label = stringResource(R.string.energy_checkin_rating, rating)
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = DriftTheme.spacing.minTouch)
                        .clip(RoundedCornerShape(DriftTheme.radii.sm))
                        .border(BorderStroke(DriftTheme.strokes.outline, DriftTheme.colors.outline), RoundedCornerShape(DriftTheme.radii.sm))
                        .testTag("energy_rating_$rating")
                        .clickable(onClickLabel = label, role = Role.Button) { onRate(rating) }
                        .semantics { contentDescription = label; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(rating.toString(), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface, modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.energy_checkin_low), style = DriftTheme.typography.caption, color = DriftTheme.colors.onSurfaceMuted)
            Text(
                stringResource(R.string.energy_checkin_high), style = DriftTheme.typography.caption,
                color = DriftTheme.colors.onSurfaceMuted, textAlign = TextAlign.End,
            )
        }
    }
}
