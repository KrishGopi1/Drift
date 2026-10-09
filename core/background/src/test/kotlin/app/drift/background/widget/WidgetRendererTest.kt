package app.drift.background.widget

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.drift.background.Fixtures
import app.drift.background.Fixtures.at
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class WidgetRendererTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun opaquePixels(bitmap: android.graphics.Bitmap): Int {
        var n = 0
        for (x in 0 until bitmap.width step 2) for (y in 0 until bitmap.height step 2) if (Color.alpha(bitmap.getPixel(x, y)) > 0) n++
        return n
    }

    @Test fun `empty state draws only the baseline`() {
        val bmp = WidgetRenderer.render(context, WidgetState.Empty, 320, 120)
        assertThat(bmp.width).isEqualTo(320)
        assertThat(bmp.height).isEqualTo(120)
        assertThat(opaquePixels(bmp)).isGreaterThan(0)
        assertThat(opaquePixels(bmp)).isLessThan(400)
    }

    @Test fun `fresh state draws the curve and marker`() {
        val state = WidgetStateMapper.map(Fixtures.snapshot(computedAt = at(13)), at(14, 30), Fixtures.zone)
        val bmp = WidgetRenderer.render(context, state, 320, 120)
        assertThat(opaquePixels(bmp)).isGreaterThan(1500)
    }

    @Test fun `stale state is drawn dimmer than fresh`() {
        val fresh = WidgetStateMapper.map(Fixtures.snapshot(computedAt = at(13)), at(14, 30), Fixtures.zone)
        val stale = WidgetStateMapper.map(Fixtures.snapshot(computedAt = at(0, 0, -1)), at(14, 30), Fixtures.zone)
        assertThat(stale).isInstanceOf(WidgetState.Stale::class.java)
        val a = WidgetRenderer.render(context, fresh, 320, 120)
        val b = WidgetRenderer.render(context, stale, 320, 120)
        fun alphaSum(bm: android.graphics.Bitmap): Long {
            var s = 0L
            for (x in 0 until bm.width) for (y in 0 until bm.height) s += Color.alpha(bm.getPixel(x, y))
            return s
        }
        assertThat(alphaSum(b)).isLessThan(alphaSum(a))
    }

    @Test fun `tiny sizes do not crash`() {
        val state = WidgetStateMapper.map(Fixtures.snapshot(computedAt = at(13)), at(14, 30), Fixtures.zone)
        assertThat(WidgetRenderer.render(context, state, 1, 1).width).isAtLeast(2)
    }

    @Test fun `widget texts for each state`() {
        val zone = Fixtures.zone
        val empty = WidgetTexts.of(context, WidgetState.Empty, zone, true)
        assertThat(empty.primary).contains("Open Drift")
        assertThat(empty.secondary).isNull()

        val fresh = WidgetTexts.of(context, WidgetStateMapper.map(Fixtures.snapshot(computedAt = at(13)), at(14, 30), zone), zone, true)
        assertThat(fresh.primary).isEqualTo("Now: Afternoon dip")
        assertThat(fresh.secondary).isEqualTo("Next: Evening peak at 16:00")

        val stale = WidgetTexts.of(context, WidgetStateMapper.map(Fixtures.snapshot(computedAt = at(0, 0, -1)), at(14, 30), zone), zone, true)
        assertThat(stale.primary).contains("Out of date")
        assertThat(stale.secondary).isEqualTo("Now: Afternoon dip")
    }
}
