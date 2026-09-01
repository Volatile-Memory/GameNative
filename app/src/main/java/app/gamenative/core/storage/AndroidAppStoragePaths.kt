package app.gamenative.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAppStoragePaths @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppStoragePaths {

    override val fileSystem: FileSystem = FileSystem.SYSTEM

    override val filesDir: Path
        get() = context.filesDir.toOkioPath()

    override val dataDir: Path
        get() = context.dataDir.toOkioPath()

    override val cacheDir: Path
        get() = context.cacheDir.toOkioPath()

    override val externalFilesDir: Path?
        get() = context.getExternalFilesDir(null)?.toOkioPath()

    override val imageFsDir: Path
        get() = filesDir / "imagefs"

    override val containersDir: Path
        get() = filesDir / "imagefs" / "home" / "xuser" / ".local" / "share" / "winlator" / "containers"

    override fun getContainerDir(containerId: String): Path {
        return containersDir / containerId
    }

    override fun getGameInstallBaseDir(sourceName: String): Path {
        return dataDir / sourceName
    }
}
