package app.gamenative.service.gog

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class GOGConstantsTest {
    @Before
    fun setUp() {
        // Mock context for GOGConstants
        val context = Mockito.mock(Context::class.java)
        val filesDir = File("/tmp/internal")
        filesDir.mkdirs()
        Mockito.`when`(context.filesDir).thenReturn(filesDir)
        Mockito.`when`(context.dataDir).thenReturn(filesDir)
        Mockito.`when`(context.applicationContext).thenReturn(context)

        GOGConstants.init(context)
    }

    @Test
    fun testGetGameInstallPath_pathStructure() {
        val path = GOGConstants.getGameInstallPath("Another Game 2026")
        assertEquals(path, "/tmp/internal/GOG/games/common/Another Game 2026")
    }

    @Test
    fun testSanitizationSpecialChars() {
        val path = GOGConstants.getGameInstallPath("G%ame@With^Special*Chars")
        assertEquals(path, "/tmp/internal/GOG/games/common/GameWithSpecialChars")
    }
}
