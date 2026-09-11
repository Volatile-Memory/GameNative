package app.gamenative.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import com.winlator.container.Container
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the default screen size and aspect ratio for containers based on device display metrics.
 */
@Singleton
class ScreenSizeResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cachedDefaultScreenSize: String? = null

    fun getDefaultScreenSize(): String {
        cachedDefaultScreenSize?.let { return it }

        return try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null) {
                val width: Int
                val height: Int

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val mode = display.mode
                    width = mode.physicalWidth
                    height = mode.physicalHeight
                } else {
                    val displayMetrics = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    display.getRealMetrics(displayMetrics)
                    width = displayMetrics.widthPixels
                    height = displayMetrics.heightPixels
                }

                // Calculate aspect ratio (always use landscape orientation for calculation)
                val aspectRatio = maxOf(width, height).toFloat() / minOf(width, height).toFloat()

                // Aspect ratio thresholds:
                // 4:3 = 1.33
                // 16:10 = 1.6
                // 16:9 = 1.77
                val result = when {
                    aspectRatio < 1.5f -> Container.DEFAULT_SCREEN_SIZE_4_3
                    aspectRatio < 1.7f -> Container.DEFAULT_SCREEN_SIZE_16_10
                    else -> Container.DEFAULT_SCREEN_SIZE_16_9
                }
                cachedDefaultScreenSize = result
                result
            } else {
                val fallback = Container.DEFAULT_SCREEN_SIZE_16_9
                cachedDefaultScreenSize = fallback
                fallback
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get device screen size")
            val fallback = Container.DEFAULT_SCREEN_SIZE_16_9
            cachedDefaultScreenSize = fallback
            fallback
        }
    }
}
