package app.drift.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Modal bottom sheet (e.g. "Why this changed"). Dismiss by swipe, scrim tap, back, or the close button.
 * Content scrolls; the sheet is capped by Material at the screen height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriftBottomSheet(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = DriftTheme.radii.sheet, topEnd = DriftTheme.radii.sheet),
        containerColor = DriftTheme.colors.surfaceRaised,
        contentColor = DriftTheme.colors.onSurface,
        scrimColor = DriftTheme.colors.scrim.copy(alpha = 0.5f),
        dragHandle = { SheetHandle() },
    ) {
        SheetBody(title = title, onClose = onDismiss, content = content)
    }
}

/** The sheet's inner layout, usable without a window (screenshot tests, previews). */
@Composable
fun SheetBody(
    title: String,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = DriftTheme.spacing.gutter)
            .padding(bottom = DriftTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = DriftTheme.typography.title,
                color = DriftTheme.colors.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (onClose != null) DriftIconButton(DriftIcons.Close, stringResource(R.string.ds_close), onClose)
        }
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
            content = content,
        )
    }
}

@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = DriftTheme.spacing.md), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(DriftTheme.colors.outline),
        )
    }
}
