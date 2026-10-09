package app.drift.source.phone

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Cheap point samples of things the OS cannot replay later: next alarm, plugged state, step counter.
 * Runs a few times a day from an inexact alarm window; there is no service and no wakelock.
 */
internal class PhoneSampler(
    private val system: PhoneSystem,
    private val store: PhoneStore,
) {
    suspend fun sample(now: Instant) {
        system.isPlugged()?.let { store.addCharging(now, it, now) }
        system.nextAlarmAt()?.let { trigger ->
            if (trigger.isAfter(now) && trigger < now.plus(Duration.ofHours(36))) store.addAlarm(AlarmSample(now, trigger), now)
        }
        system.stepCounter()?.let { (total, boot) -> store.addStepSample(StepSample(now, total, boot), now) }
    }

    companion object {
        /** Local times of day at which samples are taken: evening (alarm for tomorrow) and deep night. */
        val SLOTS: List<LocalTime> = listOf(LocalTime.of(21, 30), LocalTime.of(2, 30))
        val WINDOW: Duration = Duration.ofMinutes(10)

        fun nextSlot(now: Instant, zone: ZoneId): Instant {
            val local = now.atZone(zone)
            val candidates = (0..1).flatMap { d -> SLOTS.map { ZonedDateTime.of(local.toLocalDate().plusDays(d.toLong()), it, zone) } }
            return candidates.map { it.toInstant() }.filter { it.isAfter(now) }.min()
        }

        fun arm(context: Context, now: Instant, zone: ZoneId) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pendingIntent(context)
            am.setWindow(AlarmManager.RTC, nextSlot(now, zone).toEpochMilli(), WINDOW.toMillis(), pi)
        }

        fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, PhoneMaintenanceReceiver::class.java).setAction(PhoneMaintenanceReceiver.ACTION_SAMPLE)
            return PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
