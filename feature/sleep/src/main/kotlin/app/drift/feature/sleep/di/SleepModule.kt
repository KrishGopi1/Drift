package app.drift.feature.sleep.di

import android.content.Context
import app.drift.feature.sleep.R
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Qualifier

/** The clock (and therefore the "current zone") used by the sleep screens. Local to this module. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SleepClock

/** Source of ids for manually entered episodes and naps. */
fun interface EpisodeIdGenerator {
    fun next(): String
}

/** Texts a ViewModel has to put into a [app.drift.model.ui.ScreenState] (which carries plain strings). */
interface SleepTexts {
    fun sourceProblem(sourceName: String): String
    fun loadFailed(): String
    fun nightUnavailable(): String
    fun saveFailed(): String
}

class AndroidSleepTexts @Inject constructor(@ApplicationContext private val context: Context) : SleepTexts {
    override fun sourceProblem(sourceName: String): String = context.getString(R.string.sleep_source_problem, sourceName)
    override fun loadFailed(): String = context.getString(R.string.sleep_load_failed)
    override fun nightUnavailable(): String = context.getString(R.string.sleep_night_unavailable)
    override fun saveFailed(): String = context.getString(R.string.sleep_save_failed)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SleepBindingsModule {
    @Binds
    abstract fun bindTexts(impl: AndroidSleepTexts): SleepTexts

    companion object {
        @Provides
        @SleepClock
        fun provideClock(): Clock = Clock.systemDefaultZone()

        @Provides
        fun provideIdGenerator(): EpisodeIdGenerator = EpisodeIdGenerator { UUID.randomUUID().toString() }
    }
}
