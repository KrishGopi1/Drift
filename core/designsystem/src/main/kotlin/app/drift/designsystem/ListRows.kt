package app.drift.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.drift.model.Provenance

/**
 * Two-line list row with optional leading/trailing slots. With [onClick] it is a button (role Button, chevron
 * shown unless [trailing] is given) and at least 56 dp tall. [provenance] adds a chip under the subtitle.
 */
@Composable
fun DriftListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    provenance: Provenance? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val base = modifier.fillMaxWidth().heightIn(min = 56.dp)
    val interactive = if (onClick != null) base.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else base
    Row(
        modifier = interactive.padding(vertical = DriftTheme.spacing.sm).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xxs)) {
            Text(title, style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
            if (subtitle != null) Text(subtitle, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
            if (provenance != null) ProvenanceChip(provenance, Modifier.padding(top = DriftTheme.spacing.xs))
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(DriftIcons.ChevronRight, null, tint = DriftTheme.colors.onSurfaceMuted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Settings row with a switch. The whole row toggles (role Switch); the inner switch is not a second focus stop. */
@Composable
fun DriftSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val c = DriftTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = DriftTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xxs)) {
            Text(title, style = DriftTheme.typography.titleSmall, color = c.onSurface)
            if (subtitle != null) Text(subtitle, style = DriftTheme.typography.bodySmall, color = c.onSurfaceMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {},
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary,
                uncheckedThumbColor = c.outline, uncheckedTrackColor = c.surfaceRaised, uncheckedBorderColor = c.outline,
            ),
        )
    }
}

@Composable
fun DriftDivider(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().height(1.dp).background(DriftTheme.colors.outlineSubtle)) {}
}
