package app.drift.background.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.content.ContextCompat
import app.drift.background.R
import app.drift.model.engine.EnergyPoint
import java.time.Duration

/** Draws the mini energy curve with a "now" marker onto a Bitmap (Glance cannot draw paths directly). */
object WidgetRenderer {
    private const val STALE_ALPHA = 90

    fun render(context: Context, state: WidgetState, widthPx: Int, heightPx: Int): Bitmap {
        val w = widthPx.coerceAtLeast(2)
        val h = heightPx.coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val density = context.resources.displayMetrics.density
        val curveColor = ContextCompat.getColor(context, R.color.widget_curve)
        val fillColor = ContextCompat.getColor(context, R.color.widget_fill)
        val markerColor = ContextCompat.getColor(context, R.color.widget_marker)
        val baseColor = ContextCompat.getColor(context, R.color.widget_baseline)

        val pad = 6f * density
        val baseY = h - pad
        val baseline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor; strokeWidth = density; style = Paint.Style.STROKE }
        canvas.drawLine(pad, baseY, w - pad, baseY, baseline)

        val model = when (state) {
            is WidgetState.Fresh -> state.model
            is WidgetState.Stale -> state.model
            WidgetState.Empty -> return bitmap
        }
        val dim = state is WidgetState.Stale
        val xy = project(model.points, w.toFloat(), h.toFloat(), pad)
        if (xy.size < 2) return bitmap

        val line = Path().apply {
            moveTo(xy[0].first, xy[0].second)
            for (i in 1 until xy.size) {
                // Midpoint smoothing keeps the curve free of the kinks 5-minute samples would show.
                val (px, py) = xy[i - 1]
                val (cx, cy) = xy[i]
                quadTo(px, py, (px + cx) / 2f, (py + cy) / 2f)
            }
            lineTo(xy.last().first, xy.last().second)
        }
        val fill = Path(line).apply {
            lineTo(xy.last().first, baseY)
            lineTo(xy.first().first, baseY)
            close()
        }
        canvas.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(0f, pad, 0f, baseY, fillColor, fillColor and 0x00FFFFFF, Shader.TileMode.CLAMP)
            if (dim) alpha = STALE_ALPHA / 2
        })
        canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = curveColor
            if (dim) alpha = STALE_ALPHA
        })

        model.nowFraction?.let { f ->
            val x = pad + f * (w - 2 * pad)
            val y = yAt(model.points, f, h.toFloat(), pad)
            canvas.drawLine(x, pad, x, baseY, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = markerColor; strokeWidth = 1.5f * density; if (dim) alpha = STALE_ALPHA
            })
            canvas.drawCircle(x, y, 4.5f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = markerColor; style = Paint.Style.FILL; if (dim) alpha = STALE_ALPHA
            })
        }
        return bitmap
    }

    private fun project(points: List<EnergyPoint>, w: Float, h: Float, pad: Float): List<Pair<Float, Float>> {
        if (points.size < 2) return emptyList()
        val spanMs = Duration.between(points.first().at, points.last().at).toMillis().toFloat().coerceAtLeast(1f)
        return points.map { p ->
            val f = Duration.between(points.first().at, p.at).toMillis() / spanMs
            pad + f * (w - 2 * pad) to levelToY(p.level, h, pad)
        }
    }

    private fun levelToY(level: Double, h: Float, pad: Float): Float {
        val frac = (level / 100.0).coerceIn(0.0, 1.0).toFloat()
        return (h - pad) - frac * (h - 2 * pad)
    }

    private fun yAt(points: List<EnergyPoint>, fraction: Float, h: Float, pad: Float): Float {
        val spanMs = Duration.between(points.first().at, points.last().at).toMillis().toFloat().coerceAtLeast(1f)
        val idx = points.indexOfFirst { Duration.between(points.first().at, it.at).toMillis() / spanMs >= fraction }.let { if (it < 0) points.lastIndex else it }
        return levelToY(points[idx].level, h, pad)
    }
}
