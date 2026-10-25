package com.shfileexplorer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

data class SafLocation(
    val uri: String,
    val label: String,
)

data class SafImport(
    val location: SafLocation,
    val persisted: Boolean,
)

/**
 * Browses and edits Storage Access Framework document trees.
 *
 * SAF providers expose a tree URI plus per-document URIs. The tree URI is kept
 * in the document URI path, so a document path can be navigated without a
 * separate in-memory tree handle. Parent links are cached while browsing
 * because the DocumentsContract API does not expose a parent lookup.
 */
class SafRepository(context: Context) {

    private val resolver = context.applicationContext.contentResolver
    private val files = FileRepository()
    private val nameValidator = FileRepository()
    private val parentByPath = mutableMapOf<String, String?>()
    private val labelByPath = mutableMapOf<String, String>()

    fun isSafPath(path: String?): Boolean =
        path?.startsWith("content://", ignoreCase = true) == true

    fun rememberLocation(location: SafLocation) {
        parentByPath.putIfAbsent(location.uri, null)
        labelByPath[location.uri] = location.label
    }

    suspend fun importTree(treeUri: Uri): SafImport = withContext(Dispatchers.IO) {
        val persisted = persistAccess(treeUri)
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val path = rootUri.toString()
        val label = displayName(rootUri)?.takeIf { it.isNotBlank() } ?: "Cloud folder"
        parentByPath[path] = null
        labelByPath[path] = label
        SafImport(SafLocation(path, label), persisted)
    }

    fun parentOf(path: String): String? {
        ensureKnown(path)
        return parentByPath[path]
    }

    fun isUnderTree(path: String?, rootPath: String): Boolean {
        if (path == null) return false
        val pathTree = treeUriFor(Uri.parse(path)) ?: return false
        val rootTree = treeUriFor(Uri.parse(rootPath)) ?: return false
        return pathTree == rootTree
    }

    fun breadcrumbs(path: String): List<Pair<String, String>> {
        ensureKnown(path)
        val result = ArrayDeque<Pair<String, String>>()
        val seen = mutableSetOf<String>()
        var current: String? = path
        while (current != null && seen.add(current)) {
            val label = labelByPath[current]
                ?: if (parentByPath[current] == null) "Cloud folder" else "Folder"
            result.addFirst(current to label)
            current = parentByPath[current]
        }
        return result.toList()
    }

    suspend fun listDirectory(uri: Uri, showHidden: Boolean): List<FileEntry> =
        withContext(Dispatchers.IO) {
            ensureKnown(uri.toString())
            val treeUri = treeUriFor(uri)
                ?: throw IOException("This cloud folder is not available.")
            val documentId = DocumentsContract.getDocumentId(uri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val result = ArrayList<FileEntry>()
            resolver.query(childrenUri, CHILD_PROJECTION, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                if (idIndex < 0 || nameIndex < 0 || mimeIndex < 0) {
                    throw IOException("This cloud folder returned an unsupported listing.")
                }
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex) ?: continue
                    val name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: continue
                    if (!showHidden && name.startsWith('.')) continue
                    val mime = cursor.getString(mimeIndex).orEmpty()
                    val directory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        cursor.getLong(sizeIndex)
                    } else {
                        0L
                    }
                    val modified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) {
                        cursor.getLong(modifiedIndex)
                    } else {
                        0L
                    }
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    val path = childUri.toString()
                    parentByPath[path] = uri.toString()
                    labelByPath[path] = name
                    result.add(entryFor(childUri, name, mime, size, modified, directory))
                }
            } ?: throw IOException("This cloud folder cannot be read.")
            result
        }

    suspend fun search(
        root: Uri,
        query: String,
        showHidden: Boolean,
        subfolders: Boolean,
        limit: Int = 500,
        onMatch: (FileEntry) -> Unit = {},
    ): List<FileEntry> = withContext(Dispatchers.IO) {
        val needle = query.trim()
        if (needle.isEmpty()) return@withContext emptyList()
        val results = ArrayList<FileEntry>()
        val pending = ArrayDeque<Uri>()
        val seen = mutableSetOf<String>()
        pending.add(root)
        while (pending.isNotEmpty() && results.size < limit) {
            currentCoroutineContext().ensureActive()
            val directory = pending.removeFirst()
            if (!seen.add(directory.toString())) continue
            val children = runCatching { listDirectory(directory, showHidden) }.getOrNull() ?: continue
            for (entry in children) {
                currentCoroutineContext().ensureActive()
                if (entry.name.contains(needle, ignoreCase = true)) {
                    results.add(entry)
                    onMatch(entry)
                    if (results.size >= limit) break
                }
                if (subfolders && entry.isDirectory) {
                    pending.add(Uri.parse(entry.path))
                }
            }
        }
        results
    }

    suspend fun createFolder(parent: Uri, rawName: String): FileEntry =
        withContext(Dispatchers.IO) {
            val name = nameValidator.validateName(rawName)
            ensureNameAvailable(parent, name)
            val created = createDocument(parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            entryFor(
                created,
                name,
                DocumentsContract.Document.MIME_TYPE_DIR,
                size = 0L,
                modified = System.currentTimeMillis(),
                directory = true,
            )
        }

    suspend fun createFile(parent: Uri, rawName: String): FileEntry =
        withContext(Dispatchers.IO) {
            val name = nameValidator.validateName(rawName)
            ensureNameAvailable(parent, name)
            val mime = FileEntry.mimeTypeFor(name)
                .takeUnless { it == "application/octet-stream" }
                ?: "text/plain"
            val created = createDocument(parent, mime, name)
            entryFor(
                created,
                name,
                mime,
                size = 0L,
                modified = System.currentTimeMillis(),
                directory = false,
            )
        }

    suspend fun rename(uri: Uri, rawName: String): String = withContext(Dispatchers.IO) {
        val name = nameValidator.validateName(rawName)
        val oldPath = uri.toString()
        val oldName = labelByPath[oldPath] ?: documentMeta(uri)?.name.orEmpty()
        if (name == oldName) return@withContext oldPath
        val parentPath = parentByPath[oldPath]
            ?: throw IOException("Open the parent cloud folder before renaming this item.")
        val parentUri = Uri.parse(parentPath)
        val existing = childNames(parentUri) - oldName
        if (name in existing) {
            throw IOException("A file or folder with that name already exists.")
        }
        val renamed = DocumentsContract.renameDocument(resolver, uri, name)
            ?: throw IOException("Could not rename this item.")
        parentByPath.remove(oldPath)
        labelByPath.remove(oldPath)
        val newPath = renamed.toString()
        parentByPath[newPath] = parentPath
        labelByPath[newPath] = name
        newPath
    }

    suspend fun delete(
        paths: Collection<String>,
        onProgress: (String) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (path in paths) {
            currentCoroutineContext().ensureActive()
            val uri = Uri.parse(path)
            val label = labelByPath[path] ?: documentMeta(uri)?.name ?: "item"
            onProgress("Deleting $label")
            deleteTree(uri)
            deleted++
        }
        deleted
    }

    /**
     * Copies or moves any mix of local files and SAF documents into either a
     * local directory or a SAF folder. SAF-to-SAF transfers stream through the
     * app so the operation also works across different cloud providers.
     */
    suspend fun transfer(
        sources: List<String>,
        destination: String,
        cut: Boolean,
        onProgress: (String) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        var transferred = 0
        for (source in sources) {
            currentCoroutineContext().ensureActive()
            val sourceIsSaf = isSafPath(source)
            val destinationIsSaf = isSafPath(destination)
            if (cut && sourceIsSaf && destinationIsSaf && parentByPath[source] == destination) {
                continue
            }
            if (sourceIsSaf) {
                val sourceUri = Uri.parse(source)
                if (destinationIsSaf) {
                    if (isDescendantOrSelf(source, destination)) {
                        throw IOException("Cannot copy a folder into itself.")
                    }
                    copyDocumentToDocument(sourceUri, Uri.parse(destination), onProgress)
                } else {
                    copyDocumentToLocal(sourceUri, File(destination), onProgress)
                }
                if (cut) deleteTree(sourceUri)
            } else {
                val sourceFile = File(source)
                if (!sourceFile.exists()) {
                    throw IOException("${sourceFile.name} no longer exists.")
                }
                if (destinationIsSaf) {
                    copyLocalToDocument(sourceFile, Uri.parse(destination), onProgress)
                    if (cut) files.delete(listOf(sourceFile), onProgress)
                } else if (cut) {
                    files.move(listOf(sourceFile), File(destination), onProgress)
                } else {
                    files.copy(listOf(sourceFile), File(destination), onProgress)
                }
            }
            transferred++
        }
        transferred
    }

    suspend fun releaseTree(path: String) = withContext(Dispatchers.IO) {
        val treeUri = treeUriFor(Uri.parse(path)) ?: return@withContext
        runCatching {
            resolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        runCatching {
            resolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private suspend fun copyDocumentToDocument(
        source: Uri,
        destinationParent: Uri,
        onProgress: (String) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        val meta = documentMeta(source) ?: throw IOException("This cloud item is no longer available.")
        val name = uniqueDocumentName(destinationParent, meta.name)
        if (meta.isDirectory) {
            onProgress("Copying ${meta.name}")
            val created = createDocument(
                destinationParent,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
            val children = listDirectory(source, showHidden = true)
            for (child in children) {
                copyDocumentToDocument(Uri.parse(child.path), created, onProgress)
            }
        } else {
            onProgress("Copying ${meta.name}")
            val created = createDocument(
                destinationParent,
                meta.mimeType.ifBlank { FileEntry.mimeTypeFor(meta.name) },
                name,
            )
            resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(created, "w")?.use { output ->
                    copyStream(input, output)
                } ?: throw IOException("Could not write ${meta.name}.")
            } ?: throw IOException("Could not read ${meta.name}.")
        }
    }

    private suspend fun copyDocumentToLocal(
        source: Uri,
        destinationDirectory: File,
        onProgress: (String) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        val meta = documentMeta(source) ?: throw IOException("This cloud item is no longer available.")
        val target = uniqueLocalFile(destinationDirectory, meta.name)
        if (meta.isDirectory) {
            onProgress("Copying ${meta.name}")
            if (!target.mkdirs() && !target.isDirectory) {
                throw IOException("Could not create ${target.name}.")
            }
            val children = listDirectory(source, showHidden = true)
            for (child in children) {
                copyDocumentToLocal(Uri.parse(child.path), target, onProgress)
            }
        } else {
            onProgress("Copying ${meta.name}")
            resolver.openInputStream(source)?.use { input ->
                FileOutputStream(target).use { output ->
                    copyStream(input, output)
                }
            } ?: throw IOException("Could not read ${meta.name}.")
        }
    }

    private suspend fun copyLocalToDocument(
        source: File,
        destinationParent: Uri,
        onProgress: (String) -> Unit,
    ) {
        currentCoroutineContext().ensureActive()
        val name = uniqueDocumentName(destinationParent, source.name)
        if (source.isDirectory) {
            onProgress("Copying ${source.name}")
            val created = createDocument(
                destinationParent,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
            val children = source.listFiles()
                ?: throw IOException("Could not read ${source.name}.")
            for (child in children) {
                copyLocalToDocument(child, created, onProgress)
            }
        } else {
            onProgress("Copying ${source.name}")
            val created = createDocument(destinationParent, FileEntry.mimeTypeFor(source.name), name)
            resolver.openOutputStream(created, "w")?.use { output ->
                FileInputStream(source).use { input ->
                    copyStream(input, output)
                }
            } ?: throw IOException("Could not write ${source.name}.")
        }
    }

    private suspend fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
    }

    private suspend fun deleteTree(uri: Uri) {
        currentCoroutineContext().ensureActive()
        val path = uri.toString()
        val meta = runCatching { documentMeta(uri) }.getOrNull()
        if (meta?.isDirectory == true) {
            val children = runCatching { listDirectory(uri, showHidden = true) }
                .getOrDefault(emptyList())
            for (child in children) {
                deleteTree(Uri.parse(child.path))
            }
        }
        if (!DocumentsContract.deleteDocument(resolver, uri)) {
            throw IOException("Could not delete ${labelByPath[path] ?: "this item"}.")
        }
        parentByPath.remove(path)
        labelByPath.remove(path)
    }

    private fun createDocument(parent: Uri, mimeType: String, name: String): Uri {
        val created = DocumentsContract.createDocument(resolver, parent, mimeType, name)
            ?: throw IOException("Could not create $name.")
        val path = created.toString()
        parentByPath[path] = parent.toString()
        labelByPath[path] = name
        return created
    }

    private fun ensureNameAvailable(parent: Uri, name: String) {
        if (name in childNames(parent)) {
            throw IOException("A file or folder with that name already exists.")
        }
    }

    private fun childNames(parent: Uri): Set<String> {
        val treeUri = treeUriFor(parent)
            ?: throw IOException("This cloud folder is not available.")
        val documentId = DocumentsContract.getDocumentId(parent)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val names = mutableSetOf<String>()
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (nameIndex < 0) throw IOException("This cloud folder cannot be read.")
            while (cursor.moveToNext()) {
                cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let(names::add)
            }
        } ?: throw IOException("This cloud folder cannot be read.")
        return names
    }

    private fun uniqueDocumentName(parent: Uri, desired: String): String {
        val existing = childNames(parent)
        if (desired !in existing) return desired
        val (base, extension) = splitName(desired)
        var index = 2
        var candidate = "$base ($index)$extension"
        while (candidate in existing) {
            index++
            candidate = "$base ($index)$extension"
        }
        return candidate
    }

    private fun uniqueLocalFile(directory: File, name: String): File {
        var candidate = File(directory, name)
        if (!candidate.exists()) return candidate
        val (base, extension) = splitName(name)
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "$base ($index)$extension")
            index++
        }
        return candidate
    }

    private fun splitName(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) {
            name.substring(0, dot) to name.substring(dot)
        } else {
            name to ""
        }
    }

    private fun isDescendantOrSelf(ancestorPath: String, candidatePath: String): Boolean {
        var current: String? = candidatePath
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current)) {
            if (current == ancestorPath) return true
            current = parentByPath[current]
        }
        return false
    }

    private fun ensureKnown(path: String) {
        if (parentByPath.containsKey(path)) return
        val uri = runCatching { Uri.parse(path) }.getOrNull() ?: return
        if (isTreeRoot(uri)) {
            parentByPath[path] = null
        }
    }

    private fun isTreeRoot(uri: Uri): Boolean = runCatching {
        val treeUri = treeUriFor(uri) ?: return@runCatching false
        DocumentsContract.getTreeDocumentId(treeUri) == DocumentsContract.getDocumentId(uri)
    }.getOrDefault(false)

    private fun treeUriFor(uri: Uri): Uri? {
        val segments = uri.pathSegments
        val treeIndex = segments.indexOf("tree")
        if (treeIndex < 0 || treeIndex + 1 >= segments.size) return null
        val authority = uri.authority ?: return null
        return Uri.Builder()
            .scheme("content")
            .authority(authority)
            .appendPath("tree")
            .appendPath(segments[treeIndex + 1])
            .build()
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun documentMeta(uri: Uri): DocumentMeta? {
        resolver.query(uri, DOCUMENT_PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else ""
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
            val modified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) {
                cursor.getLong(modifiedIndex)
            } else {
                0L
            }
            return DocumentMeta(
                name = name?.takeIf { it.isNotBlank() } ?: "Untitled",
                mimeType = mime,
                isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                size = size,
                modified = modified,
            )
        }
        return null
    }

    private fun entryFor(
        uri: Uri,
        name: String,
        mimeType: String,
        size: Long,
        modified: Long,
        directory: Boolean,
    ): FileEntry = FileEntry(
        path = uri.toString(),
        name = name,
        isDirectory = directory,
        size = if (directory) 0L else size.coerceAtLeast(0L),
        lastModified = modified,
        mimeType = if (directory) {
            FileEntry.DIRECTORY_MIME
        } else {
            mimeType.ifBlank { FileEntry.mimeTypeFor(name) }
        },
        isHidden = name.startsWith('.'),
    )

    private fun persistAccess(treeUri: Uri): Boolean {
        val both = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (runCatching {
                resolver.takePersistableUriPermission(treeUri, both)
            }.isSuccess
        ) {
            return true
        }
        return runCatching {
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
    }

    private data class DocumentMeta(
        val name: String,
        val mimeType: String,
        val isDirectory: Boolean,
        val size: Long,
        val modified: Long,
    )

    private companion object {
        const val BUFFER_SIZE = 64 * 1024

        val DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
