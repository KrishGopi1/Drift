package app.drift.background.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.drift.background.R
import app.drift.background.notify.NotificationContent
import app.drift.model.repository.AnalysisRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun analysisRepository(): AnalysisRepository
}

/** Home-screen widget: today's energy curve, the current phase and what comes next. */
class DriftWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = try {
            EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .analysisRepository().observeLatest().first()
        } catch (_: Exception) {
            null
        }
        val clock = Clock.systemDefaultZone()
        val state = WidgetStateMapper.map(snapshot, clock.instant(), clock.zone)
        val bitmap = WidgetRenderer.render(context, state, BITMAP_WIDTH, BITMAP_HEIGHT)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val texts = WidgetTexts.of(context, state, clock.zone)

        provideContent { WidgetContent(texts, bitmap, launch, context.getString(R.string.widget_curve_description)) }
    }

    companion object {
        private const val BITMAP_WIDTH = 640
        private const val BITMAP_HEIGHT = 240
    }
}

/** The two text lines under the curve, already localised. */
data class WidgetTexts(val primary: String, val secondary: String?) {
    companion object {
        fun of(context: Context, state: WidgetState, zone: ZoneId, use24Hour: Boolean? = null): WidgetTexts = when (state) {
            WidgetState.Empty -> WidgetTexts(context.getString(R.string.widget_empty), null)
            is WidgetState.Fresh -> fromModel(context, state.model, zone, use24Hour)
            is WidgetState.Stale -> WidgetTexts(context.getString(R.string.widget_stale), fromModel(context, state.model, zone, use24Hour).primary)
        }

        private fun fromModel(context: Context, model: WidgetModel, zone: ZoneId, use24Hour: Boolean?): WidgetTexts {
            val is24 = use24Hour ?: android.text.format.DateFormat.is24HourFormat(context)
            val current = model.currentPhase?.let { context.getString(R.string.widget_now, PhaseLabels.of(context, it)) }
            val next = if (model.nextPhase != null && model.nextPhaseAt != null) {
                context.getString(R.string.widget_next, PhaseLabels.of(context, model.nextPhase), NotificationContent.formatTime(model.nextPhaseAt, zone, is24))
            } else null
            return WidgetTexts(current ?: next.orEmpty(), if (current != null) next else null)
        }
    }
}

@Composable
private fun WidgetContent(texts: WidgetTexts, curve: android.graphics.Bitmap, launch: Intent?, curveDescription: String) {
    val open = launch?.let { actionStartActivity(it) }
    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .let { m -> if (open != null) m.clickable(open) else m },
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(curve),
                contentDescription = curveDescription,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.fillMaxWidth().height(72.dp),
            )
            Text(
                text = texts.primary,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            texts.secondary?.let {
                Text(text = it, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
            }
        }
    }
}
