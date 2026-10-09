package app.drift.background.notify

import android.content.Context
import app.drift.background.R
import app.drift.model.SleepEpisode
import app.drift.model.repository.ReminderKind
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ReminderText(val title: String, val body: String)

/** Text for notifications. Times use the user's clock preference. */
object NotificationContent {
    fun reminder(context: Context, kind: ReminderKind): ReminderText = when (kind) {
        ReminderKind.WIND_DOWN -> ReminderText(context.getString(R.string.reminder_wind_down_title), context.getString(R.string.reminder_wind_down_body))
        ReminderKind.MELATONIN_WINDOW -> ReminderText(context.getString(R.string.reminder_melatonin_title), context.getString(R.string.reminder_melatonin_body))
        ReminderKind.CAFFEINE_CUTOFF -> ReminderText(context.getString(R.string.reminder_caffeine_title), context.getString(R.string.reminder_caffeine_body))
        ReminderKind.MORNING_LIGHT -> ReminderText(context.getString(R.string.reminder_light_title), context.getString(R.string.reminder_light_body))
        ReminderKind.CONFIRM_SLEEP -> ReminderText(context.getString(R.string.confirm_title), context.getString(R.string.confirm_generic_body))
        ReminderKind.ENERGY_CHECK_IN -> ReminderText(context.getString(R.string.reminder_checkin_title), context.getString(R.string.reminder_checkin_body))
    }

    /** "Looks like you slept 11:40 pm to 7:05 am". [use24Hour] null falls back to the device setting. */
    fun confirmation(context: Context, episode: SleepEpisode, zone: ZoneId, use24Hour: Boolean?): ReminderText {
        val is24 = use24Hour ?: android.text.format.DateFormat.is24HourFormat(context)
        val body = context.getString(
            R.string.confirm_body,
            formatTime(episode.start, zone, is24),
            formatTime(episode.end, zone, is24),
        )
        return ReminderText(context.getString(R.string.confirm_title), body)
    }

    fun formatTime(at: java.time.Instant, zone: ZoneId, use24Hour: Boolean): String {
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(at.atZone(zone))
    }
}
