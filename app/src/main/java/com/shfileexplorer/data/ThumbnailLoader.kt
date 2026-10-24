package com.shfileexplorer.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Small, dependency-free thumbnail decoder with an in-memory LRU cache.
 *
 * Thumbnails are only decoded for visible rows, and the cache is bounded by a
 * fraction of the process heap so scrolling through large photo folders does
 * not grow memory without limit.
 */
object ThumbnailLoader {

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    // Keep fast scrolling responsive: decode only a few thumbnails at a time.
    private val decodeGate = Semaphore(3)

    suspend fun load(file: File, targetPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        val key = "${file.absolutePath}:${file.lastModified()}:$targetPx"
        cache.get(key)?.let { return@withContext it }

        val bitmap = decodeGate.withPermit {
            try {
                if (isVideo(file.name)) loadVideoFrame(file, targetPx) else loadImage(file, targetPx)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        }
        if (bitmap != null) cache.put(key, bitmap)
        bitmap
    }

    private fun loadImage(file: File, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        var sample = 1
        while (width / (sample * 2) >= targetPx && height / (sample * 2) >= targetPx) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return scaleDown(decoded, targetPx)
    }

    private fun loadVideoFrame(file: File, targetPx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            scaleDown(frame, targetPx)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleDown(bitmap: Bitmap, targetPx: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= targetPx) return bitmap
        val ratio = targetPx.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun isVideo(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    private fun cacheSizeKb(): Int {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
        return maxKb.coerceIn(8 * 1024, 40 * 1024)
    }

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v",
    )
}
