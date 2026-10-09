package app.drift.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import app.drift.model.Provenance

fun Provenance.labelRes(): Int = when (this) {
    Provenance.MEASURED -> R.string.ds_provenance_measured
    Provenance.DERIVED -> R.string.ds_provenance_derived
    Provenance.ESTIMATED -> R.string.ds_provenance_estimated
    Provenance.USER_ENTERED -> R.string.ds_provenance_user_entered
}

fun Provenance.descriptionRes(): Int = when (this) {
    Provenance.MEASURED -> R.string.ds_provenance_measured_desc
    Provenance.DERIVED -> R.string.ds_provenance_derived_desc
    Provenance.ESTIMATED -> R.string.ds_provenance_estimated_desc
    Provenance.USER_ENTERED -> R.string.ds_provenance_user_entered_desc
}

/**
 * Tag shown next to any number: Measured, Derived, Estimated or User-entered. The label is always text (never
 * colour alone) and each kind has its own glyph. With [onClick] it is a button (48 dp touch target).
 */
@Composable
fun ProvenanceChip(
    provenance: Provenance,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val tint = DriftTheme.colors.provenance(provenance)
    val shape = RoundedCornerShape(DriftTheme.radii.pill)
    val description = stringResource(provenance.descriptionRes())
    val clickMod = if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier
    Row(
        modifier = modifier
            .then(clickMod)
            .clip(shape)
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.55f), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = DriftTheme.spacing.md, vertical = DriftTheme.spacing.xs)
            .clearAndSetSemantics {
                contentDescription = description
                if (onClick != null) role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs + 2.dp),
    ) {
        Canvas(Modifier.size(10.dp)) { drawGlyph(provenance, tint) }
        Text(
            text = stringResource(provenance.labelRes()),
            style = DriftTheme.typography.caption,
            color = tint,
            maxLines = 1,
        )
    }
}

private fun DrawScope.drawGlyph(p: Provenance, tint: androidx.compose.ui.graphics.Color) {
    val s = size.minDimension
    when (p) {
        Provenance.MEASURED -> drawCircle(tint, radius = s / 2)
        Provenance.DERIVED -> {
            val path = Path().apply {
                moveTo(s / 2, 0f); lineTo(s, s / 2); lineTo(s / 2, s); lineTo(0f, s / 2); close()
            }
            drawPath(path, tint)
        }
        Provenance.ESTIMATED -> drawCircle(
            tint, radius = s / 2 - 1f,
            style = Stroke(width = 1.6f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.2f * density, 1.6f * density))),
        )
        Provenance.USER_ENTERED -> drawRect(tint, topLeft = Offset(s * 0.1f, s * 0.1f), size = Size(s * 0.8f, s * 0.8f))
    }
}
