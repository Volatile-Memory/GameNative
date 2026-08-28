package app.gamenative.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultFavoritesRepositoryTest {

    @Test
    fun loadsInitialFavoritesFromPreferences() = runBlocking {
        val initialData = setOf("10", "20")
        var savedData: Set<String>? = null

        val repository = DefaultFavoritesRepository(
            scope = this,
            loadPreferences = { initialData },
            savePreferences = { savedData = it },
        )

        val loaded = repository.loaded.first { it }
        assertTrue(loaded)
        assertEquals(initialData, repository.favorites.value)
    }

    @Test
    fun toggleAddsAppIdWhenNotPresent() = runBlocking {
        val initialData = setOf("10")
        var savedData: Set<String>? = null

        val repository = DefaultFavoritesRepository(
            scope = this,
            loadPreferences = { initialData },
            savePreferences = { savedData = it },
        )

        repository.loaded.first { it }

        val toggleResult = repository.toggle("20")

        assertEquals(true, toggleResult)
        assertEquals(setOf("10", "20"), repository.favorites.value)
        assertEquals(setOf("10", "20"), savedData)
    }

    @Test
    fun toggleRemovesAppIdWhenAlreadyPresent() = runBlocking {
        val initialData = setOf("10", "20")
        var savedData: Set<String>? = null

        val repository = DefaultFavoritesRepository(
            scope = this,
            loadPreferences = { initialData },
            savePreferences = { savedData = it },
        )

        repository.loaded.first { it }

        val toggleResult = repository.toggle("10")

        assertEquals(false, toggleResult)
        assertEquals(setOf("20"), repository.favorites.value)
        assertEquals(setOf("20"), savedData)
    }

    @Test
    fun toggleReturnsNullWhenNotLoaded() {
        val latch = CountDownLatch(1)
        val repository = DefaultFavoritesRepository(
            scope = CoroutineScope(Dispatchers.IO),
            loadPreferences = {
                latch.await(2, TimeUnit.SECONDS)
                setOf("10")
            },
            savePreferences = {},
        )

        try {
            assertFalse(repository.loaded.value)
            val result = repository.toggle("20")
            assertNull(result)
        } finally {
            latch.countDown()
        }
    }

    @Test
    fun handlesExceptionInLoadPreferencesGracefully() = runBlocking {
        val repository = DefaultFavoritesRepository(
            scope = this,
            loadPreferences = { throw RuntimeException("Disk error") },
            savePreferences = {},
        )

        val loaded = repository.loaded.first { it }
        assertTrue(loaded)
        assertEquals(emptySet<String>(), repository.favorites.value)
    }
}
