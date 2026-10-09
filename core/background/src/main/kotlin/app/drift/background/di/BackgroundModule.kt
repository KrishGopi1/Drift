package app.drift.background.di

import android.app.AlarmManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import app.drift.background.widget.GlanceWidgetRefresher
import app.drift.background.widget.WidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** Clock used by background scheduling (qualified so it cannot clash with other modules' clocks). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackgroundClock

/** This module's own bindings. The scheduler, workers and notifier are constructor-injected. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackgroundBindings {
    @Binds abstract fun widgetRefresher(impl: GlanceWidgetRefresher): WidgetRefresher
}

@Module
@InstallIn(SingletonComponent::class)
object BackgroundProvides {
    @Provides @Singleton fun alarmManager(@ApplicationContext context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Provides @Singleton fun notificationManager(@ApplicationContext context: Context): NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    @Provides @Singleton fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides @BackgroundClock fun clock(): Clock = Clock.systemDefaultZone()
}
