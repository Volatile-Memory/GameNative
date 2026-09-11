package app.gamenative.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.winlator.container.Container
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ScreenSizeResolverStressTest {

    @Test
    fun zeroDimensions_returnsFallback16_9() {
        val context = mockk<Context>()
        val displayManager = mockk<DisplayManager>()
        val display = mockk<Display>()
        val mode = mockk<Display.Mode>()

        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
        every { displayManager.getDisplay(Display.DEFAULT_DISPLAY) } returns display
        every { display.mode } returns mode
        every { mode.physicalWidth } returns 0
        every { mode.physicalHeight } returns 0

        val resolver = ScreenSizeResolver(context)
        val result = resolver.getDefaultScreenSize()

        // 0 / 0 is NaN -> when clauses false -> falls back to 16:9
        assertEquals(Container.DEFAULT_SCREEN_SIZE_16_9, result)
    }

    @Test
    fun zeroWidth_nonZeroHeight_returnsFallback16_9() {
        val context = mockk<Context>()
        val displayManager = mockk<DisplayManager>()
        val display = mockk<Display>()
        val mode = mockk<Display.Mode>()

        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
        every { displayManager.getDisplay(Display.DEFAULT_DISPLAY) } returns display
        every { display.mode } returns mode
        every { mode.physicalWidth } returns 0
        every { mode.physicalHeight } returns 1080

        val resolver = ScreenSizeResolver(context)
        val result = resolver.getDefaultScreenSize()

        // 1080 / 0 is Infinity -> when clauses false -> falls back to 16:9
        assertEquals(Container.DEFAULT_SCREEN_SIZE_16_9, result)
    }

    @Test
    fun portraitVsLandscape_equivalenceAcrossStandardRatios() {
        val pairs = listOf(
            Pair(720, 1280), // 16:9
            Pair(1080, 1920), // 16:9
            Pair(1200, 1920), // 16:10
            Pair(768, 1024), // 4:3
            Pair(1440, 2560), // 16:9
            Pair(1600, 2560), // 16:10
        )

        for ((w, h) in pairs) {
            val context1 = mockk<Context>()
            val dm1 = mockk<DisplayManager>()
            val d1 = mockk<Display>()
            val m1 = mockk<Display.Mode>()
            every { context1.getSystemService(Context.DISPLAY_SERVICE) } returns dm1
            every { dm1.getDisplay(Display.DEFAULT_DISPLAY) } returns d1
            every { d1.mode } returns m1
            every { m1.physicalWidth } returns w
            every { m1.physicalHeight } returns h

            val context2 = mockk<Context>()
            val dm2 = mockk<DisplayManager>()
            val d2 = mockk<Display>()
            val m2 = mockk<Display.Mode>()
            every { context2.getSystemService(Context.DISPLAY_SERVICE) } returns dm2
            every { dm2.getDisplay(Display.DEFAULT_DISPLAY) } returns d2
            every { d2.mode } returns m2
            every { m2.physicalWidth } returns h
            every { m2.physicalHeight } returns w

            val resolverPortrait = ScreenSizeResolver(context1)
            val resolverLandscape = ScreenSizeResolver(context2)

            assertEquals(
                "Mismatch between ($w x $h) and ($h x $w)",
                resolverLandscape.getDefaultScreenSize(),
                resolverPortrait.getDefaultScreenSize(),
            )
        }
    }

    @Test
    fun unusualRatios_mappedCorrectly() {
        val testCases = listOf(
            // 19.5:9 (e.g. 2340x1080 = 2.166f) -> >= 1.7 -> 16:9
            Triple(2340, 1080, Container.DEFAULT_SCREEN_SIZE_16_9),
            // 20:9 (2400x1080 = 2.222f) -> >= 1.7 -> 16:9
            Triple(2400, 1080, Container.DEFAULT_SCREEN_SIZE_16_9),
            // 21:9 (2560x1080 = 2.370f) -> >= 1.7 -> 16:9
            Triple(2560, 1080, Container.DEFAULT_SCREEN_SIZE_16_9),
            // 32:9 super ultra-wide (5120x1440 = 3.555f) -> >= 1.7 -> 16:9
            Triple(5120, 1440, Container.DEFAULT_SCREEN_SIZE_16_9),
            // 1:1 square (1080x1080 = 1.0f) -> < 1.5 -> 4:3
            Triple(1080, 1080, Container.DEFAULT_SCREEN_SIZE_4_3),
            // 3:2 boundary (2160x1440 = 1.5f) -> [1.5, 1.7) -> 16:10
            Triple(2160, 1440, Container.DEFAULT_SCREEN_SIZE_16_10),
            // 5:4 ratio (1280x1024 = 1.25f) -> < 1.5 -> 4:3
            Triple(1280, 1024, Container.DEFAULT_SCREEN_SIZE_4_3),
        )

        for ((w, h, expected) in testCases) {
            val context = mockk<Context>()
            val dm = mockk<DisplayManager>()
            val d = mockk<Display>()
            val m = mockk<Display.Mode>()
            every { context.getSystemService(Context.DISPLAY_SERVICE) } returns dm
            every { dm.getDisplay(Display.DEFAULT_DISPLAY) } returns d
            every { d.mode } returns m
            every { m.physicalWidth } returns w
            every { m.physicalHeight } returns h

            val resolver = ScreenSizeResolver(context)
            val actual = resolver.getDefaultScreenSize()
            assertEquals("Failed for ratio $w x $h", expected, actual)
        }
    }

    @Test
    fun concurrentAccess_returnsConsistentResultAndCaches() {
        val context = mockk<Context>()
        val displayManager = mockk<DisplayManager>()
        val display = mockk<Display>()
        val mode = mockk<Display.Mode>()

        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
        every { displayManager.getDisplay(Display.DEFAULT_DISPLAY) } returns display
        every { display.mode } returns mode
        every { mode.physicalWidth } returns 1920
        every { mode.physicalHeight } returns 1080

        val resolver = ScreenSizeResolver(context)
        val numThreads = 64
        val latch = CountDownLatch(numThreads)
        val executor = Executors.newFixedThreadPool(numThreads)
        val mismatchCount = AtomicInteger(0)

        for (i in 0 until numThreads) {
            executor.submit {
                try {
                    val size = resolver.getDefaultScreenSize()
                    if (size != Container.DEFAULT_SCREEN_SIZE_16_9) {
                        mismatchCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        assertEquals(0, mismatchCount.get())
        // DisplayManager should be queried once due to volatile caching
        verify(atLeast = 1, atMost = numThreads) { displayManager.getDisplay(Display.DEFAULT_DISPLAY) }
    }
}
