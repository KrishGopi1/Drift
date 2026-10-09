package app.drift.feature.sleep.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.ProvenanceChip
import app.drift.designsystem.labelRes
import app.drift.model.Provenance

/** A label with its value and provenance chip. Wraps instead of truncating at large font sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatRow(label: String, value: String, provenance: Provenance?, modifier: Modifier = Modifier) {
    val spoken = if (provenance != null) "$label: $value, ${stringResource(provenance.labelRes())}" else "$label: $value"
    FlowRow(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted)
        Text(value, style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface, modifier = Modifier.clearAndSetSemantics {})
        if (provenance != null) ProvenanceChip(provenance, Modifier.clearAndSetSemantics {})
    }
}

/** Card title, announced as a plain label next to the content; kept in one place for consistent style. */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface, modifier = modifier)
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted, modifier = modifier)
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = DriftTheme.typography.body, color = DriftTheme.colors.onSurface, modifier = modifier)
}
