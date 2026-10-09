package app.drift.designsystem

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/** Motion tokens. Durations in ms. When reduced motion is on, every animation snaps to its end state. */
@Immutable
data class DriftMotion(
    val instant: Int = 90,
    val short: Int = 160,
    val medium: Int = 280,
    val long: Int = 480,
    /** Energy curve draw-in. */
    val drawIn: Int = 1100,
    /** Metric count-up. */
    val countUp: Int = 900,
    /** Now-marker pulse period. */
    val pulse: Int = 1800,
    /** Delay between consecutive staggered cards. */
    val staggerStep: Int = 45,
    /** Never stagger more items than this; later ones appear with the last delay. */
    val staggerMaxItems: Int = 8,
    val shimmer: Int = 1400,
) {
    /** CSS-like (0.2, 0, 0, 1): default for movement. */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    /** Slow-out for draw-in and count-up. */
    val emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    fun staggerDelay(index: Int): Int = index.coerceIn(0, staggerMaxItems) * staggerStep

    fun <T> spec(reduced: Boolean, durationMs: Int, easing: Easing = standard, delayMs: Int = 0): FiniteAnimationSpec<T> =
        if (reduced || durationMs <= 0) snap() else tween(durationMs, delayMs, easing)
}

/** True when the user turned animations off (Android "Remove animations" sets the animator scale to 0). */
fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale <= 0f

private fun readAnimatorScale(context: Context): Float =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/** Reads the system animator duration scale and follows live changes. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember(context) { mutableStateOf(isReducedMotion(readAnimatorScale(context))) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = isReducedMotion(readAnimatorScale(context))
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/** Animates 0 -> [target] on first composition (count-up) and between later targets. Snaps when reduced. */
@Composable
fun rememberCountUp(target: Float, reduced: Boolean = DriftTheme.reducedMotion): State<Float> {
    val motion = DriftTheme.motion
    val anim = remember { Animatable(if (reduced) target else 0f) }
    LaunchedEffect(target, reduced) {
        if (reduced) anim.snapTo(target) else anim.animateTo(target, motion.spec(false, motion.countUp, motion.emphasized))
    }
    return anim.asState()
}
