package app.gamenative.core.storage

import app.gamenative.testutil.FakeAppStoragePaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStoragePathsTest {

    @Test
    fun fakeAppStoragePaths_resolvesDirectoriesCorrectly() {
        val paths = FakeAppStoragePaths()
        val fs = paths.fileSystem

        assertTrue(fs.exists(paths.filesDir))
        assertTrue(fs.exists(paths.dataDir))
        assertTrue(fs.exists(paths.cacheDir))
        assertTrue(fs.exists(paths.containersDir))
        assertTrue(fs.exists(paths.imageFsDir))

        val containerDir = paths.getContainerDir("1")
        assertEquals(paths.containersDir / "1", containerDir)
        assertTrue(fs.exists(containerDir))

        val steamBaseDir = paths.getGameInstallBaseDir("Steam")
        assertEquals(paths.dataDir / "Steam", steamBaseDir)
        assertTrue(fs.exists(steamBaseDir))
    }
}
