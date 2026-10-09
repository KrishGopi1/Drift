package app.drift.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Root navigation graph. A0 wires feature entry points here during integration (Phase 2). */
@Composable
fun DriftNavHost() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Drift")
    }
}
