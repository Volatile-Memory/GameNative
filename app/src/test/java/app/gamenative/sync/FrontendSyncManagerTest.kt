package app.gamenative.sync

import app.gamenative.core.appinfo.StringResolver
import app.gamenative.data.GameSource
import app.gamenative.db.dao.AmazonGameDao
import app.gamenative.db.dao.EpicGameDao
import app.gamenative.db.dao.GOGGameDao
import app.gamenative.db.dao.SteamAppDao
import app.gamenative.preferences.DownloadPreferences
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FrontendSyncManagerTest {

    private lateinit var tmpDir: File
    private lateinit var frontendSyncManager: FrontendSyncManager
    private lateinit var downloadPreferences: DownloadPreferences

    @Before
    fun setUp() {
        tmpDir = kotlin.io.path.createTempDirectory("frontend_sync_test").toFile()
        downloadPreferences = mockk<DownloadPreferences>(relaxed = true)
        val stringResolver = mockk<StringResolver>(relaxed = true)
        val steamAppDao = mockk<SteamAppDao>(relaxed = true)
        val epicGameDao = mockk<EpicGameDao>(relaxed = true)
        val gogGameDao = mockk<GOGGameDao>(relaxed = true)
        val amazonGameDao = mockk<AmazonGameDao>(relaxed = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        frontendSyncManager = FrontendSyncManager(
            scope = scope,
            downloadPreferences = downloadPreferences,
            stringResolver = stringResolver,
            steamAppDao = steamAppDao,
            epicGameDao = epicGameDao,
            gogGameDao = gogGameDao,
            amazonGameDao = amazonGameDao,
        )
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    // region extensionFor

    @Test
    fun `extensionFor returns correct extension for each source`() {
        assertEquals(".steam", frontendSyncManager.extensionFor(GameSource.STEAM))
        assertEquals(".epic", frontendSyncManager.extensionFor(GameSource.EPIC))
        assertEquals(".gog", frontendSyncManager.extensionFor(GameSource.GOG))
        assertEquals(".amazon", frontendSyncManager.extensionFor(GameSource.AMAZON))
        assertEquals(".pcgame", frontendSyncManager.extensionFor(GameSource.CUSTOM_GAME))
    }

    @Test
    fun `extensionFor covers all GameSource entries`() {
        GameSource.entries.forEach { source ->
            val ext = frontendSyncManager.extensionFor(source)
            assertTrue("Extension for $source should start with a dot", ext.startsWith("."))
            assertTrue("Extension for $source should be non-empty after dot", ext.length > 1)
        }
    }

    // endregion

    // region deleteAllFilesWithExtension

    @Test
    fun `deleteAllFilesWithExtension removes files with matching extension`() {
        File(tmpDir, "Half-Life 2.steam").writeText("220")
        File(tmpDir, "Portal.steam").writeText("400")
        File(tmpDir, "Celeste.steam").writeText("504230")

        frontendSyncManager.deleteAllFilesWithExtension(tmpDir.path, ".steam")

        assertFalse(File(tmpDir, "Half-Life 2.steam").exists())
        assertFalse(File(tmpDir, "Portal.steam").exists())
        assertFalse(File(tmpDir, "Celeste.steam").exists())
    }

    @Test
    fun `deleteAllFilesWithExtension leaves files with other extensions untouched`() {
        File(tmpDir, "Hades.epic").writeText("12345")
        File(tmpDir, "Fortnite.epic").writeText("67890")
        File(tmpDir, "Half-Life 2.steam").writeText("220")
        File(tmpDir, "readme.txt").writeText("hello")

        frontendSyncManager.deleteAllFilesWithExtension(tmpDir.path, ".steam")

        assertTrue(File(tmpDir, "Hades.epic").exists())
        assertTrue(File(tmpDir, "Fortnite.epic").exists())
        assertTrue(File(tmpDir, "readme.txt").exists())
        assertFalse(File(tmpDir, "Half-Life 2.steam").exists())
    }

    @Test
    fun `deleteAllFilesWithExtension does not recurse into subdirectories`() {
        val subDir = File(tmpDir, "subdir").also { it.mkdir() }
        File(subDir, "nested.steam").writeText("999")
        File(tmpDir, "top.steam").writeText("100")

        frontendSyncManager.deleteAllFilesWithExtension(tmpDir.path, ".steam")

        assertFalse(File(tmpDir, "top.steam").exists())
        assertTrue("Nested file should not be deleted", File(subDir, "nested.steam").exists())
    }

    @Test
    fun `deleteAllFilesWithExtension on empty directory completes without error`() {
        frontendSyncManager.deleteAllFilesWithExtension(tmpDir.path, ".steam")
        assertEquals(0, tmpDir.listFiles()?.size ?: 0)
    }

    @Test
    fun `deleteAllFilesWithExtension on non-existent directory completes without error`() {
        val missing = File(tmpDir, "does_not_exist")
        frontendSyncManager.deleteAllFilesWithExtension(missing.path, ".steam")
    }

    @Test
    fun `deleteAllFilesWithExtension on non-directory path completes without error`() {
        val file = File(tmpDir, "notadir.txt").also { it.writeText("x") }
        frontendSyncManager.deleteAllFilesWithExtension(file.path, ".steam")
        assertTrue("File passed as dir should be untouched", file.exists())
    }

    @Test
    fun `deleteAllFilesWithExtension file content matches expected export format`() {
        val appId = 220
        val gameFile = File(tmpDir, "Half-Life 2.steam")
        gameFile.writeText(appId.toString(), Charsets.UTF_8)

        assertEquals("220", gameFile.readText(Charsets.UTF_8))

        frontendSyncManager.deleteAllFilesWithExtension(tmpDir.path, ".steam")
        assertFalse(gameFile.exists())
    }

    // endregion

    // region Directory Changes & anyConfigured state

    @Test
    fun `changeDirectory updates anyConfigured state flow synchronously`() {
        assertFalse(frontendSyncManager.anyConfigured.value)

        frontendSyncManager.changeDirectory(GameSource.STEAM, tmpDir.path, deleteOldFiles = false)
        assertTrue(frontendSyncManager.anyConfigured.value)

        frontendSyncManager.changeDirectory(GameSource.STEAM, "", deleteOldFiles = false)
        assertFalse(frontendSyncManager.anyConfigured.value)
    }

    @Test
    fun `changeDirectory with deleteOldFiles cleans up previous path`() {
        val oldDir = File(tmpDir, "old_steam").apply { mkdir() }
        val newDir = File(tmpDir, "new_steam").apply { mkdir() }
        val oldFile = File(oldDir, "Game.steam").apply { writeText("100") }

        frontendSyncManager.changeDirectory(GameSource.STEAM, oldDir.path, deleteOldFiles = false)
        assertTrue(oldFile.exists())

        frontendSyncManager.changeDirectory(GameSource.STEAM, newDir.path, deleteOldFiles = true)
        assertFalse(oldFile.exists())
        verify { downloadPreferences.setFrontendSyncDir(GameSource.STEAM, newDir.path) }
    }

    // endregion

    // region Concurrency & Thread Safety

    @Test
    fun `concurrent directory updates execute safely without race conditions`() {
        val threadCount = 6
        val iterations = 30
        val latch = CountDownLatch(threadCount)
        val errors = AtomicInteger(0)

        val sources = GameSource.entries

        for (t in 0 until threadCount) {
            Thread {
                try {
                    for (i in 0 until iterations) {
                        val source = sources[(t + i) % sources.size]
                        val path = if (i % 2 == 0) tmpDir.path else ""
                        frontendSyncManager.changeDirectory(source, path, deleteOldFiles = false)
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
    }

    // endregion
}
