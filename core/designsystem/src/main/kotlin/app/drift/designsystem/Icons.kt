package app.drift.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icon set: Material Symbols Rounded (Apache 2.0, via material-icons-extended) for general glyphs, plus a few
 * drawn here on the same 24 dp grid (tab icons and the sleep mark). Tint with `Icon(tint = ...)`.
 */
object DriftIcons {
    private fun stroke(name: String, width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    // ---- Tabs (drawn) --------------------------------------------------------------------------
    /** Sleep tab: a ring around a solid core. */
    val TabSleep: ImageVector by lazy {
        ImageVector.Builder("TabSleep", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = addPathNodes("M12 2.75 A9.25 9.25 0 1 1 12 21.25 A9.25 9.25 0 1 1 12 2.75 Z"),
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            )
            addPath(pathData = addPathNodes("M12 7.5 A4.5 4.5 0 1 1 12 16.5 A4.5 4.5 0 1 1 12 7.5 Z"), fill = SolidColor(Color.Black))
        }.build()
    }

    /** Energy tab: stacked lines whose lengths trace a day's rise and dip. */
    val TabEnergy: ImageVector by lazy {
        stroke("TabEnergy", 1.8f, "M4 4.5 H15", "M4 8.25 H20", "M4 12 H17", "M4 15.75 H12", "M4 19.5 H18")
    }
    val TabProgress: ImageVector get() = Icons.AutoMirrored.Rounded.ShowChart
    val TabLearn: ImageVector get() = Icons.Outlined.Lightbulb
    val Profile: ImageVector get() = Icons.Outlined.AccountCircle

    /** Small "zz" mark used on sleep blocks. */
    val Zz: ImageVector by lazy { stroke("Zz", 2f, "M5 5 H11 L5 12 H11", "M13.5 12 H18.5 L13.5 18 H18.5") }

    // ---- General ---------------------------------------------------------------------------------
    val Back: ImageVector get() = Icons.AutoMirrored.Rounded.ArrowBack
    val Close: ImageVector get() = Icons.Rounded.Close
    val ChevronRight: ImageVector get() = Icons.AutoMirrored.Rounded.KeyboardArrowRight
    val ChevronDown: ImageVector get() = Icons.Rounded.KeyboardArrowDown
    val ChevronUp: ImageVector get() = Icons.Rounded.KeyboardArrowUp
    val Check: ImageVector get() = Icons.Rounded.Check
    val Refresh: ImageVector get() = Icons.Rounded.Refresh
    val Info: ImageVector get() = Icons.Rounded.Info
    val Help: ImageVector get() = Icons.Rounded.HelpOutline
    val Warning: ImageVector get() = Icons.Rounded.Warning
    val Energy: ImageVector get() = Icons.Rounded.Bolt
    val Star: ImageVector get() = Icons.Rounded.Star
    val Moon: ImageVector get() = Icons.Rounded.Bedtime
    val MoonSmall: ImageVector get() = Icons.Rounded.Nightlight
    val Sun: ImageVector get() = Icons.Rounded.WbSunny
    val Sunrise: ImageVector get() = Icons.Rounded.WbTwilight
    val BodyClock: ImageVector get() = Icons.Rounded.Schedule
    val Settings: ImageVector get() = Icons.Rounded.Tune
    val Shield: ImageVector get() = Icons.Rounded.Lock
    val Add: ImageVector get() = Icons.Rounded.Add
    val Alarm: ImageVector get() = Icons.Rounded.Alarm
    val Cut: ImageVector get() = Icons.Rounded.ContentCut
    val Edit: ImageVector get() = Icons.Rounded.Edit
    val Delete: ImageVector get() = Icons.Rounded.Delete

    // ---- Habits ----------------------------------------------------------------------------------
    val Caffeine: ImageVector get() = Icons.Rounded.LocalCafe
    val Meal: ImageVector get() = Icons.Rounded.Restaurant
    val Alcohol: ImageVector get() = Icons.Rounded.LocalBar
    val Daylight: ImageVector get() = Icons.Rounded.WbSunny
    val Exercise: ImageVector get() = Icons.Rounded.DirectionsRun
    val DimLights: ImageVector get() = Icons.Rounded.Nightlight
}
