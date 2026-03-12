package app.gamenative

import kotlin.math.abs

/**
 * Provides a curated list of standard PC resolutions and a function to find the
 * closest one to an arbitrary container size.
 *
 * Used when the Android container is resized (split-screen, foldable, windowed) to
 * offer the user a compatible resolution rather than an arbitrary one that may not
 * be supported by legacy games.
 */
object ResolutionSnapper {

    data class StandardResolution(
        val width: Int,
        val height: Int,
        val label: String,
    ) {
        val aspectLabel: String get() {
            val g = gcd(width, height)
            return "${width / g}:${height / g}"
        }

        override fun toString() = label
    }

    val STANDARD_RESOLUTIONS: List<StandardResolution> = listOf(
        StandardResolution(640,  480,  "640×480 (4:3)"),
        StandardResolution(800,  600,  "800×600 (4:3)"),
        StandardResolution(1024, 600,  "1024×600 (widescreen netbook)"),
        StandardResolution(1024, 768,  "1024×768 (4:3)"),
        StandardResolution(1152, 864,  "1152×864 (4:3)"),
        StandardResolution(1280, 720,  "1280×720 (16:9)"),
        StandardResolution(1280, 800,  "1280×800 (16:10)"),
        StandardResolution(1280, 960,  "1280×960 (4:3)"),
        StandardResolution(1280, 1024, "1280×1024 (5:4)"),
        StandardResolution(1360, 768,  "1360×768 (16:9)"),
        StandardResolution(1366, 768,  "1366×768 (16:9)"),
        StandardResolution(1440, 900,  "1440×900 (16:10)"),
        StandardResolution(1600, 900,  "1600×900 (16:9)"),
        StandardResolution(1600, 1024, "1600×1024 (14:9)"),
        StandardResolution(1680, 1050, "1680×1050 (16:10)"),
        StandardResolution(1920, 1080, "1920×1080 (16:9)"),
        StandardResolution(1920, 1200, "1920×1200 (16:10)"),
        StandardResolution(2560, 1080, "2560×1080 (21:9)"),
        StandardResolution(2560, 1440, "2560×1440 (16:9)"),
        StandardResolution(3440, 1440, "3440×1440 (21:9)"),
        StandardResolution(3840, 2160, "3840×2160 (16:9 4K)"),
    )

    /**
     * Find the standard resolution closest to [width]×[height].
     * Closeness is measured by the sum of absolute pixel differences in each axis,
     * weighted slightly to prefer resolutions that don't exceed the container
     * (i.e., prefer down-scaling over cropping).
     */
    fun findClosest(width: Int, height: Int): StandardResolution {
        return STANDARD_RESOLUTIONS.minByOrNull { res ->
            val dw = abs(res.width - width)
            val dh = abs(res.height - height)
            // Penalise slightly if the standard res is *larger* than the container
            // (would require scaling down and losing pixels)
            val overflowPenalty = if (res.width > width || res.height > height) 50 else 0
            dw + dh + overflowPenalty
        } ?: STANDARD_RESOLUTIONS.last()
    }

    /** Returns true if [width]×[height] is already an exact standard resolution. */
    fun isStandard(width: Int, height: Int): Boolean =
        STANDARD_RESOLUTIONS.any { it.width == width && it.height == height }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
