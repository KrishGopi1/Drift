package app.drift.source.healthconnect

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Plain-View screen that Health Connect opens to explain why Drift asks for access. */
class PermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.density * 20).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        column.addView(
            TextView(this).apply {
                setText(R.string.hc_rationale_title)
                textSize = 22f
                setPadding(0, 0, 0, pad / 2)
            },
        )
        column.addView(TextView(this).apply { setText(R.string.hc_rationale_body); textSize = 16f })
        column.addView(
            Button(this).apply {
                setText(android.R.string.ok)
                setOnClickListener { finish() }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .also { it.gravity = Gravity.END; it.topMargin = pad }
            },
        )
        setContentView(ScrollView(this).apply { addView(column) })
    }
}
