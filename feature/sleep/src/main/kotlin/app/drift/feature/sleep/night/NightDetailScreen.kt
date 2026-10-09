package app.drift.feature.sleep.night

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.drift.charts.StageChart
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftDivider
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftListRow
import app.drift.designsystem.DriftSwitchRow
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.DriftTopBar
import app.drift.designsystem.DurationHero
import app.drift.designsystem.ProvenanceChip
import app.drift.designsystem.ScreenStateHost
import app.drift.designsystem.labelRes
import app.drift.feature.sleep.R
import app.drift.feature.sleep.common.BodyText
import app.drift.feature.sleep.common.Caption
import app.drift.feature.sleep.common.CardTitle
import app.drift.feature.sleep.common.ScreenBody
import app.drift.feature.sleep.common.StatRow
import app.drift.feature.sleep.common.clockIn
import app.drift.feature.sleep.common.entrance
import app.drift.feature.sleep.common.longLabel
import app.drift.feature.sleep.common.rememberUse24h
import app.drift.model.Provenance
import app.drift.model.QualityFlag
import app.drift.model.QualityTier
import app.drift.model.SleepStage
import app.drift.model.SourceId
import app.drift.model.ui.ScreenState
import java.time.LocalDate

data class NightDetailActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onStartChange: (Int) -> Unit = {},
    val onEndChange: (Int) -> Unit = {},
    val onAddNap: (LocalDate) -> Unit = {},
    val onFreeDayChange: (LocalDate, Boolean) -> Unit = { _, _ -> },
    val onDelete: () -> Unit = {},
    val onUseSource: (SourceOption) -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
fun NightDetailScreen(
    state: ScreenState<NightDetailUiModel>,
    editor: EditorUiState?,
    actions: NightDetailActions,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    val ready = (state as? ScreenState.Ready)?.model
    val title = ready?.sleepDay?.longLabel() ?: stringResource(R.string.night_title_fallback)
    ScreenBody(
        modifier = modifier,
        scrollable = scrollable,
        topBar = {
            DriftTopBar(
                title = title,
                onBack = actions.onBack,
                actions = {
                    if (ready != null && editor == null) DriftButton(stringResource(R.string.night_edit), actions.onEdit, style = ButtonStyle.Text)
                },
            )
        },
        bottomBar = if (editor != null) {
            { EditorButtons(editor.canSave, actions.onSave, actions.onCancelEdit) }
        } else null,
    ) {
        ScreenStateHost(state, onRetry = actions.onRetry) { model ->
            NightContent(model, editor, actions)
        }
    }
}

@Composable
private fun NightContent(model: NightDetailUiModel, editor: EditorUiState?, actions: NightDetailActions) {
    val use24h = rememberUse24h(model.use24hSetting)
    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sectionGap)) {
        SummaryCard(model, Modifier.entrance(0))
        if (editor != null) {
            EpisodeEditor(
                state = editor,
                use24h = use24h,
                startLabel = stringResource(R.string.night_bedtime),
                endLabel = stringResource(R.string.night_wake),
                onStartChange = actions.onStartChange,
                onEndChange = actions.onEndChange,
                modifier = Modifier.entrance(1),
            )
        } else {
            TimesCard(model, use24h, Modifier.entrance(1))
        }
        StagesCard(model, use24h, Modifier.entrance(2))
        QualityCard(model, Modifier.entrance(3))
        if (model.conflicts.isNotEmpty()) ConflictCard(model, use24h, actions, Modifier.entrance(4))
        ActionsCard(model, actions, Modifier.entrance(5))
    }
}

@Composable
private fun sourceName(id: SourceId): String = stringResource(
    when (id) {
        SourceId.HEALTH_CONNECT -> R.string.night_source_health_connect
        SourceId.SLEEP_AS_ANDROID -> R.string.night_source_saa
        SourceId.PHONE -> R.string.night_source_phone
        SourceId.USER -> R.string.night_source_user
        else -> R.string.night_source_other
    },
)

@Composable
private fun tierName(tier: QualityTier): String = stringResource(
    when (tier) {
        QualityTier.WEARABLE_RING -> R.string.night_tier_ring
        QualityTier.WEARABLE_WRIST -> R.string.night_tier_wrist
        QualityTier.PHONE_APP -> R.string.night_tier_phone_app
        QualityTier.PHONE_INFERRED -> R.string.night_tier_phone_inferred
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(model: NightDetailUiModel, modifier: Modifier) {
    val origin = model.episode.origin
    DriftCard(modifier) {
        DurationHero(
            duration = model.asleep,
            label = stringResource(R.string.night_total_sleep),
            provenance = model.provenance,
        )
        if (model.episode.userEdited) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                ProvenanceChip(Provenance.USER_ENTERED)
                Caption(stringResource(R.string.night_user_edited))
            }
        }
        Caption(stringResource(R.string.night_source_line, sourceName(origin.sourceId), tierName(origin.qualityTier)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimesCard(model: NightDetailUiModel, use24h: Boolean, modifier: Modifier) {
    val ep = model.episode
    DriftCard(modifier) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
        ) {
            TimeBlock(stringResource(R.string.night_bedtime), ep.start.clockIn(ep.zone, use24h), model.provenance)
            TimeBlock(stringResource(R.string.night_wake), ep.end.clockIn(ep.zone, use24h), model.provenance)
        }
        ep.inBed?.let {
            StatRow(
                stringResource(R.string.night_in_bed),
                stringResource(R.string.night_in_bed_value, it.start.clockIn(ep.zone, use24h), it.end.clockIn(ep.zone, use24h)),
                model.provenance,
            )
        }
    }
}

@Composable
private fun TimeBlock(label: String, time: String, provenance: Provenance) {
    val spoken = stringResource(R.string.sleep_time_description, label, time, stringResource(provenance.labelRes()))
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }, verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs)) {
        Caption(label)
        Text(time, style = DriftTheme.typography.display, color = DriftTheme.colors.onSurface)
        ProvenanceChip(provenance)
    }
}

@Composable
private fun stageName(stage: SleepStage): String = stringResource(
    when (stage) {
        SleepStage.AWAKE -> R.string.night_stage_awake
        SleepStage.REM -> R.string.night_stage_rem
        SleepStage.LIGHT -> R.string.night_stage_light
        SleepStage.DEEP -> R.string.night_stage_deep
        SleepStage.UNKNOWN -> R.string.night_stage_unknown
    },
)

@Composable
private fun StagesCard(model: NightDetailUiModel, use24h: Boolean, modifier: Modifier) {
    val ep = model.episode
    DriftCard(modifier) {
        CardTitle(stringResource(R.string.night_stages_title))
        val totals = model.stages
        if (totals == null || ep.stages == null) {
            BodyText(stringResource(R.string.night_no_stages))
        } else {
            StageChart(ep.stages!!, zone = ep.zone, use24h = use24h, height = 180.dp)
            totals.forEach { t ->
                StatRow(
                    stageName(t.stage),
                    stringResource(R.string.night_stage_value, DriftFormat.duration(t.duration), t.percent),
                    model.provenance,
                )
            }
        }
    }
}

@Composable
private fun QualityCard(model: NightDetailUiModel, modifier: Modifier) {
    DriftCard(modifier) {
        CardTitle(stringResource(R.string.night_quality_title))
        BodyText(
            stringResource(
                when (model.completeness) {
                    CompletenessWord.COMPLETE -> R.string.night_complete
                    CompletenessWord.SOME_GAPS -> R.string.night_some_gaps
                    CompletenessWord.MANY_GAPS -> R.string.night_many_gaps
                },
            ),
        )
        BodyText(
            stringResource(
                when (model.detection) {
                    DetectionWord.RECORDED -> R.string.night_recorded
                    DetectionWord.BEST_GUESS -> R.string.night_best_guess
                },
            ),
        )
        model.flags.forEach { flag ->
            val text = when (flag) {
                QualityFlag.IMPLAUSIBLE_DURATION -> R.string.night_flag_implausible
                QualityFlag.ENDS_IN_FUTURE -> R.string.night_flag_future
                QualityFlag.USER_CONFIRMED -> R.string.night_flag_confirmed
                QualityFlag.SOURCE_CONFLICT -> R.string.night_flag_conflict
            }
            val color = if (flag == QualityFlag.USER_CONFIRMED) DriftTheme.colors.onSurfaceMuted else DriftTheme.colors.warning
            Text(stringResource(text), style = DriftTheme.typography.bodySmall, color = color)
        }
    }
}

@Composable
private fun ConflictCard(model: NightDetailUiModel, use24h: Boolean, actions: NightDetailActions, modifier: Modifier) {
    DriftCard(modifier) {
        CardTitle(stringResource(R.string.night_conflict_title))
        Caption(stringResource(R.string.night_conflict_body))
        model.conflicts.forEach { option ->
            val ep = option.episode
            val name = stringResource(R.string.night_source_line, sourceName(ep.origin.sourceId), tierName(ep.origin.qualityTier))
            val times = stringResource(
                R.string.history_row_subtitle,
                ep.start.clockIn(ep.zone, use24h),
                ep.end.clockIn(ep.zone, use24h),
                DriftFormat.duration(java.time.Duration.between(ep.start, ep.end)),
            )
            DriftListRow(
                title = name,
                subtitle = times,
                trailing = {
                    if (option.used) {
                        Text(stringResource(R.string.night_conflict_used), style = DriftTheme.typography.label, color = DriftTheme.colors.success)
                    } else {
                        DriftButton(stringResource(R.string.night_conflict_use), { actions.onUseSource(option) }, style = ButtonStyle.Secondary)
                    }
                },
            )
        }
    }
}

@Composable
private fun ActionsCard(model: NightDetailUiModel, actions: NightDetailActions, modifier: Modifier) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    DriftCard(modifier) {
        DriftListRow(
            title = stringResource(R.string.night_add_nap),
            onClick = { actions.onAddNap(model.sleepDay) },
            onClickLabel = stringResource(R.string.night_add_nap),
        )
        DriftDivider()
        DriftSwitchRow(
            title = stringResource(R.string.night_free_day),
            checked = model.freeDay,
            onCheckedChange = { actions.onFreeDayChange(model.sleepDay, it) },
            subtitle = stringResource(R.string.night_free_day_hint),
        )
        DriftDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClickLabel = stringResource(R.string.night_delete), role = Role.Button) { confirming = true }
                .padding(vertical = DriftTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.night_delete), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.error)
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = DriftTheme.colors.surfaceRaised,
            title = { Text(stringResource(R.string.night_delete_title), style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface) },
            text = { Text(stringResource(R.string.night_delete_body), style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted) },
            confirmButton = {
                DriftButton(stringResource(R.string.night_delete_confirm), { confirming = false; actions.onDelete() })
            },
            dismissButton = { DriftButton(stringResource(R.string.night_cancel), { confirming = false }, style = ButtonStyle.Text) },
        )
    }
}
