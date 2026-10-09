package app.drift.background.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Seam so workers can ask for a widget redraw without depending on Glance (and tests can fake it). */
interface WidgetRefresher {
    suspend fun update()
}

class GlanceWidgetRefresher @Inject constructor(@ApplicationContext private val context: Context) : WidgetRefresher {
    override suspend fun update() {
        DriftWidget().updateAll(context)
    }
}
