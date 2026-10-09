package app.drift.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Original geometric icon set (24 dp grid, 2 dp strokes). Tint with `Icon(tint = ...)`. */
object DriftIcons {
    private fun stroke(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun fill(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        }.build()

    val Back: ImageVector by lazy { stroke("Back", "M19 12 H5", "M11 6 L5 12 L11 18") }
    val Close: ImageVector by lazy { stroke("Close", "M6 6 L18 18", "M18 6 L6 18") }
    val ChevronRight: ImageVector by lazy { stroke("ChevronRight", "M9 5 L16 12 L9 19") }
    val Check: ImageVector by lazy { stroke("Check", "M5 12.5 L10 17.5 L19 7") }
    val Refresh: ImageVector by lazy { stroke("Refresh", "M19 12 A7 7 0 1 1 16.5 6.6", "M18 3 V7.5 H13.5") }
    val Info: ImageVector by lazy { stroke("Info", "M12 3 A9 9 0 1 1 12 21 A9 9 0 1 1 12 3 Z", "M12 11 V16", "M12 8 V8.2") }
    val Warning: ImageVector by lazy { stroke("Warning", "M12 4 L21 19 H3 Z", "M12 10 V14", "M12 16.8 V17") }
    val Energy: ImageVector by lazy { fill("Energy", "M13.5 2.5 L5.5 13.5 H11 L10 21.5 L18.5 10 H13 Z") }
    val Moon: ImageVector by lazy { fill("Moon", "M20 14.5 A8.5 8.5 0 1 1 9.5 4 A6.8 6.8 0 0 0 20 14.5 Z") }
    val BodyClock: ImageVector by lazy {
        stroke("BodyClock", "M12 3 A9 9 0 1 1 12 21 A9 9 0 1 1 12 3 Z", "M12 7 V12 L15.5 14")
    }
    val Settings: ImageVector by lazy {
        stroke(
            "Settings", "M4 7 H7", "M11 7 H20", "M4 17 H13", "M17 17 H20",
            "M7 7 A2 2 0 1 0 11 7 A2 2 0 1 0 7 7 Z", "M13 17 A2 2 0 1 0 17 17 A2 2 0 1 0 13 17 Z",
        )
    }
    val Shield: ImageVector by lazy { stroke("Shield", "M12 3 L19 6 V12 C19 16.5 16 19.5 12 21 C8 19.5 5 16.5 5 12 V6 Z") }
}
