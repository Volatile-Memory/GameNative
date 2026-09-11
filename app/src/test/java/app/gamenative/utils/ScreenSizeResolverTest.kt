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

class ScreenSizeResolverTest {

    @Test
    fun getDefaultScreenSize_nullDisplayManager_returnsFallback16_9() {
        val context = mockk<Context>()
        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns null

        val resolver = ScreenSizeResolver(context)
        val result = resolver.getDefaultScreenSize()

        assertEquals(Container.DEFAULT_SCREEN_SIZE_16_9, result)
    }

    @Test
    fun getDefaultScreenSize_nullDisplay_returnsFallback16_9() {
        val context = mockk<Context>()
        val displayManager = mockk<DisplayManager>()
        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
        every { displayManager.getDisplay(Display.DEFAULT_DISPLAY) } returns null

        val resolver = ScreenSizeResolver(context)
        val result = resolver.getDefaultScreenSize()

        assertEquals(Container.DEFAULT_SCREEN_SIZE_16_9, result)
    }

    @Test
    fun getDefaultScreenSize_aspectRatio4_3_returns4_3Size() {
        val context = mockk<Context>()
        val displayManager = mockk<DisplayManager>()
        val display = mockk<Display>()
        val mode = mockk<Display.Mode>()

        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
        every { displayManager.getDisplay(Display.DEFAULT_DISPLAY) } returns display
        every { display.mode } returns mode
        // 1024 / 768 = 1.33 < 1.5
        every { mode.physicalWidth } returns 1024
        every { mode.physicalHeight } returns 768

        val resolver = ScreenSizeResolver(context)
        val result = resolver.getDefaultScreenSize()

        assertEquals(Container.DEFAULT_SCREEN_SIZE_4_3, result)
    }

    @Test
    fun getDefaultScreenSize_aspectRatio16_10_returns16_10Size() {
        val context = mockk<Context>()
        val displayManager = mockk<DisplayManager>()
        val display = mockk<Display>()
        val mode = mockk<Display.Mode>()

        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
        every { displayManager.getDisplay(Display.DEFAULT_DISPLAY) } returns display
        every { display.mode } returns mode
        // 1920 / 1200 = 1.6 in [1.5, 1.7)
        every { mode.physicalWidth } returns 1920
        every { mode.physicalHeight } returns 1200

        val resolver = ScreenSizeResolver(context)
        val result = resolver.getDefaultScreenSize()

        assertEquals(Container.DEFAULT_SCREEN_SIZE_16_10, result)
    }

    @Test
    fun getDefaultScreenSize_aspectRatio16_9_returns16_9Size() {
        val context = mockk<Context>()
        val displayManager = mockk<DisplayManager>()
        val display = mockk<Display>()
        val mode = mockk<Display.Mode>()

        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
        every { displayManager.getDisplay(Display.DEFAULT_DISPLAY) } returns display
        every { display.mode } returns mode
        // 1920 / 1080 = 1.77 >= 1.7
        every { mode.physicalWidth } returns 1920
        every { mode.physicalHeight } returns 1080

        val resolver = ScreenSizeResolver(context)
        val result = resolver.getDefaultScreenSize()

        assertEquals(Container.DEFAULT_SCREEN_SIZE_16_9, result)
    }

    @Test
    fun getDefaultScreenSize_portraitOrientation_normalizesToLandscapeAspectRatio() {
        val context = mockk<Context>()
        val displayManager = mockk<DisplayManager>()
        val display = mockk<Display>()
        val mode = mockk<Display.Mode>()

        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
        every { displayManager.getDisplay(Display.DEFAULT_DISPLAY) } returns display
        every { display.mode } returns mode
        // Portrait 1080 x 1920 -> 1920 / 1080 = 1.77 >= 1.7
        every { mode.physicalWidth } returns 1080
        every { mode.physicalHeight } returns 1920

        val resolver = ScreenSizeResolver(context)
        val result = resolver.getDefaultScreenSize()

        assertEquals(Container.DEFAULT_SCREEN_SIZE_16_9, result)
    }

    @Test
    fun getDefaultScreenSize_cachesResultOnSubsequentCalls() {
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
        val first = resolver.getDefaultScreenSize()
        val second = resolver.getDefaultScreenSize()

        assertEquals(Container.DEFAULT_SCREEN_SIZE_16_9, first)
        assertEquals(Container.DEFAULT_SCREEN_SIZE_16_9, second)
        verify(exactly = 1) { displayManager.getDisplay(Display.DEFAULT_DISPLAY) }
    }
}
