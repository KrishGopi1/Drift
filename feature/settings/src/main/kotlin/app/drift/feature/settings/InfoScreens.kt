package app.drift.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.drift.designsystem.ButtonStyle
import app.drift.designsystem.DriftButton
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftDivider
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.DriftTopBar

private class Method(val title: Int, val summary: Int, val params: Int, val cites: Int)

private val methods = listOf(
    Method(R.string.st_meth_need_title, R.string.st_meth_need_summary, R.string.st_meth_need_params, R.array.st_meth_need_cites),
    Method(R.string.st_meth_debt_title, R.string.st_meth_debt_summary, R.string.st_meth_debt_params, R.array.st_meth_debt_cites),
    Method(R.string.st_meth_sri_title, R.string.st_meth_sri_summary, R.string.st_meth_sri_params, R.array.st_meth_sri_cites),
    Method(R.string.st_meth_chrono_title, R.string.st_meth_chrono_summary, R.string.st_meth_chrono_params, R.array.st_meth_chrono_cites),
    Method(R.string.st_meth_clock_title, R.string.st_meth_clock_summary, R.string.st_meth_clock_params, R.array.st_meth_clock_cites),
    Method(R.string.st_meth_energy_title, R.string.st_meth_energy_summary, R.string.st_meth_energy_params, R.array.st_meth_energy_cites),
    Method(R.string.st_meth_sufficiency_title, R.string.st_meth_sufficiency_summary, R.string.st_meth_sufficiency_params, R.array.st_meth_sufficiency_cites),
)

/** Plain-language explanation of every metric with its parameters and citations. Static text, no network. */
@Composable
fun MethodologyScreen(onBack: () -> Unit, initiallyExpanded: Set<Int> = emptySet()) {
    Column(Modifier.fillMaxSize().background(DriftTheme.colors.background)) {
        DriftTopBar(stringResource(R.string.st_meth_title), onBack = onBack, applyStatusBarInsets = true)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = DriftTheme.spacing.gutter).padding(bottom = DriftTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sectionGap),
        ) {
            Text(stringResource(R.string.st_meth_intro), style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted)
            methods.forEachIndexed { index, m ->
                var expanded by rememberSaveable { mutableStateOf(index in initiallyExpanded) }
                val toggleLabel = stringResource(if (expanded) R.string.st_meth_collapse else R.string.st_meth_expand)
                DriftCard {
                    Text(
                        stringResource(m.title), style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(m.summary), style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted)
                    if (expanded) {
                        DriftDivider()
                        Text(stringResource(R.string.st_meth_parameters), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
                        Text(stringResource(m.params), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
                        Text(stringResource(R.string.st_meth_citations), style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
                        stringArrayResource(m.cites).forEach {
                            Text(it, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
                        }
                    }
                    DriftButton(
                        toggleLabel, { expanded = !expanded }, style = ButtonStyle.Text,
                        modifier = Modifier.semantics { stateDescription = if (expanded) "expanded" else "collapsed" },
                    )
                }
            }
        }
    }
}

/** The Manrope OFL text and the list of libraries. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ofl = remember {
        try {
            context.resources.openRawResource(R.raw.manrope_ofl).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }
    Column(Modifier.fillMaxSize().background(DriftTheme.colors.background)) {
        DriftTopBar(stringResource(R.string.st_lic_title), onBack = onBack, applyStatusBarInsets = true)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = DriftTheme.spacing.gutter).padding(bottom = DriftTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.sectionGap),
        ) {
            DriftCard {
                Text(stringResource(R.string.st_lic_libs_title), style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.st_lic_libs_body), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
                stringArrayResource(R.array.st_lic_libs).forEach {
                    Text(it, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurface)
                }
            }
            DriftCard {
                Text(stringResource(R.string.st_lic_font_title), style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.st_lic_font_body), style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
                Text(ofl, style = DriftTheme.typography.caption, color = DriftTheme.colors.onSurfaceMuted)
            }
        }
    }
}
