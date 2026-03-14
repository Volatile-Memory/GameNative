package app.gamenative.provider

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import app.gamenative.R
import app.gamenative.service.epic.EpicService
import app.gamenative.service.gog.GOGService
import app.gamenative.utils.ContainerUtils
import app.gamenative.data.GameSource
import com.winlator.container.ContainerManager
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException

class ContainerProvider : DocumentsProvider() {

    companion object {
        const val AUTHORITY = "app.gamenative.provider"
    }

    private val defaultDocumentProjection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_ICON
    )

    private val defaultRootProjection = arrayOf(
        DocumentsContract.Root.COLUMN_ROOT_ID,
        DocumentsContract.Root.COLUMN_MIME_TYPES,
        DocumentsContract.Root.COLUMN_FLAGS,
        DocumentsContract.Root.COLUMN_ICON,
        DocumentsContract.Root.COLUMN_TITLE,
        DocumentsContract.Root.COLUMN_SUMMARY,
        DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
    )

    private lateinit var containerManager: ContainerManager

    override fun onCreate(): Boolean {
        context?.let {
            containerManager = ContainerManager(it)
        }
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: defaultRootProjection)
        context?.let { ctx ->
            containerManager = ContainerManager(ctx) // Refresh
            val containers = containerManager.containers

            for (container in containers) {
                val gameName = ContainerUtils.resolveGameName(container.id.toString())

                result.newRow().apply {
                    add(DocumentsContract.Root.COLUMN_ROOT_ID, container.id.toString())
                    add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, getDocIdForFile(container.rootDir))
                    add(DocumentsContract.Root.COLUMN_TITLE, gameName)
                    add(DocumentsContract.Root.COLUMN_SUMMARY, "Container: ${container.id}")
                    add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE or DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD)
                    add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
                    add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
                    add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, container.rootDir.freeSpace)
                }
            }
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: defaultDocumentProjection)
        val file = getFileForDocId(documentId)
        val isRoot = isRootDirectory(file)
        includeFile(result, null, file, isRoot)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: defaultDocumentProjection)
        val parent = getFileForDocId(parentDocumentId)

        parent.listFiles()?.forEach { file ->
            includeFile(result, parentDocumentId, file, false)
        }
        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = getFileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        val isWrite = mode.indexOf('w') != -1

        if (isWrite) {
            try {
                val handler = android.os.Handler(context!!.mainLooper)
                return ParcelFileDescriptor.open(file, accessMode, handler) { e ->
                    Timber.d(e, "Error during file creation")
                }
            } catch (e: Exception) {
                return ParcelFileDescriptor.open(file, accessMode)
            }
        } else {
             return ParcelFileDescriptor.open(file, accessMode)
        }
    }

    override fun createDocument(
        documentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val parent = getFileForDocId(documentId)
        val file = File(parent, displayName)

        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            file.mkdir()
        } else {
            file.createNewFile()
        }

        return getDocIdForFile(file)
    }

    override fun deleteDocument(documentId: String) {
        val file = getFileForDocId(documentId)
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = getFileForDocId(documentId)
        val parent = file.parentFile ?: throw FileNotFoundException("Parent not found")
        val newFile = File(parent, displayName)

        if (file.renameTo(newFile)) {
            return getDocIdForFile(newFile)
        } else {
            throw FileNotFoundException("Failed to rename file")
        }
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        val file = getFileForDocId(documentId)
        if (isRootDirectory(file)) {
            val containerId = getContainerIdForRoot(file)
            if (containerId != null) {
                val iconFile = getThumbnailFileForContainer(containerId)
                if (iconFile != null && iconFile.exists()) {
                    val pfd = ParcelFileDescriptor.open(iconFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
                }
            }
        }
        return super.openDocumentThumbnail(documentId, sizeHint, signal)
    }

    private fun getFileForDocId(docId: String): File {
        return File(docId)
    }

    private fun getDocIdForFile(file: File): String {
        return file.absolutePath
    }

    private fun isRootDirectory(file: File): Boolean {
        // A root directory is a direct child of the home directory
        containerManager.containers.forEach { container ->
            if (container.rootDir.absolutePath == file.absolutePath) {
                return true
            }
        }
        return false
    }

    private fun getContainerIdForRoot(file: File): String? {
        containerManager.containers.forEach { container ->
            if (container.rootDir.absolutePath == file.absolutePath) {
                return container.id.toString()
            }
        }
        return null
    }

    private fun getThumbnailFileForContainer(containerId: String): File? {
        try {
            val gameSource = ContainerUtils.extractGameSourceFromContainerId(containerId)
            val gameId = ContainerUtils.extractGameIdFromContainerId(containerId)

            // For Steam games, icons might be cached or we can fallback to the capsule image if saved.
            // Returning null for now as we don't have a reliable way to get a raw File from Coil's cache
            // synchronously for the DocumentProvider without complex custom caching logic.
            return null
        } catch (e: Exception) {
            Timber.e(e, "Error getting thumbnail for container")
            return null
        }
    }

    private fun includeFile(result: MatrixCursor, parentDocumentId: String?, file: File, isRoot: Boolean) {
        var flags = 0
        if (file.isDirectory) {
            flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        } else if (file.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        }

        // Add support for thumbnails if this is a root folder
        if (isRoot) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL
        }

        val displayName = file.name
        val mimeType = getMimeType(file)

        result.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, getDocIdForFile(file))
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
            add(DocumentsContract.Document.COLUMN_SIZE, file.length())
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        }
    }

    private fun getMimeType(file: File): String {
        if (file.isDirectory) {
            return DocumentsContract.Document.MIME_TYPE_DIR
        }
        val extension = file.extension
        if (extension.isNotEmpty()) {
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            if (mimeType != null) {
                return mimeType
            }
        }
        return "application/octet-stream"
    }
}
