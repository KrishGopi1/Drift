package app.drift.background.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.drift.background.work.MorningSyncWorker
import app.drift.background.work.Work

/** Fires from the morning alarm windows and hands the actual work to an expedited WorkManager job. */
class MorningAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        Work.enqueueMorningSync(context, fallback = intent.getBooleanExtra(EXTRA_FALLBACK, false))
    }

    companion object {
        const val ACTION = "app.drift.background.MORNING_SYNC"
        const val EXTRA_FALLBACK = "fallback"
    }
}
