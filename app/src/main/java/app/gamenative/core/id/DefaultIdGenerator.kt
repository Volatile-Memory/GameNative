package app.gamenative.core.id

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultIdGenerator @Inject constructor() : IdGenerator {
    override fun generateUuid(): String = UUID.randomUUID().toString()

    override fun generatePrefixedId(prefix: String): String = "${prefix}_${UUID.randomUUID()}"
}
