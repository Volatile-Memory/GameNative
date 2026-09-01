package app.gamenative.core.id

import app.gamenative.testutil.FakeIdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdGeneratorTest {

    @Test
    fun defaultIdGenerator_generatesUniqueValues() {
        val generator = DefaultIdGenerator()
        val id1 = generator.generateUuid()
        val id2 = generator.generateUuid()
        assertNotEquals(id1, id2)

        val prefixed = generator.generatePrefixedId("STEAM")
        assertEquals(true, prefixed.startsWith("STEAM_"))
    }

    @Test
    fun fakeIdGenerator_isDeterministic() {
        val generator = FakeIdGenerator()
        assertEquals("test-uuid-1", generator.generateUuid())
        assertEquals("test-uuid-2", generator.generateUuid())
        assertEquals("PREFIX_3", generator.generatePrefixedId("PREFIX"))

        generator.reset()
        assertEquals("test-uuid-1", generator.generateUuid())
    }
}
