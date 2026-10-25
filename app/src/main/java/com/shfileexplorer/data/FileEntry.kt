package com.shfileexplorer.data

import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File

data class FileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val isHidden: Boolean,
) {
    val isSaf: Boolean get() = path.startsWith("content://", ignoreCase = true)
    val uri: Uri? get() = if (isSaf) Uri.parse(path) else null
    val file: File? get() = if (isSaf) null else File(path)
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val isZip: Boolean get() = !isDirectory && (extension == "zip" || mimeType == "application/zip")
    val isImage: Boolean get() = !isDirectory && extension in IMAGE_EXTENSIONS
    val isVideo: Boolean get() = !isDirectory && extension in VIDEO_EXTENSIONS
    val isPreviewable: Boolean get() = isImage || isVideo

    companion object {
        fun from(file: File): FileEntry {
            val directory = file.isDirectory
            val mime = if (directory) DIRECTORY_MIME else mimeTypeFor(file.name)
            return FileEntry(
                path = file.absolutePath,
                name = file.name,
                isDirectory = directory,
                size = if (directory) 0L else file.length(),
                lastModified = file.lastModified(),
                mimeType = mime,
                isHidden = file.name.startsWith('.'),
            )
        }

        fun mimeTypeFor(name: String): String {
            val extension = name.substringAfterLast('.', "").lowercase()
            if (extension.isEmpty()) return "application/octet-stream"
            return MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(extension)
                ?: when (extension) {
                    "apk", "apks", "xapk" -> "application/vnd.android.package-archive"
                    "zip" -> "application/zip"
                    "7z" -> "application/x-7z-compressed"
                    "rar" -> "application/vnd.rar"
                    "tar" -> "application/x-tar"
                    "gz", "gzip" -> "application/gzip"
                    "md", "log", "conf", "ini", "kt", "kts" -> "text/plain"
                    else -> "application/octet-stream"
                }
        }

        const val DIRECTORY_MIME = "inode/directory"

        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif",
        )
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v",
        )
    }
}

enum class SortMode(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    MODIFIED("Modified"),
    TYPE("Type"),
}

data class SortSpec(
    val mode: SortMode = SortMode.NAME,
    val ascending: Boolean = true,
) {
    fun apply(entries: List<FileEntry>): List<FileEntry> {
        val comparator = when (mode) {
            SortMode.NAME -> compareBy<FileEntry> { it.name.lowercase() }
            SortMode.SIZE -> compareBy<FileEntry> { it.size }
            SortMode.MODIFIED -> compareBy<FileEntry> { it.lastModified }
            SortMode.TYPE -> compareBy<FileEntry> { it.extension }.thenBy { it.name.lowercase() }
        }
        val directed = if (ascending) comparator else comparator.reversed()
        return entries.sortedWith(
            compareByDescending<FileEntry> { it.isDirectory }
                .then(directed),
        )
    }
}

data class StorageStats(
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toDouble() / totalBytes).toFloat()
}

data class ClipboardState(
    val paths: List<String>,
    val cut: Boolean,
)

data class OperationState(
    val label: String,
)

data class BrowserLocation(
    val label: String,
    val path: String,
    val isDirectory: Boolean = true,
)
