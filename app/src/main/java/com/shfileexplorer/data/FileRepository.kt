package com.shfileexplorer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileRepository {

    suspend fun listDirectory(directory: File, showHidden: Boolean): List<FileEntry> =
        withContext(Dispatchers.IO) {
            if (!directory.isDirectory) throw IOException("This location is not a folder.")
            val children = directory.listFiles()
                ?: throw IOException("This folder cannot be read. Check file access.")
            children.asSequence()
                .filter { showHidden || !it.name.startsWith('.') }
                .map(FileEntry::from)
                .toList()
        }

    suspend fun search(
        root: File,
        query: String,
        showHidden: Boolean,
        subfolders: Boolean,
        limit: Int = 500,
        onMatch: (FileEntry) -> Unit = {},
    ): List<FileEntry> = withContext(Dispatchers.IO) {
        val needle = query.trim()
        if (needle.isEmpty()) return@withContext emptyList()
        val results = ArrayList<FileEntry>()
        val pending = ArrayDeque<File>()
        pending.add(root)
        while (pending.isNotEmpty() && results.size < limit) {
            ensureActive()
            val directory = pending.removeFirst()
            val children = directory.listFiles() ?: continue
            for (child in children) {
                ensureActive()
                if (!showHidden && child.name.startsWith('.')) continue
                if (child.name.contains(needle, ignoreCase = true)) {
                    val entry = FileEntry.from(child)
                    results.add(entry)
                    onMatch(entry)
                    if (results.size >= limit) break
                }
                if (subfolders && child.isDirectory && !isSymbolicLink(child)) {
                    pending.add(child)
                }
            }
        }
        results
    }

    suspend fun createFolder(parent: File, rawName: String): File = withContext(Dispatchers.IO) {
        val name = validateName(rawName)
        val target = File(parent, name)
        if (target.exists()) throw IOException("A file or folder with that name already exists.")
        if (!target.mkdirs()) throw IOException("Could not create the folder.")
        target
    }

    suspend fun createTextFile(parent: File, rawName: String): File = withContext(Dispatchers.IO) {
        val name = validateName(rawName)
        val target = File(parent, name)
        if (target.exists()) throw IOException("A file or folder with that name already exists.")
        if (!target.createNewFile()) throw IOException("Could not create the file.")
        target
    }

    suspend fun rename(file: File, rawName: String): File = withContext(Dispatchers.IO) {
        val name = validateName(rawName)
        if (name == file.name) return@withContext file
        val target = File(file.parentFile ?: throw IOException("This item cannot be renamed."), name)
        if (target.exists()) throw IOException("A file or folder with that name already exists.")
        if (!file.renameTo(target)) throw IOException("Could not rename this item.")
        target
    }

    suspend fun delete(files: List<File>, onProgress: (String) -> Unit = {}): Int =
        withContext(Dispatchers.IO) {
            var deleted = 0
            for (file in files) {
                ensureNotProtected(file)
                onProgress("Deleting ${file.name}")
                if (deleteSafely(file)) deleted++ else {
                    throw IOException("Could not delete ${file.name}.")
                }
            }
            deleted
        }

    suspend fun copy(
        sources: List<File>,
        destination: File,
        onProgress: (String) -> Unit = {},
    ): List<File> = withContext(Dispatchers.IO) {
        if (!destination.isDirectory) throw IOException("Choose a folder as the destination.")
        sources.map { source ->
            ensureNotProtected(source)
            val sourceCanonical = source.canonicalFile
            val destinationCanonical = destination.canonicalFile
            if (sourceCanonical == destinationCanonical ||
                destinationCanonical.path.startsWith(sourceCanonical.path + File.separator)
            ) {
                throw IOException("Cannot copy a folder into itself.")
            }
            val target = uniqueDestination(destination, source.name)
            try {
                copyRecursively(source, target, onProgress)
            } catch (failure: Exception) {
                deleteSafely(target)
                throw failure
            }
            target
        }
    }

    suspend fun move(
        sources: List<File>,
        destination: File,
        onProgress: (String) -> Unit = {},
    ): List<File> = withContext(Dispatchers.IO) {
        if (!destination.isDirectory) throw IOException("Choose a folder as the destination.")
        sources.map { source ->
            ensureNotProtected(source)
            val sourceCanonical = source.canonicalFile
            val destinationCanonical = destination.canonicalFile
            if (sourceCanonical == destinationCanonical) {
                throw IOException("Cannot move a folder into itself.")
            }
            if (destinationCanonical.path.startsWith(sourceCanonical.path + File.separator)) {
                throw IOException("Cannot move a folder inside itself.")
            }

            val target = uniqueDestination(destination, source.name)
            onProgress("Moving ${source.name}")
            if (source.renameTo(target)) {
                target
            } else {
                copyRecursively(source, target, onProgress)
                if (!deleteSafely(source)) {
                    deleteSafely(target)
                    throw IOException("Copied ${source.name}, but could not remove the original.")
                }
                target
            }
        }
    }

    suspend fun compress(
        sources: List<File>,
        destination: File,
        archiveName: String,
        onProgress: (String) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        if (!destination.isDirectory) throw IOException("Choose a folder for the archive.")
        if (sources.isEmpty()) throw IOException("Select at least one item.")
        val safeName = validateName(archiveName)
        val normalized = if (safeName.lowercase().endsWith(".zip")) safeName else "$safeName.zip"
        val output = uniqueDestination(destination, normalized)

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
                for (source in sources) {
                    ensureActive()
                    ensureNotProtected(source)
                    onProgress("Compressing ${source.name}")
                    addToZip(source, source.name, zip)
                }
            }
        } catch (failure: Exception) {
            output.delete()
            throw failure
        }
        output
    }

    suspend fun extractZip(
        archive: File,
        destination: File,
        onProgress: (String) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        if (!destination.isDirectory) throw IOException("Choose a folder for the extracted files.")
        if (!archive.isFile) throw IOException("Archive not found.")
        val baseName = archive.name.substringBeforeLast('.', archive.name)
        val target = uniqueDestination(destination, baseName.ifBlank { "extracted" })
        if (!target.mkdirs()) throw IOException("Could not create the extraction folder.")

        var entries = 0
        var totalBytes = 0L
        val canonicalRoot = target.canonicalFile
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > MAX_ZIP_ENTRIES) throw IOException("Archive has too many entries.")
                    onProgress("Extracting ${entry.name}")
                    val output = File(target, entry.name)
                    val canonicalOutput = output.canonicalFile
                    if (canonicalOutput != canonicalRoot &&
                        !canonicalOutput.path.startsWith(canonicalRoot.path + File.separator)
                    ) {
                        throw IOException("Archive contains an unsafe path.")
                    }
                    if (entry.isDirectory) {
                        if (!output.mkdirs() && !output.isDirectory) {
                            throw IOException("Could not create ${entry.name}.")
                        }
                    } else {
                        output.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(output)).use { out ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                totalBytes += read
                                if (totalBytes > MAX_EXTRACTED_BYTES) {
                                    throw IOException("Archive is too large to extract safely.")
                                }
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (failure: Exception) {
            deleteSafely(target)
            throw failure
        }
        target
    }

    fun validateName(rawName: String): String {
        val name = rawName.trim()
        if (name.isEmpty()) throw IOException("Enter a name.")
        if (name == "." || name == "..") throw IOException("That name is reserved.")
        if (name.contains('/') || name.contains('\u0000')) {
            throw IOException("Names cannot contain slashes.")
        }
        if (name.length > 255) throw IOException("That name is too long.")
        return name
    }

    private fun copyRecursively(source: File, target: File, onProgress: (String) -> Unit) {
        if (isSymbolicLink(source)) return
        if (source.isDirectory) {
            if (!target.mkdirs() && !target.isDirectory) {
                throw IOException("Could not create ${target.name}.")
            }
            val children = source.listFiles() ?: throw IOException("Could not read ${source.name}.")
            for (child in children) {
                copyRecursively(child, File(target, child.name), onProgress)
            }
            target.setLastModified(source.lastModified())
        } else {
            onProgress("Copying ${source.name}")
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
            target.setLastModified(source.lastModified())
        }
    }

    private fun addToZip(file: File, entryName: String, zip: ZipOutputStream) {
        if (isSymbolicLink(file)) return
        val normalized = entryName.replace(File.separatorChar, '/')
        if (file.isDirectory) {
            zip.putNextEntry(ZipEntry("$normalized/"))
            zip.closeEntry()
            file.listFiles()?.forEach { child ->
                addToZip(child, "$normalized/${child.name}", zip)
            }
        } else {
            zip.putNextEntry(ZipEntry(normalized))
            FileInputStream(file).use { input -> input.copyTo(zip, BUFFER_SIZE) }
            zip.closeEntry()
            zip.setLevel(6)
        }
    }

    private fun deleteSafely(file: File): Boolean {
        if (isSymbolicLink(file)) return file.delete()
        if (file.isDirectory) {
            val children = file.listFiles() ?: return false
            for (child in children) {
                if (!deleteSafely(child)) return false
            }
        }
        return file.delete()
    }

    private fun uniqueDestination(directory: File, name: String): File {
        var candidate = File(directory, name)
        if (!candidate.exists()) return candidate
        val directoryPart: String
        val extensionPart: String
        val dot = name.lastIndexOf('.')
        if (dot > 0) {
            directoryPart = name.substring(0, dot)
            extensionPart = name.substring(dot)
        } else {
            directoryPart = name
            extensionPart = ""
        }
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "$directoryPart ($index)$extensionPart")
            index++
        }
        return candidate
    }

    private fun ensureNotProtected(file: File) {
        val canonical = file.canonicalFile
        if (canonical.parentFile == null) {
            throw IOException("This location cannot be changed.")
        }
    }

    private fun isSymbolicLink(file: File): Boolean = runCatching {
        Files.isSymbolicLink(file.toPath())
    }.getOrDefault(false)

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_ZIP_ENTRIES = 50_000
        const val MAX_EXTRACTED_BYTES = 10L * 1024 * 1024 * 1024
    }
}
