package app.drift.feature.settings

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.drift.designsystem.DriftCard
import app.drift.designsystem.DriftIcons
import app.drift.designsystem.DriftScreenHeader
import app.drift.designsystem.DriftTheme
import app.drift.designsystem.DriftTopBar
import app.drift.designsystem.HeaderSubtitle
import app.drift.designsystem.ProfileButton

/** One Learn article: title, one-line summary and its paragraphs. */
internal class Article(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @ArrayRes val body: Int,
    val icon: ImageVector,
)

internal val articles: List<Article>
    get() = listOf(
        Article(R.string.learn_debt_title, R.string.learn_debt_summary, R.array.learn_debt_body, DriftIcons.TabSleep),
        Article(R.string.learn_potential_title, R.string.learn_potential_summary, R.array.learn_potential_body, DriftIcons.Energy),
        Article(R.string.learn_schedule_title, R.string.learn_schedule_summary, R.array.learn_schedule_body, DriftIcons.TabEnergy),
        Article(R.string.learn_melatonin_title, R.string.learn_melatonin_summary, R.array.learn_melatonin_body, DriftIcons.Moon),
        Article(R.string.learn_need_title, R.string.learn_need_summary, R.array.learn_need_body, DriftIcons.BodyClock),
        Article(R.string.learn_payback_title, R.string.learn_payback_summary, R.array.learn_payback_body, DriftIcons.TabProgress),
        Article(R.string.learn_habits_title, R.string.learn_habits_summary, R.array.learn_habits_body, DriftIcons.Caffeine),
    )

/**
 * Learn tab: short articles on how sleep debt, energy and the body clock work, then links to the full methodology
 * and the body clock screen.
 */
@Composable
fun LearnScreen(
    onOpenArticle: (Int) -> Unit,
    onOpenMethodology: () -> Unit,
    onOpenBodyClock: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(DriftTheme.colors.background).verticalScroll(rememberScrollState())) {
        DriftScreenHeader(
            title = stringResource(R.string.learn_title),
            subtitle = { HeaderSubtitle(stringResource(R.string.learn_subtitle)) },
            trailing = { ProfileButton(onOpenSettings) },
        )
        Column(
            Modifier.padding(horizontal = DriftTheme.spacing.gutter).padding(bottom = DriftTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md),
        ) {
            articles.forEachIndexed { i, a ->
                LearnRow(stringResource(a.title), stringResource(a.summary), a.icon, { onOpenArticle(i) }, "learn_article_$i")
            }
            Text(
                stringResource(R.string.learn_more_title),
                style = DriftTheme.typography.title,
                color = DriftTheme.colors.onSurface,
                modifier = Modifier.padding(top = DriftTheme.spacing.md).semantics { heading() },
            )
            LearnRow(stringResource(R.string.learn_body_clock_title), stringResource(R.string.learn_body_clock_summary), DriftIcons.BodyClock, onOpenBodyClock, "learn_body_clock")
            LearnRow(stringResource(R.string.learn_methodology_title), stringResource(R.string.learn_methodology_summary), DriftIcons.Info, onOpenMethodology, "learn_methodology")
        }
    }
}

@Composable
private fun LearnRow(title: String, summary: String, icon: ImageVector, onClick: () -> Unit, tag: String) {
    DriftCard(modifier = Modifier.testTag(tag), onClick = onClick, onClickLabel = title) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DriftTheme.spacing.md)) {
            Box(
                Modifier.size(44.dp).background(DriftTheme.colors.accentSurface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = DriftTheme.colors.primary, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = DriftTheme.typography.titleSmall, color = DriftTheme.colors.onSurface)
                Text(summary, style = DriftTheme.typography.bodySmall, color = DriftTheme.colors.onSurfaceMuted)
            }
            Icon(DriftIcons.ChevronRight, null, tint = DriftTheme.colors.onSurfaceMuted)
        }
    }
}

/** One article, read top to bottom. */
@Composable
fun ArticleScreen(index: Int, onBack: () -> Unit) {
    val a = articles.getOrNull(index) ?: articles.first()
    Column(Modifier.fillMaxSize().background(DriftTheme.colors.background)) {
        DriftTopBar(stringResource(a.title), onBack = onBack, applyStatusBarInsets = true)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DriftTheme.spacing.gutter)
                .padding(bottom = DriftTheme.spacing.xl)
                .testTag("learn_article_body"),
            verticalArrangement = Arrangement.spacedBy(DriftTheme.spacing.lg),
        ) {
            Text(stringResource(a.summary), style = DriftTheme.typography.title, color = DriftTheme.colors.onSurface)
            stringArrayResource(a.body).forEach {
                Text(it, style = DriftTheme.typography.body, color = DriftTheme.colors.onSurfaceMuted)
            }
        }
    }
}
