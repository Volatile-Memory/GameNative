package app.gamenative.utils

import android.content.Context
import app.gamenative.core.storage.AppStoragePaths
import app.gamenative.preferences.ContainerPreferences
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.preferences.LibraryPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CustomGameScannerTest {

    private lateinit var tmpDir: File
    private lateinit var scanner: CustomGameScanner
    private lateinit var libraryPreferences: LibraryPreferences
    private lateinit var downloadPreferences: DownloadPreferences

    @Before
    fun setUp() {
        tmpDir = kotlin.io.path.createTempDirectory("custom_game_scanner_test").toFile()
        val context = mockk<Context>(relaxed = true)
        val appStoragePaths = mockk<AppStoragePaths>(relaxed = true)
        downloadPreferences = mockk<DownloadPreferences>(relaxed = true)
        libraryPreferences = mockk<LibraryPreferences>(relaxed = true)
        val containerPreferences = mockk<ContainerPreferences>(relaxed = true)

        every { downloadPreferences.externalStoragePath } returns tmpDir.absolutePath
        every { downloadPreferences.useExternalStorage } returns true

        scanner = CustomGameScanner(
            context = context,
            appStoragePaths = appStoragePaths,
            downloadPreferences = downloadPreferences,
            libraryPreferences = libraryPreferences,
            containerPreferences = containerPreferences,
        )
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    // region findUniqueExeRelativeToFolder

    @Test
    fun `findUniqueExeRelativeToFolder returns exe when single valid exe exists`() {
        val gameFolder = File(tmpDir, "MyGame").apply { mkdir() }
        File(gameFolder, "game.exe").writeText("dummy")
        File(gameFolder, "unins000.exe").writeText("dummy uninstaller")

        val result = scanner.findUniqueExeRelativeToFolder(gameFolder)
        assertEquals("game.exe", result)
    }

    @Test
    fun `findUniqueExeRelativeToFolder returns null when multiple valid exes exist in root`() {
        val gameFolder = File(tmpDir, "MyGame").apply { mkdir() }
        File(gameFolder, "game1.exe").writeText("dummy")
        File(gameFolder, "game2.exe").writeText("dummy")

        val result = scanner.findUniqueExeRelativeToFolder(gameFolder)
        assertNull(result)
    }

    @Test
    fun `findUniqueExeRelativeToFolder returns relative path for single exe in immediate subdirectory`() {
        val gameFolder = File(tmpDir, "MyGame").apply { mkdir() }
        val binDir = File(gameFolder, "bin").apply { mkdir() }
        File(binDir, "game.exe").writeText("dummy")

        val result = scanner.findUniqueExeRelativeToFolder(gameFolder)
        assertEquals("bin/game.exe", result)
    }

    @Test
    fun `findUniqueExeRelativeToFolder ignores uninstaller variants case insensitively`() {
        val gameFolder = File(tmpDir, "MyGame").apply { mkdir() }
        File(gameFolder, "UNINS000.EXE").writeText("dummy")
        File(gameFolder, "uninstall.exe").writeText("dummy")
        File(gameFolder, "main.exe").writeText("dummy")

        val result = scanner.findUniqueExeRelativeToFolder(gameFolder)
        assertEquals("main.exe", result)
    }

    @Test
    fun `findUniqueExeRelativeToFolder returns null for empty folder`() {
        val gameFolder = File(tmpDir, "EmptyGame").apply { mkdir() }
        val result = scanner.findUniqueExeRelativeToFolder(gameFolder)
        assertNull(result)
    }

    @Test
    fun `findUniqueExeRelativeToFolder returns null for non-existent folder`() {
        val missing = File(tmpDir, "non_existent")
        val result = scanner.findUniqueExeRelativeToFolder(missing)
        assertNull(result)
    }

    @Test
    fun `findUniqueExeRelativeToFolder ignores non-exe files`() {
        val gameFolder = File(tmpDir, "MyGame").apply { mkdir() }
        File(gameFolder, "readme.txt").writeText("dummy")
        File(gameFolder, "engine.dll").writeText("dummy")
        File(gameFolder, "data.pak").writeText("dummy")
        File(gameFolder, "game.exe").writeText("dummy")

        val result = scanner.findUniqueExeRelativeToFolder(gameFolder)
        assertEquals("game.exe", result)
    }

    // endregion

    // region findAllValidExeFiles

    @Test
    fun `findAllValidExeFiles lists non-uninstaller executables`() {
        val gameFolder = File(tmpDir, "MyGame").apply { mkdir() }
        File(gameFolder, "game.exe").writeText("dummy")
        File(gameFolder, "launcher.exe").writeText("dummy")
        File(gameFolder, "unins000.exe").writeText("dummy")

        val results = scanner.findAllValidExeFiles(gameFolder)
        assertEquals(2, results.size)
        assertTrue(results.contains("game.exe"))
        assertTrue(results.contains("launcher.exe"))
    }

    @Test
    fun `findAllValidExeFiles includes immediate subdirectories`() {
        val gameFolder = File(tmpDir, "MyGame").apply { mkdir() }
        val binDir = File(gameFolder, "bin").apply { mkdir() }
        File(gameFolder, "root.exe").writeText("dummy")
        File(binDir, "sub.exe").writeText("dummy")

        val results = scanner.findAllValidExeFiles(gameFolder)
        assertEquals(2, results.size)
        assertTrue(results.contains("root.exe"))
        assertTrue(results.contains("bin/sub.exe"))
    }

    @Test
    fun `findAllValidExeFiles returns empty list for missing folder`() {
        val missing = File(tmpDir, "missing")
        val results = scanner.findAllValidExeFiles(missing)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findAllValidExeFiles returns empty list for folder without executables`() {
        val gameFolder = File(tmpDir, "MyGame").apply { mkdir() }
        File(gameFolder, "data.json").writeText("{}")
        val results = scanner.findAllValidExeFiles(gameFolder)
        assertTrue(results.isEmpty())
    }

    // endregion

    // region Cover Art Discovery

    @Test
    fun `findCapsuleCoverInFolder prefers coverv over cover`() {
        val gameFolder = File(tmpDir, "MyGame").apply { mkdir() }
        val coverv = File(gameFolder, "coverv.png").apply { writeText("img") }
        File(gameFolder, "cover.png").apply { writeText("img") }

        val result = scanner.findCapsuleCoverInFolder(gameFolder)
        assertNotNull(result)
        assertTrue("Expected coverv but got: $result", result!!.contains("coverv.png"))
    }

    @Test
    fun `findCapsuleCoverInFolder falls back to cover when coverv is absent`() {
        val gameFolder = File(tmpDir, "MyGame").apply { mkdir() }
        File(gameFolder, "cover.jpg").apply { writeText("img") }

        val result = scanner.findCapsuleCoverInFolder(gameFolder)
        assertNotNull(result)
        assertTrue("Expected cover.jpg but got: $result", result!!.contains("cover.jpg"))
    }

    @Test
    fun `findHeroCoverInFolder prefers coverh over cover`() {
        val gameFolder = File(tmpDir, "MyGame").apply { mkdir() }
        File(gameFolder, "coverh.webp").apply { writeText("img") }
        File(gameFolder, "cover.png").apply { writeText("img") }

        val result = scanner.findHeroCoverInFolder(gameFolder)
        assertNotNull(result)
        assertTrue("Expected coverh.webp but got: $result", result!!.contains("coverh.webp"))
    }

    @Test
    fun `findCapsuleCoverInFolder returns null when no cover files exist`() {
        val gameFolder = File(tmpDir, "MyGame").apply { mkdir() }
        File(gameFolder, "screenshot.png").apply { writeText("img") }

        val result = scanner.findCapsuleCoverInFolder(gameFolder)
        assertNull(result)
    }

    @Test
    fun `findCapsuleCoverInFolder returns null for non-existent directory`() {
        val missing = File(tmpDir, "does_not_exist")
        val result = scanner.findCapsuleCoverInFolder(missing)
        assertNull(result)
    }

    // endregion

    // region App ID and Path Resolution

    @Test
    fun `getFolderPathFromAppId returns null for non-custom-game appId`() {
        assertNull(scanner.getFolderPathFromAppId("STEAM_12345"))
        assertNull(scanner.getFolderPathFromAppId("EPIC_67890"))
        assertNull(scanner.getFolderPathFromAppId("INVALID"))
    }

    @Test
    fun `getFolderPathFromAppId returns null for non-integer custom-game id`() {
        assertNull(scanner.getFolderPathFromAppId("CUSTOM_GAME_notanumber"))
        assertNull(scanner.getFolderPathFromAppId("CUSTOM_GAME_"))
    }

    @Test
    fun `isGameInstalled returns false when id is not known`() {
        assertFalse(scanner.isGameInstalled(9999999))
    }

    // endregion

    // region Concurrency & Cache Stress Testing

    @Test
    fun `concurrent cache invalidation and queries execute without deadlock or exception`() {
        val threadCount = 8
        val iterationsPerThread = 50
        val latch = CountDownLatch(threadCount)
        val errors = AtomicInteger(0)

        for (t in 0 until threadCount) {
            Thread {
                try {
                    for (i in 0 until iterationsPerThread) {
                        if (i % 5 == 0) {
                            scanner.invalidateCache()
                        }
                        scanner.findCustomGameById(i * 100)
                        scanner.getFolderPathFromAppId("CUSTOM_GAME_${i * 100}")
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
