package app.drift.background.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.drift.background.work.Work
import app.drift.model.repository.ReminderKind

/** Receives a reminder alarm and queues the notification job (which checks settings and pending state first). */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { name -> ReminderKind.entries.firstOrNull { it.name == name } } ?: return
        Work.enqueueReminder(context, kind)
    }

    companion object {
        const val ACTION = "app.drift.background.REMINDER"
        const val EXTRA_KIND = "kind"
        const val EXTRA_AT = "at"
    }
}
