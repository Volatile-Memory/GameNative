package app.gamenative.utils

import app.gamenative.preferences.DownloadPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class SteamGridDBEdgeCasesTest {

    private lateinit var downloadPreferences: DownloadPreferences
    private lateinit var steamGridDB: SteamGridDB

    @Before
    fun setUp() {
        downloadPreferences = mockk<DownloadPreferences>(relaxed = true)
        steamGridDB = SteamGridDB(downloadPreferences, Dispatchers.Unconfined)
    }

    @Test
    fun searchGame_returnsNullWhenImageFetchingIsDisabled() = runBlocking {
        every { downloadPreferences.fetchSteamGridDBImages } returns false

        val result = steamGridDB.searchGame("Half-Life")
        assertNull(result)
    }

    @Test
    fun fetchGameImages_returnsEmptyWhenImageFetchingIsDisabled() = runBlocking {
        every { downloadPreferences.fetchSteamGridDBImages } returns false

        val result = steamGridDB.fetchGameImages("Half-Life", "C:/non/existent/path")
        assertNull(result.gridPath)
        assertNull(result.heroPath)
        assertNull(result.logoPath)
        assertNull(result.capsulePath)
    }

    @Test
    fun fetchGameImages_handlesInvalidDirectoryPathGracefully() = runBlocking {
        every { downloadPreferences.fetchSteamGridDBImages } returns true

        val nonExistentFolder = "C:/invalid_non_existent_folder_path_12345"
        val result = steamGridDB.fetchGameImages("Half-Life", nonExistentFolder)
        assertNull(result.gridPath)
        assertNull(result.heroPath)
        assertNull(result.logoPath)
        assertNull(result.capsulePath)
    }

    @Test
    fun searchGame_handlesBlankAndExtremeGameNamesGracefully() = runBlocking {
        every { downloadPreferences.fetchSteamGridDBImages } returns true

        // Blank name
        assertNull(steamGridDB.searchGame(""))
        assertNull(steamGridDB.searchGame("   "))

        // Extremely long name with special characters
        val extremeName = "Game Title ".repeat(200) + "!@#$%^&*()_+{}|:\"<>?[]\\;',./`~"
        val result = steamGridDB.searchGame(extremeName)
        // Since API key might not be configured or network call fails, result is null safely
        assertNull(result)
    }
}
