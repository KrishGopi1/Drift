package app.drift.feature.sleep.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.drift.charts.DebtGauge
import app.drift.charts.MiniEnergyCurve
import app.drift.designsystem.CardTone
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftDivider
import app.drift.designsystem.DriftFormat
import app.drift.designsystem.DriftIconButton
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftScreenHeader
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.DurationHero
import app.drift.designsystem.HeaderSubtitle
import app.drift.designsystem.InsufficientDataPanel
import app.drift.designsystem.SettingsButton
import app.drift.designsystem.ScreenStateHost
import app.drift.designsystem.labelRes
import app.drift.feature.sleep.R
import app.drift.feature.sleep.common.Caption
import app.drift.feature.sleep.common.CardTitle
import app.drift.feature.sleep.common.RegularityBlock
import app.drift.feature.sleep.common.StatRow
import app.drift.feature.sleep.common.clockIn
import app.drift.feature.sleep.common.entrance
import app.drift.feature.sleep.common.rememberUse24h
import app.drift.feature.sleep.history.provenanceOf
import app.drift.model.EpisodeId
import app.drift.model.SourceId
import app.drift.model.engine.DebtBand
import app.drift.model.engine.EnergyLabel
import app.drift.model.engine.MetricKey
import app.drift.model.repository.SourceFix
import app.drift.model.ui.InsufficientReason
import app.drift.model.ui.ScreenState
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Callbacks of the Sleep screen. Every one is wired to a visible control. */
data class SleepActions(
    val onExplain: (MetricKey) -> Unit = {},
    val onOpenBodyClock: () -> Unit = {},
    val onOpenNight: (EpisodeId) -> Unit = {},
    val onOpenEnergy: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onFix: (SourceFix) -> Unit = {},
)

/**
 * Sleep tab, the app's home: "My sleep" over the debt window's dates, the debt gauge with how many nights reached
 * Great and Super, the change since yesterday next to today's energy potential, last night, today's energy curve,
 * then need, regularity and tonight's times. [scrollable] is false only for screenshot tests.
 */
@Composable
fun SleepScreen(
    state: ScreenState<SleepUiModel>,
    actions: SleepActions,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    val ready = (state as? ScreenState.Ready)?.model
    Column(
        modifier
            .background(DriftTheme.colors.background)
            .then(if (scrollable) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier),
    ) {
        DriftScreenHeader(
            title = stringResource(R.string.sleep_home_title),
            subtitle = { HeaderSubtitle(ready?.let { windowLabel(it) } ?: stringResource(R.string.sleep_subtitle)) },
            trailing = { SettingsButton(actions.onOpenSettings) },
        )
        Column(Modifier.padding(horizontal = DriftTheme.spacing.gutter).padding(bottom = DriftTheme.spacing.xl)) {
            ScreenStateHost(state, onRetry = actions.onRetry, onFix = actions.onFix) { model ->
                SleepContent(model, actions)
            }
        }
    }
}

/** "Oct 12 – 26" or "Sep 28 – Oct 11". */
@Composable
private fun windowLabel(model: SleepUiModel): String {
    val a = model.windowStart ?: return stringResource(R.string.sleep_subtitle)
    val b = model.windowEnd ?: a
    val locale = Locale.getDefault()
    val month = DateTimeFormatter.ofPattern("MMM d", locale)
    val day = DateTimeFormatter.ofPattern("d", locale)
    return if (a.month == b.month) "${month.format(a)} – ${day.format(b)}" else "${month.format(a)} – ${month.format(b)}"
}

@Composable
private fun SleepContent(model: SleepUiModel, actions: SleepActions) {
    val use24h = rememberUse24h(model.use24hSetting)
    Column(verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md)) {
        DebtCard(model, actions, Modifier.entrance(0))
        if (model.debt is DebtSection.Available) {
            TileRow(model, actions, Modifier.entrance(1))
        }
        IdealScheduleCard(model, use24h, actions, Modifier.entrance(1))
        model.lastNight?.let { LastNightCard(model, use24h, actions, Modifier.entrance(2)) }
        model.todayCurve?.let { EnergyTodayCard(model, use24h, actions, Modifier.entrance(3)) }
        NeedCard(model.need, actions, Modifier.entrance(4))
        RegularityCard(model.regularity, actions, Modifier.entrance(5))
    }
}

@Composable
private fun bandWord(band: DebtBand): String = stringResource(
    when (band) {
        DebtBand.LOW -> R.string.sleep_band_low
        DebtBand.MODERATE -> R.string.sleep_band_moderate
        DebtBand.HIGH -> R.string.sleep_band_high
    },
)

@Composable
private fun labelWord(label: EnergyLabel): String = stringResource(
    when (label) {
        EnergyLabel.SUPER -> R.string.sleep_label_super
        EnergyLabel.GREAT -> R.string.sleep_label_great
        EnergyLabel.GOOD -> R.string.sleep_label_good
        EnergyLabel.FAIR -> R.string.sleep_label_fair
        EnergyLabel.LOW -> R.string.sleep_label_low
    },
)

@Composable
internal fun labelColor(label: EnergyLabel): Color = when (label) {
    EnergyLabel.SUPER, EnergyLabel.GREAT -> DriftTheme.colors.success
    EnergyLabel.GOOD -> DriftTheme.colors.energy
    EnergyLabel.FAIR -> DriftTheme.colors.debt
    EnergyLabel.LOW -> DriftTheme.colors.error
}

@Composable
private fun DebtCard(model: SleepUiModel, actions: SleepActions, modifier: Modifier) {
    when (val section = model.debt) {
        is DebtSection.Available -> {
            val debt = section.debt
            val description = stringResource(
                R.string.sleep_debt_description,
                DriftFormat.durationSpoken(debt.value),
                stringResource(debt.provenance.labelRes()),
                section.band?.let { bandWord(it) }.orEmpty(),
            )
            Column(modifier.testTag("sleep_debt_card"), verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm)) {
                Box {
                    DebtGauge(
                        debt = debt.value,
                        label = stringResource(R.string.sleep_debt_label),
                        unit = stringResource(R.string.sleep_debt_unit_hours),
                        contentDescription = description,
                    )
                    if (model.potential != null) {
                        val great = model.nightsWith(EnergyLabel.GREAT)
                        val superNights = model.nightsWith(EnergyLabel.SUPER)
                        val greatWord = labelWord(EnergyLabel.GREAT)
                        val superWord = labelWord(EnergyLabel.SUPER)
                        Row(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = DriftTheme.spacing.md, end = DriftTheme.spacing.xs)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "$greatWord $great, $superWord $superNights"
                                },
                            horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
                        ) {
                            Counter(DriftIcons.Energy, greatWord, great)
                            Counter(DriftIcons.Star, superWord, superNights)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    section.band?.let { BandPill(it) }
                    Box(Modifier.weight(1f))
                    DriftIconButton(DriftIcons.Help, stringResource(R.string.sleep_why_debt), { actions.onExplain(MetricKey.SLEEP_DEBT) })
                }
                Caption(
                    stringResource(
                        R.string.sleep_band_basis,
                        DriftFormat.duration(DebtBandBasis.lowBelow),
                        DriftFormat.duration(DebtBandBasis.highAbove),
                        DebtBandBasis.WINDOW_NIGHTS,
                    ),
                )
            }
        }
        is DebtSection.TooFewNights ->
            InsufficientDataPanel(InsufficientReason.TOO_FEW_NIGHTS, section.nightsNeeded, modifier)
        is DebtSection.Unavailable ->
            InsufficientDataPanel(InsufficientReason.TOO_MANY_MISSING_NIGHTS, 0, modifier)
    }
}

@Composable
private fun Counter(icon: androidx.compose.ui.graphics.vector.ImageVector, word: String, n: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = DriftTheme.colors.onSurfaceMuted, modifier = Modifier.size(14.dp))
        Text(word.uppercase(Locale.getDefault()), style = DriftTheme.typography.overline, color = DriftTheme.colors.onSurfaceMuted)
        Text("$n", style = DriftTheme.typography.overline, color = DriftTheme.colors.onSurface)
    }
}

@Composable
private fun BandPill(band: DebtBand) {
    val color = when (band) {
        DebtBand.LOW -> DriftTheme.colors.success
        DebtBand.MODERATE -> DriftTheme.colors.warning
        DebtBand.HIGH -> DriftTheme.colors.error
    }
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm),
    ) {
        Box(Modifier.padding(start = 12.dp).size(8.dp).clip(CircleShape).background(color))
        Text(bandWord(band), style = DriftTheme.typography.label, color = color, modifier = Modifier.padding(end = 12.dp))
    }
}

/** "−1.2 h debt since yesterday" next to "Energy potential 77% >". */
@Composable
private fun TileRow(model: SleepUiModel, actions: SleepActions, modifier: Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm)) {
        val change = model.debtChange
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(DriftTheme.radii.md))
                .background(DriftTheme.colors.surface)
                .padding(DriftTheme.spacing.md)
                .testTag("sleep_change_tile"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (change != null) {
                val up = !change.isNegative && !change.isZero
                val color = if (up) DriftTheme.colors.debt else DriftTheme.colors.repaid
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.size(18.dp).background(color, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (up) "+" else "−", style = DriftTheme.typography.label, color = DriftTheme.colors.background)
                    }
                    Text(
                        stringResource(R.string.sleep_change_value, DriftFormat.hoursDecimal(change.abs())),
                        style = DriftTheme.typography.titleSmall,
                        color = DriftTheme.colors.onSurface,
                    )
                }
                Text(stringResource(R.string.sleep_change_since), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
            } else {
                Text(stringResource(R.string.sleep_change_none), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
            }
        }
        val potential = model.potential
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(DriftTheme.radii.md))
                .background(DriftTheme.colors.surfaceRaised)
                .clickable(onClickLabel = stringResource(R.string.sleep_open_energy), role = Role.Button, onClick = actions.onOpenEnergy)
                .padding(DriftTheme.spacing.md)
                .testTag("sleep_potential_tile"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.sleep_potential_title), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
                if (potential != null) {
                    Text(
                        "${potential.score.value.roundToInt()}%",
                        style = DriftTheme.typography.titleSmall,
                        color = labelColor(potential.label),
                    )
                } else {
                    Text(stringResource(R.string.sleep_potential_pending), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
                }
            }
            Icon(DriftIcons.ChevronRight, null, tint = DriftTheme.colors.onSurface)
        }
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
private fun LastNightCard(model: SleepUiModel, use24h: Boolean, actions: SleepActions, modifier: Modifier) {
    val ep = model.lastNight ?: return
    val total = Duration.between(ep.start, ep.end)
    val times = "${DriftFormat.shortClock(ep.start, model.zone, use24h)} – ${DriftFormat.shortClock(ep.end, model.zone, use24h)}"
    Column(modifier.fillMaxWidth().testTag("sleep_last_night")) {
        Text(
            stringResource(R.string.sleep_last_night_overline).uppercase(Locale.getDefault()),
            style = DriftTheme.typography.overline,
            color = DriftTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(start = DriftTheme.spacing.lg, bottom = DriftTheme.spacing.sm),
        )
        DriftCard(
            onClick = { actions.onOpenNight(ep.id) },
            onClickLabel = stringResource(R.string.sleep_row_open_night),
            contentDescription = stringResource(R.string.sleep_last_night_a11y, DriftFormat.durationSpoken(total), times, sourceName(ep.origin.sourceId)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(DriftFormat.hm(total), style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface)
                    Text(stringResource(R.string.sleep_total_sleep), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
                }
                Text(times, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
                Icon(DriftIcons.ChevronRight, null, tint = DriftTheme.colors.onSurface, modifier = Modifier.padding(start = DriftTheme.spacing.sm))
            }
            DriftDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sm)) {
                Box(
                    Modifier.size(24.dp).background(DriftTheme.colors.accentSurface, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(DriftIcons.Zz, null, tint = DriftTheme.colors.primary, modifier = Modifier.size(14.dp))
                }
                Text(sourceName(ep.origin.sourceId), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurface, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EnergyTodayCard(model: SleepUiModel, use24h: Boolean, actions: SleepActions, modifier: Modifier) {
    val curve = model.todayCurve ?: return
    Column(modifier.fillMaxWidth().testTag("sleep_energy_today")) {
        Text(
            stringResource(R.string.sleep_energy_today).uppercase(Locale.getDefault()),
            style = DriftTheme.typography.overline,
            color = DriftTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(start = DriftTheme.spacing.lg, bottom = DriftTheme.spacing.sm),
        )
        DriftCard(onClick = actions.onOpenEnergy, onClickLabel = stringResource(R.string.sleep_open_energy)) {
            MiniEnergyCurve(curve, now = model.asOf, zone = model.zone, use24h = use24h, height = 88.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sleep_energy_open_schedule),
                    style = DriftTheme.typography.label,
                    color = DriftTheme.colors.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(DriftIcons.ChevronRight, null, tint = DriftTheme.colors.primary)
            }
        }
    }
}

@Composable
private fun NeedCard(need: NeedSection, actions: SleepActions, modifier: Modifier) {
    val caption = if (need.personalized) {
        pluralStringResource(R.plurals.sleep_need_personal, need.informativeNights, need.informativeNights)
    } else {
        pluralStringResource(R.plurals.sleep_need_prior, need.nightsUntilPersonal, need.nightsUntilPersonal)
    }
    DriftCard(
        modifier = modifier,
        contentDescription = stringResource(
            R.string.sleep_need_description,
            DriftFormat.durationSpoken(need.need.value),
            stringResource(need.need.provenance.labelRes()),
            caption,
        ),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) {
                DurationHero(
                    duration = need.need.value,
                    label = stringResource(R.string.sleep_need_label),
                    provenance = need.need.provenance,
                )
            }
            DriftIconButton(DriftIcons.Help, stringResource(R.string.sleep_why_need), { actions.onExplain(MetricKey.SLEEP_NEED) })
        }
        Caption(caption)
    }
}

@Composable
private fun RegularityCard(section: RegularitySection, actions: SleepActions, modifier: Modifier) {
    DriftCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.sleep_regularity_title), Modifier.weight(1f))
            DriftIconButton(DriftIcons.Help, stringResource(R.string.sleep_why_regularity), { actions.onExplain(MetricKey.REGULARITY) })
        }
        if (!section.available || section.sri7 == null) {
            InsufficientDataPanel(InsufficientReason.TOO_FEW_NIGHTS, section.nightsNeeded)
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = stringResource(R.string.sleep_open_body_clock), role = Role.Button, onClick = actions.onOpenBodyClock),
            ) {
                RegularityBlock(section, trendHeight = 120.dp)
            }
        }
    }
}

/** Tonight's ideal bedtime and wake time, the first thing to act on, with the time in bed it gives. */
@Composable
private fun IdealScheduleCard(model: SleepUiModel, use24h: Boolean, actions: SleepActions, modifier: Modifier) {
    val t = model.tonight
    val bed = DriftFormat.shortClock(t.bedtime.value, model.zone, use24h)
    val wake = DriftFormat.shortClock(t.wake.value, model.zone, use24h)
    DriftCard(modifier = modifier.testTag("sleep_ideal_schedule")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.sleep_ideal_title).uppercase(Locale.getDefault()),
                style = DriftTheme.typography.overline,
                color = DriftTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            DriftIconButton(DriftIcons.Help, stringResource(R.string.sleep_why_recommendation), { actions.onExplain(MetricKey.RECOMMENDATION) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md)) {
            TimeColumn(DriftIcons.Moon, stringResource(R.string.sleep_bedtime), bed, DriftTheme.colors.windDown, Modifier.weight(1f))
            TimeColumn(DriftIcons.Sunrise, stringResource(R.string.sleep_wake), wake, DriftTheme.colors.energy, Modifier.weight(1f))
        }
        Text(
            stringResource(R.string.sleep_ideal_in_bed, DriftFormat.hm(t.opportunity.value)),
            style = DriftTheme.typography.bodySmall,
            color = DriftTheme.colors.onSurfaceMuted,
        )
        t.tradeOff?.let { trade ->
            DriftCard(tone = CardTone.Accent, contentDescription = stringResource(R.string.sleep_tradeoff_description, trade.message)) {
                Text(
                    stringResource(R.string.sleep_tradeoff_title),
                    style = DriftTheme.typography.titleSmall,
                    color = DriftTheme.colors.onPrimaryContainer,
                )
                Text(trade.message, style = DriftTheme.typography.body, color = DriftTheme.colors.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun TimeColumn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, time: String, tint: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(DriftTheme.radii.md))
            .background(DriftTheme.colors.surfaceRaised)
            .semantics(mergeDescendants = true) { contentDescription = "$label $time" }
            .padding(DriftTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(label, style = DriftTheme.typography.label, color = DriftTheme.colors.onSurfaceMuted)
        }
        Text(time, style = DriftTheme.typography.display, color = DriftTheme.colors.onSurface)
    }
}
