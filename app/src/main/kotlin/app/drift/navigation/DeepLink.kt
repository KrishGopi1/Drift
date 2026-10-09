package app.drift.navigation

import android.content.Intent
import app.drift.background.notify.Notifier
import app.drift.model.EpisodeId

/** Destinations requested by notifications and the widget (extras defined in :core:background). */
sealed interface DeepLink {
    data object Energy : DeepLink
    data class EditSleep(val episodeId: EpisodeId?) : DeepLink

    companion object {
        fun from(intent: Intent?): DeepLink? = when (intent?.getStringExtra(Notifier.EXTRA_DESTINATION)) {
            "energy" -> Energy
            "edit_sleep" -> EditSleep(intent.getStringExtra(Notifier.EXTRA_EPISODE_ID)?.let(::EpisodeId))
            else -> null
        }
    }
}
