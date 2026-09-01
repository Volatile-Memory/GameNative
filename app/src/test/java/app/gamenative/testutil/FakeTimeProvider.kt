package app.gamenative.testutil

import app.gamenative.core.time.TimeProvider
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.TestTimeSource
import kotlin.time.TimeMark

/**
 * Controllable in-memory fake [TimeProvider] for deterministic unit testing.
 */
class FakeTimeProvider(
    initialTime: Instant = Instant.fromEpochMilliseconds(1_000_000L),
) : TimeProvider {

    var currentInstant: Instant = initialTime
    val testTimeSource = TestTimeSource()

    override fun now(): Instant = currentInstant

    override fun markNow(): TimeMark = testTimeSource.markNow()

    /**
     * Advances simulated wall-clock and monotonic time forward.
     */
    fun advanceTimeBy(duration: Duration) {
        currentInstant = currentInstant.plus(duration)
        testTimeSource += duration
    }

    /**
     * Sets wall-clock time directly (e.g. simulating leap second or clock sync).
     */
    fun setWallClockTime(instant: Instant) {
        currentInstant = instant
    }
}
