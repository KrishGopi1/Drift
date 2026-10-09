package app.drift.background.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.drift.background.work.Work
import app.drift.model.EpisodeId

/** Handles the "Confirm" button on the morning notification by queueing the confirm job. */
class ConfirmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONFIRM) return
        val id = intent.getStringExtra(EXTRA_EPISODE) ?: return
        Work.enqueueConfirm(context, EpisodeId(id))
    }

    companion object {
        const val ACTION_CONFIRM = "app.drift.background.CONFIRM_SLEEP"
        const val EXTRA_EPISODE = "episode"
    }
}
