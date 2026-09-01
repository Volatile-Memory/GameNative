package app.gamenative.core.time

import kotlinx.datetime.Instant
import kotlin.time.TimeMark

/**
 * Pure abstraction for time querying, isolating system time and monotonic clock impurities
 * for deterministic unit testing.
 */
interface TimeProvider {
    /** Wall-clock time. */
    fun now(): Instant

    /** Monotonic time mark, useful for measuring durations. */
    fun markNow(): TimeMark
}
