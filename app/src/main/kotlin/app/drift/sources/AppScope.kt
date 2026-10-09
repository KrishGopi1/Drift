package app.drift.sources

import javax.inject.Qualifier

/** Application-lifetime coroutine scope for fire-and-forget work started by receivers. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
