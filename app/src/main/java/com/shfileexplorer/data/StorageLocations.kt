package com.shfileexplorer.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

object StorageLocations {

    fun primaryRoot(): File = Environment.getExternalStorageDirectory()

    fun quickLocations(context: Context): List<BrowserLocation> {
        val root = primaryRoot()
        val candidates = listOf(
            "Internal storage" to root,
            "Downloads" to File(root, Environment.DIRECTORY_DOWNLOADS),
            "Documents" to File(root, Environment.DIRECTORY_DOCUMENTS),
            "Pictures" to File(root, Environment.DIRECTORY_PICTURES),
            "DCIM" to File(root, Environment.DIRECTORY_DCIM),
            "Music" to File(root, Environment.DIRECTORY_MUSIC),
            "Movies" to File(root, Environment.DIRECTORY_MOVIES),
            "Android media" to File(root, "Android/media"),
            "App storage" to (context.getExternalFilesDir(null) ?: context.filesDir),
        )
        val seen = mutableSetOf<String>()
        val locations = candidates.mapNotNull { (label, file) ->
            val path = file.absolutePath
            if (file.exists() && seen.add(path)) BrowserLocation(label, path) else null
        }.toMutableList()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val storageManager = context.getSystemService(StorageManager::class.java)
            storageManager?.storageVolumes?.forEach { volume ->
                if (!volume.isPrimary) {
                    val directory = volume.directory ?: return@forEach
                    if (directory.exists() && seen.add(directory.absolutePath)) {
                        locations.add(
                            BrowserLocation(
                                label = volume.getDescription(context).ifBlank { "SD card" },
                                path = directory.absolutePath,
                            ),
                        )
                    }
                }
            }
        }

        return locations
    }

    fun storageRoots(context: Context): List<BrowserLocation> {
        val roots = mutableListOf(BrowserLocation("Internal storage", primaryRoot().absolutePath))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(StorageManager::class.java)
                ?.storageVolumes
                ?.forEach { volume ->
                    if (!volume.isPrimary) {
                        val directory = volume.directory ?: return@forEach
                        if (directory.exists()) {
                            roots.add(
                                BrowserLocation(
                                    label = volume.getDescription(context).ifBlank { "SD card" },
                                    path = directory.absolutePath,
                                ),
                            )
                        }
                    }
                }
        }
        return roots
    }

    fun rootFor(context: Context, path: String): BrowserLocation? {
        val normalized = path.trimEnd(File.separatorChar)
        return storageRoots(context)
            .filter { root ->
                normalized == root.path ||
                    normalized.startsWith(root.path.trimEnd(File.separatorChar) + File.separator)
            }
            .maxByOrNull { it.path.length }
    }

    fun isStorageRoot(context: Context, path: String): Boolean {
        val requested = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        return storageRoots(context).any {
            runCatching { File(it.path).canonicalFile }.getOrNull() == requested
        }
    }

    fun statsFor(path: String): StorageStats? = runCatching {
        val stat = StatFs(path)
        StorageStats(
            totalBytes = stat.blockCountLong * stat.blockSizeLong,
            freeBytes = stat.availableBlocksLong * stat.blockSizeLong,
        )
    }.getOrNull()
}
