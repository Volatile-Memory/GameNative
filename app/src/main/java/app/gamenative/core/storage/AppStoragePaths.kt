package app.gamenative.core.storage

import okio.FileSystem
import okio.Path

/**
 * Pure abstraction over application storage roots and directories.
 * Decouples business and runtime logic from Android Context filesystem methods,
 * enabling fast, isolated unit tests using local temporary directories.
 */
interface AppStoragePaths {
    /** The FileSystem implementation to use for this storage path layout. */
    val fileSystem: FileSystem
    
    /** Application internal files directory (context.filesDir). */
    val filesDir: Path

    /** Application private data directory (context.dataDir). */
    val dataDir: Path

    /** Application cache directory (context.cacheDir). */
    val cacheDir: Path

    /** Primary external files directory if mounted (context.getExternalFilesDir(null)). */
    val externalFilesDir: Path?

    /** Root directory where container configurations and prefixes are located. */
    val containersDir: Path

    /** Root directory for ImageFs / rootfs files. */
    val imageFsDir: Path

    /** Directory for a specific container identifier. */
    fun getContainerDir(containerId: String): Path

    /** Base directory for a specific store's game installations (e.g., "Steam", "GOG", "Epic", "Amazon"). */
    fun getGameInstallBaseDir(sourceName: String): Path
}
