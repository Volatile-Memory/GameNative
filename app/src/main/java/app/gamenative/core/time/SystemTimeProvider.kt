package app.gamenative.core.time

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Clock.System.now()

    override fun markNow(): TimeMark = TimeSource.Monotonic.markNow()
}
