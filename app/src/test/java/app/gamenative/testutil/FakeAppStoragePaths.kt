package app.gamenative.testutil

import app.gamenative.core.storage.AppStoragePaths
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/**
 * Truly in-memory [AppStoragePaths] for testing filesystem logic without Android OS, using Okio FakeFileSystem.
 */
class FakeAppStoragePaths(
    val fakeFileSystem: FakeFileSystem = FakeFileSystem()
) : AppStoragePaths {

    override val fileSystem: FileSystem get() = fakeFileSystem

    override val filesDir: Path = "/data/user/0/app.gamenative/files".toPath()
    override val dataDir: Path = "/data/user/0/app.gamenative/app_data".toPath()
    override val cacheDir: Path = "/data/user/0/app.gamenative/cache".toPath()
    override val externalFilesDir: Path = "/storage/emulated/0/Android/data/app.gamenative/files".toPath()
    override val containersDir: Path = filesDir / "imagefs" / "home" / "xuser" / ".local" / "share" / "winlator" / "containers"
    override val imageFsDir: Path = filesDir / "imagefs"

    init {
        fakeFileSystem.createDirectories(filesDir)
        fakeFileSystem.createDirectories(dataDir)
        fakeFileSystem.createDirectories(cacheDir)
        fakeFileSystem.createDirectories(externalFilesDir)
        fakeFileSystem.createDirectories(containersDir)
        fakeFileSystem.createDirectories(imageFsDir)
    }

    override fun getContainerDir(containerId: String): Path {
        val dir = containersDir / containerId
        fakeFileSystem.createDirectories(dir)
        return dir
    }

    override fun getGameInstallBaseDir(sourceName: String): Path {
        val dir = dataDir / sourceName
        fakeFileSystem.createDirectories(dir)
        return dir
    }
}
