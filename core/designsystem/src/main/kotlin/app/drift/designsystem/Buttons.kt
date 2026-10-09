package app.drift.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class ButtonStyle { Primary, Secondary, Text }

/** Pill button, min 48 dp tall, label wraps at large font scales. Role Button comes from Material. */
@Composable
fun DriftButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val c = DriftTheme.colors
    val shape = RoundedCornerShape(DriftTheme.radii.pill)
    val min = Modifier.then(modifier)
    val padding = ButtonDefaults.ContentPadding
    when (style) {
        ButtonStyle.Primary -> Button(
            onClick = onClick, modifier = min, enabled = enabled, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
        ) { ButtonLabel(label) }
        ButtonStyle.Secondary -> OutlinedButton(
            onClick = onClick, modifier = min, enabled = enabled, shape = shape, contentPadding = padding,
            border = BorderStroke(DriftTheme.strokes.outline, c.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.onSurface),
        ) { ButtonLabel(label) }
        ButtonStyle.Text -> TextButton(
            onClick = onClick, modifier = min, enabled = enabled, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(contentColor = c.primary),
        ) { ButtonLabel(label) }
    }
}

@Composable
private fun ButtonLabel(label: String) {
    Text(label, style = DriftTheme.typography.label, textAlign = TextAlign.Center)
}

/** 48 dp icon button; [contentDescription] is required because the icon is the only label. */
@Composable
fun DriftIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(DriftTheme.spacing.minTouch),
        colors = IconButtonDefaults.iconButtonColors(contentColor = DriftTheme.colors.onSurface),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
    }
}
