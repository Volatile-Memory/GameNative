package app.gamenative.core.id

/**
 * Pure abstraction for ID and UUID generation, allowing deterministic ID generation in tests.
 */
interface IdGenerator {
    /** Generates a standard random UUID string. */
    fun generateUuid(): String

    /** Generates a prefixed unique ID string (e.g. "STEAM_123_456"). */
    fun generatePrefixedId(prefix: String): String
}
