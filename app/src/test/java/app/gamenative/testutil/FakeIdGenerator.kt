package app.gamenative.testutil

import app.gamenative.core.id.IdGenerator
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic in-memory fake [IdGenerator] for testing.
 */
class FakeIdGenerator(
    private val defaultPrefix: String = "test-uuid",
) : IdGenerator {

    private val counter = AtomicInteger(0)

    override fun generateUuid(): String {
        val next = counter.incrementAndGet()
        return "$defaultPrefix-$next"
    }

    override fun generatePrefixedId(prefix: String): String {
        val next = counter.incrementAndGet()
        return "${prefix}_$next"
    }

    fun reset() {
        counter.set(0)
    }
}
