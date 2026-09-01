package app.gamenative.core.time

import app.gamenative.testutil.FakeTimeProvider
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class TimeProviderTest {

    @Test
    fun fakeTimeProvider_advancesTimeCorrectly() {
        val initialTime = Instant.fromEpochMilliseconds(1000L)
        val fakeTime = FakeTimeProvider(initialTime = initialTime)
        
        val mark = fakeTime.markNow()

        assertEquals(initialTime, fakeTime.now())

        fakeTime.advanceTimeBy(250.milliseconds)

        assertEquals(Instant.fromEpochMilliseconds(1250L), fakeTime.now())
        assertEquals(250.milliseconds, mark.elapsedNow())
    }

    @Test
    fun fakeTimeProvider_setWallClockTime() {
        val fakeTime = FakeTimeProvider(initialTime = Instant.fromEpochMilliseconds(1000L))
        val newTime = Instant.fromEpochMilliseconds(5000L)
        fakeTime.setWallClockTime(newTime)
        assertEquals(newTime, fakeTime.now())
    }
}
