package app.drift.feature.sleep.bodyclock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.drift.charts.CircadianRhythmChart
import app.drift.designsystem.CardTone
import app.drift.designsystem.ConfidenceBand
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftIconButton
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.DriftTopBar
import app.drift.designsystem.DurationHero
import app.drift.designsystem.InsufficientDataPanel
import app.drift.designsystem.ScreenStateHost
import app.drift.designsystem.confidenceBandText
import app.drift.designsystem.labelRes
import app.drift.feature.sleep.R
import app.drift.feature.sleep.common.BodyText
import app.drift.feature.sleep.common.Caption
import app.drift.feature.sleep.common.CardTitle
import app.drift.feature.sleep.common.RegularityBlock
import app.drift.feature.sleep.common.ScreenBody
import app.drift.feature.sleep.common.StatRow
import app.drift.feature.sleep.common.clockIn
import app.drift.feature.sleep.common.entrance
import app.drift.feature.sleep.common.rememberUse24h
import app.drift.model.Provenance
import app.drift.model.engine.ChronotypeLabel
import app.drift.model.engine.MetricKey
import app.drift.model.repository.SourceFix
import app.drift.model.ui.InsufficientReason
import app.drift.model.ui.ScreenState
import kotlin.math.roundToInt

data class BodyClockActions(
    val onBack: (() -> Unit)? = null,
    val onExplain: (MetricKey) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onFix: (SourceFix) -> Unit = {},
)

@Composable
fun BodyClockScreen(
    state: ScreenState<BodyClockUiModel>,
    actions: BodyClockActions,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    ScreenBody(
        modifier = modifier,
        scrollable = scrollable,
        topBar = { DriftTopBar(title = stringResource(R.string.body_title), onBack = actions.onBack) },
    ) {
        ScreenStateHost(state, onRetry = actions.onRetry, onFix = actions.onFix) { model ->
            BodyClockContent(model, actions)
        }
    }
}

@Composable
private fun BodyClockContent(model: BodyClockUiModel, actions: BodyClockActions) {
    val use24h = rememberUse24h(model.use24hSetting)
    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sectionGap)) {
        Banners(model)
        DialCard(model, use24h, actions, Modifier.entrance(0))
        when (val c = model.chronotype) {
            is ChronotypeSection.Available -> ChronotypeCard(c, use24h, actions, Modifier.entrance(1))
            is ChronotypeSection.NotEnoughFreeNights ->
                InsufficientDataPanel(InsufficientReason.NOT_ENOUGH_FREE_NIGHTS, c.nightsNeeded, Modifier.entrance(1))
        }
        when (val j = model.socialJetlag) {
            is JetlagSection.Available -> JetlagCard(j, Modifier.entrance(2))
            is JetlagSection.NotEnoughFreeNights ->
                InsufficientDataPanel(InsufficientReason.NOT_ENOUGH_FREE_NIGHTS, j.nightsNeeded, Modifier.entrance(2))
        }
        RegularityCard(model, actions, Modifier.entrance(3))
    }
}

@Composable
private fun Banners(model: BodyClockUiModel) {
    val re = model.circadian.reentrainment
    if (re != null) {
        DriftCard(
            tone = CardTone.Accent,
            contentDescription = null,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                stringResource(if (re.eastward) R.string.body_reentrain_east else R.string.body_reentrain_west),
                style = DriftTheme.typography.titleSmall,
                color = DriftTheme.colors.onPrimaryContainer,
            )
            Text(
                pluralStringResource(
                    R.plurals.body_reentrain_detail,
                    re.estimatedDaysLeft,
                    re.estimatedDaysLeft,
                    DriftFormat.duration(re.remainingShift),
                ),
                style = DriftTheme.typography.body,
                color = DriftTheme.colors.onPrimaryContainer,
            )
        }
    }
    if (model.circadian.shiftWorkMode) {
        DriftCard(tone = CardTone.Accent) {
            Text(
                stringResource(R.string.body_shift_work),
                style = DriftTheme.typography.body,
                color = DriftTheme.colors.onPrimaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialCard(model: BodyClockUiModel, use24h: Boolean, actions: BodyClockActions, modifier: Modifier) {
    val circadian = model.circadian
    val dlmo = model.dlmo
    DriftCard(modifier = modifier, onClick = { actions.onExplain(MetricKey.CIRCADIAN) }, onClickLabel = stringResource(R.string.body_why_circadian)) {
        CardTitle(stringResource(R.string.body_dial_title))
        if (dlmo != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                dlmo.confidence?.let { Caption(stringResource(it.level.labelRes())) }
            }
        }
        CircadianRhythmChart(circadian, zone = model.zone, use24h = use24h, now = model.now, sleepWindow = model.plannedSleep)
        if (circadian.melatoninWindow == null) {
            InsufficientDataPanel(
                title = stringResource(R.string.body_no_estimate_title),
                body = stringResource(R.string.body_no_estimate_body),
            )
        }
        if (dlmo != null) {
            val onset = DriftFormat.clockMinutes(dlmo.value.roundToInt(), use24h)
            val band = model.dlmoHalfBand?.let { stringResource(R.string.body_give_or_take, DriftFormat.duration(it)) }
            StatRow(stringResource(R.string.body_dlmo), listOfNotNull(onset, band).joinToString(", "), dlmo.provenance)
        }
        circadian.windDownStart?.let {
            StatRow(stringResource(R.string.body_wind_down), it.clockIn(model.zone, use24h), dlmo?.provenance ?: Provenance.ESTIMATED)
        }
    }
}

@Composable
private fun ChronotypeCard(section: ChronotypeSection.Available, use24h: Boolean, actions: BodyClockActions, modifier: Modifier) {
    val c = section.chronotype
    val label = stringResource(
        when (c.label) {
            ChronotypeLabel.EARLY -> R.string.body_chrono_early
            ChronotypeLabel.INTERMEDIATE -> R.string.body_chrono_intermediate
            ChronotypeLabel.LATE -> R.string.body_chrono_late
        },
    )
    val msf = c.msfscMinutes
    val midSleep = DriftFormat.clockMinutes(msf.value.roundToInt(), use24h)
    DriftCard(modifier = modifier, onClick = { actions.onExplain(MetricKey.CHRONOTYPE) }, onClickLabel = stringResource(R.string.body_why_chronotype)) {
        CardTitle(stringResource(R.string.body_chrono_title))
        Text(label, style = DriftTheme.typography.display, color = DriftTheme.colors.onSurface)
        StatRow(stringResource(R.string.body_chrono_midsleep), midSleep, msf.provenance)
        msf.confidence?.let { band ->
            val half = DriftFormat.duration(java.time.Duration.ofMinutes(((band.upper - band.lower) / 2).roundToInt().toLong()))
            ConfidenceBand(
                value = msf.value,
                lower = band.lower,
                upper = band.upper,
                scaleMin = band.lower - 60,
                scaleMax = band.upper + 60,
                valueText = midSleep,
                bandText = confidenceBandText(
                    half,
                    DriftFormat.clockMinutes(band.lower.roundToInt(), use24h),
                    DriftFormat.clockMinutes(band.upper.roundToInt(), use24h),
                    band.coverage,
                ),
                level = band.level,
            )
        }
        Caption(
            pluralStringResource(R.plurals.body_chrono_basis, c.freeNightsUsed, c.freeNightsUsed, (c.questionnaireWeight * 100).roundToInt()),
        )
    }
}

@Composable
private fun JetlagCard(section: JetlagSection.Available, modifier: Modifier) {
    DriftCard(modifier) {
        DurationHero(
            duration = section.socialJetlag.value,
            label = stringResource(R.string.body_jetlag_label),
            provenance = section.socialJetlag.provenance,
        )
        BodyText(stringResource(R.string.body_jetlag_meaning))
    }
}

@Composable
private fun RegularityCard(model: BodyClockUiModel, actions: BodyClockActions, modifier: Modifier) {
    DriftCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.sleep_regularity_title), Modifier.weight(1f))
            DriftIconButton(DriftIcons.Info, stringResource(R.string.sleep_why_regularity), { actions.onExplain(MetricKey.REGULARITY) })
        }
        if (model.regularity.available) {
            RegularityBlock(model.regularity, trendHeight = 170.dp)
        } else {
            InsufficientDataPanel(InsufficientReason.TOO_FEW_NIGHTS, model.regularity.nightsNeeded)
        }
    }
}
