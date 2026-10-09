package app.drift.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Screen top bar: optional back button, title (a heading), optional subtitle and trailing actions.
 * Set [applyStatusBarInsets] when the bar is the topmost element of an edge-to-edge screen.
 */
@Composable
fun DriftTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    applyStatusBarInsets: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DriftTheme.colors.background)
            .then(if (applyStatusBarInsets) Modifier.statusBarsPadding() else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = if (onBack != null) DriftTheme.spacing.xs else DriftTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) DriftIconButton(DriftIcons.Back, stringResource(R.string.ds_back), onBack)
        Column(Modifier.weight(1f).padding(horizontal = DriftTheme.spacing.sm)) {
            Text(title, style = DriftTheme.typography.headline, color = DriftTheme.colors.onSurface, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
        }
        actions()
    }
}

/**
 * Header of a main tab: bold [title] over a muted [subtitle] (for example a date range), with [trailing] content
 * (usually [ProfileButton]) on the right. Announced as a heading.
 */
@Composable
fun DriftScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DriftTheme.colors.background)
            .padding(start = DriftTheme.spacing.gutter, end = DriftTheme.spacing.md, top = DriftTheme.spacing.lg, bottom = DriftTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DriftTheme.typography.screenTitle, color = DriftTheme.colors.onSurface, modifier = Modifier.semantics { heading() })
            subtitle?.invoke()
        }
        trailing()
    }
}

/** Muted subtitle line for [DriftScreenHeader]. */
@Composable
fun HeaderSubtitle(text: String) {
    Text(text, style = DriftTheme.typography.screenSubtitle, color = DriftTheme.colors.onSurfaceMuted)
}

/** Round profile button that opens Settings, drawn in the accent colour. */
@Composable
fun ProfileButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            DriftIcons.Profile, contentDescription = stringResource(R.string.ds_profile),
            tint = DriftTheme.colors.primary, modifier = Modifier.size(36.dp),
        )
    }
}

/** One destination in [DriftBottomNavBar]. [contentDescription] defaults to the label. */
data class DriftNavItem(val label: String, val icon: ImageVector, val contentDescription: String = label)

/** Sleep / Energy / Progress / Learn, in the order the main tabs appear. */
@Composable
fun driftDefaultNavItems(): List<DriftNavItem> = listOf(
    DriftNavItem(stringResource(R.string.ds_nav_sleep), DriftIcons.TabSleep),
    DriftNavItem(stringResource(R.string.ds_nav_energy), DriftIcons.TabEnergy),
    DriftNavItem(stringResource(R.string.ds_nav_progress), DriftIcons.TabProgress),
    DriftNavItem(stringResource(R.string.ds_nav_learn), DriftIcons.TabLearn),
)

/**
 * Bottom navigation. Items are Tabs in a selectable group, each at least 56 dp tall with an icon, a label that
 * wraps to two lines at 200% font scale, and a pill indicator on the selected item.
 */
@Composable
fun DriftBottomNavBar(
    items: List<DriftNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DriftTheme.colors
    Column(modifier.fillMaxWidth().background(c.surface).navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup().padding(horizontal = DriftTheme.spacing.xs, vertical = DriftTheme.spacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(DriftTheme.radii.md))
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .semantics { contentDescription = item.contentDescription }
                        .padding(horizontal = DriftTheme.spacing.xs, vertical = DriftTheme.spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
                ) {
                    Box(Modifier.width(56.dp).heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            item.icon, null,
                            tint = if (selected) c.onSurface else c.primary.copy(alpha = 0.75f),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    // Labels are secondary to the icon + TalkBack name, so they stop growing at 1.3x to keep four tabs on screen.
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, 1.3f))) {
                        Text(
                            item.label,
                            style = DriftTheme.typography.caption,
                            color = if (selected) c.onSurface else c.onSurfaceMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
    }
}
