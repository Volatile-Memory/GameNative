package app.gamenative.data

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FavoritesManagerTest {

    @Test
    fun `delegates favorites state flow to repository`() {
        val mockRepo = mockk<FavoritesRepository>()
        val favoritesFlow = MutableStateFlow(setOf("123", "456"))
        val loadedFlow = MutableStateFlow(true)
        every { mockRepo.favorites } returns favoritesFlow
        every { mockRepo.loaded } returns loadedFlow

        val manager = FavoritesManager(mockRepo)

        assertEquals(setOf("123", "456"), manager.favorites.value)
        assertTrue(manager.loaded.value)
    }

    @Test
    fun `delegates toggle to repository`() {
        val mockRepo = mockk<FavoritesRepository>()
        every { mockRepo.toggle("123") } returns true

        val manager = FavoritesManager(mockRepo)
        val result = manager.toggle("123")

        assertEquals(true, result)
        verify(exactly = 1) { mockRepo.toggle("123") }
    }

    @Test
    fun `concurrent toggles and reads execute safely through delegation`() {
        val mockRepo = mockk<FavoritesRepository>()
        val favoritesFlow = MutableStateFlow(setOf("1", "2", "3"))
        val loadedFlow = MutableStateFlow(true)
        every { mockRepo.favorites } returns favoritesFlow
        every { mockRepo.loaded } returns loadedFlow
        every { mockRepo.toggle(any()) } returns true

        val manager = FavoritesManager(mockRepo)

        val threadCount = 8
        val iterations = 50
        val latch = CountDownLatch(threadCount)
        val errors = AtomicInteger(0)

        for (t in 0 until threadCount) {
            Thread {
                try {
                    for (i in 0 until iterations) {
                        manager.toggle("app_$i")
                        val current = manager.favorites.value
                        val isLoaded = manager.loaded.value
                        assertTrue(isLoaded)
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(0, errors.get())
        verify(atLeast = threadCount * iterations) { mockRepo.toggle(any()) }
    }
}
