package com.personal.animeplayer.ui.compose.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.personal.animeplayer.data.model.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object VideoThumbnailLoader {

    private const val DISK_CACHE_DIR_NAME = "video_thumbs"
    private const val MAX_DISK_CACHE_BYTES = 45L * 1024L * 1024L
    private const val JPEG_QUALITY = 86
    private const val TOUCH_INTERVAL_MS = 60L * 60L * 1000L

    private val thumbCache = object : LruCache<String, ImageBitmap>(120) {}
    private val durationCache = object : LruCache<String, Long>(300) {}
    private val hasTrimmedThisSession = AtomicBoolean(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(4)

    fun getCachedThumbnail(uri: String): ImageBitmap? = thumbCache.get(uri)

    fun getCachedDuration(uri: String): Long? = durationCache.get(uri)

    suspend fun evict(context: Context, uris: Collection<String>) {
        if (uris.isEmpty()) return
        val appContext = context.applicationContext
        uris.forEach { uri ->
            thumbCache.remove(uri)
            durationCache.remove(uri)
        }
        withContext(Dispatchers.IO) {
            val dir = getDiskCacheDir(appContext)
            uris.forEach { uri ->
                val key = sha256Key(uri)
                File(dir, "$key.jpg").delete()
                File(dir, "$key.dur").delete()
            }
        }
    }

    suspend fun preload(
        context: Context,
        episodes: List<Episode>,
        maxCount: Int = 16,
        onProgress: (loaded: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        val targets = episodes.take(maxCount)
        if (targets.isEmpty()) {
            onProgress(1, 1)
            return
        }
        val appContext = context.applicationContext
        val completed = AtomicInteger(0)
        coroutineScope {
            targets.map { episode ->
                launch {
                    try {
                        load(
                            context = appContext,
                            uriString = episode.uri,
                            extractDuration = episode.durationMs <= 0L
                        )
                    } finally {
                        onProgress(completed.incrementAndGet(), targets.size)
                    }
                }
            }.joinAll()
        }
        if (hasTrimmedThisSession.compareAndSet(false, true)) {
            withContext(Dispatchers.IO) {
                trimDiskCacheIfNeeded(getDiskCacheDir(appContext))
            }
        }
    }

    private fun getDiskCacheDir(context: Context): File {
        val dir = File(context.cacheDir, DISK_CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sha256Key(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_CHARS[v ushr 4])
            sb.append(HEX_CHARS[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX_CHARS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    )

    private fun readFromDiskCache(
        context: Context,
        key: String,
        needBitmap: Boolean,
        needDuration: Boolean
    ): Pair<ImageBitmap?, Long> {
        val dir = getDiskCacheDir(context)
        var loadedImg: ImageBitmap? = null
        var loadedDur = 0L

        if (needBitmap) {
            val imgFile = File(dir, "$key.jpg")
            if (imgFile.isFile && imgFile.length() > 0L) {
                try {
                    val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                    if (bmp != null) {
                        loadedImg = bmp.asImageBitmap()
                        val now = System.currentTimeMillis()
                        if (now - imgFile.lastModified() > TOUCH_INTERVAL_MS) {
                            imgFile.setLastModified(now)
                        }
                    } else {
                        imgFile.delete()
                    }
                } catch (_: Throwable) {
                }
            }
        }

        if (needDuration) {
            val durFile = File(dir, "$key.dur")
            if (durFile.isFile) {
                loadedDur = try {
                    durFile.readText(Charsets.UTF_8).trim().toLongOrNull() ?: 0L
                } catch (_: Throwable) {
                    0L
                }
            }
        }

        return Pair(loadedImg, loadedDur)
    }

    private fun saveToDiskCache(
        context: Context,
        key: String,
        bitmap: Bitmap?,
        durationMs: Long
    ) {
        val dir = getDiskCacheDir(context)
        if (bitmap != null) {
            val targetFile = File(dir, "$key.jpg")
            if (!targetFile.exists()) {
                val tmpFile = File(dir, "$key.jpg.${Thread.currentThread().id}.tmp")
                var softwareCopy: Bitmap? = null
                try {
                    val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                        bitmap.config == Bitmap.Config.HARDWARE
                    ) {
                        bitmap.copy(Bitmap.Config.ARGB_8888, false).also { softwareCopy = it }
                    } else {
                        bitmap
                    }
                    if (source != null) {
                        val ok = FileOutputStream(tmpFile).use { out ->
                            source.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                        }
                        if (ok && tmpFile.length() > 0L) {
                            if (!tmpFile.renameTo(targetFile)) {
                                tmpFile.copyTo(targetFile, overwrite = true)
                                tmpFile.delete()
                            }
                        } else {
                            tmpFile.delete()
                        }
                    }
                } catch (_: Throwable) {
                    tmpFile.delete()
                } finally {
                    softwareCopy?.recycle()
                }
            }
        }

        if (durationMs > 0L) {
            val durFile = File(dir, "$key.dur")
            if (!durFile.exists()) {
                try {
                    durFile.writeText(durationMs.toString(), Charsets.UTF_8)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun trimDiskCacheIfNeeded(dir: File) {
        try {
            val files = dir.listFiles() ?: return
            var totalBytes = 0L
            for (f in files) {
                if (f.name.endsWith(".tmp")) {
                    f.delete()
                } else {
                    totalBytes += f.length()
                }
            }
            if (totalBytes <= MAX_DISK_CACHE_BYTES) return

            val jpgFiles = files
                .filter { it.isFile && it.name.endsWith(".jpg") }
                .sortedBy { it.lastModified() }

            for (jpg in jpgFiles) {
                if (totalBytes <= (MAX_DISK_CACHE_BYTES * 8L) / 10L) break
                val base = jpg.name.removeSuffix(".jpg")
                val durSibling = File(dir, "$base.dur")
                totalBytes -= jpg.length() + (if (durSibling.isFile) durSibling.length() else 0L)
                jpg.delete()
                durSibling.delete()
            }
        } catch (_: Throwable) {
        }
    }

    private fun isDarkFrame(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return false
        val sampleBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            bitmap.config == Bitmap.Config.HARDWARE
        ) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false
        } else {
            bitmap
        }
        return try {
            val cols = 8
            val rows = 6
            var totalLuma = 0f
            var samples = 0
            for (cy in 0 until rows) {
                val py = ((cy + 0.5f) * h / rows).toInt().coerceIn(0, h - 1)
                for (cx in 0 until cols) {
                    val px = ((cx + 0.5f) * w / cols).toInt().coerceIn(0, w - 1)
                    val pixel = sampleBitmap.getPixel(px, py)
                    val r = android.graphics.Color.red(pixel)
                    val g = android.graphics.Color.green(pixel)
                    val b = android.graphics.Color.blue(pixel)
                    totalLuma += 0.299f * r + 0.587f * g + 0.114f * b
                    samples++
                }
            }
            samples > 0 && (totalLuma / samples) < 20f
        } catch (_: Throwable) {
            false
        } finally {
            if (sampleBitmap !== bitmap) {
                sampleBitmap.recycle()
            }
        }
    }

    private fun retrieveScaledFrame(
        retriever: MediaMetadataRetriever,
        timeUs: Long
    ): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                360,
                202
            )
        } else {
            retriever.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )?.let {
                Bitmap.createScaledBitmap(it, 360, 202, true)
            }
        }
    }

    suspend fun load(
        context: Context,
        uriString: String,
        extractDuration: Boolean
    ): Pair<ImageBitmap?, Long> {
        val appContext = context.applicationContext
        return withContext(dispatcher) {
            var cachedThumb = thumbCache.get(uriString)
            var cachedDur = durationCache.get(uriString) ?: 0L
            if (cachedThumb != null && (!extractDuration || cachedDur > 0L)) {
                return@withContext Pair(cachedThumb, cachedDur)
            }

            val diskKey = sha256Key(uriString)
            val (diskThumb, diskDur) = readFromDiskCache(
                context = appContext,
                key = diskKey,
                needBitmap = cachedThumb == null,
                needDuration = extractDuration && cachedDur <= 0L
            )
            if (diskThumb != null) {
                cachedThumb = diskThumb
                thumbCache.put(uriString, diskThumb)
            }
            if (diskDur > 0L) {
                cachedDur = diskDur
                durationCache.put(uriString, diskDur)
            }
            if (cachedThumb != null && (!extractDuration || cachedDur > 0L)) {
                return@withContext Pair(cachedThumb, cachedDur)
            }

            val uri = Uri.parse(uriString)
            var fastFallback: Bitmap? = null

            if (!extractDuration &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                uri.scheme == "content" &&
                uri.authority == MediaStore.AUTHORITY
            ) {
                try {
                    val fastBmp = appContext.contentResolver.loadThumbnail(uri, Size(360, 202), null)
                    if (!isDarkFrame(fastBmp)) {
                        saveToDiskCache(appContext, diskKey, fastBmp, cachedDur)
                        val fastThumb = fastBmp.asImageBitmap()
                        thumbCache.put(uriString, fastThumb)
                        return@withContext Pair(fastThumb, cachedDur)
                    }
                    fastFallback = fastBmp
                } catch (_: Exception) {
                    // Fallback to MediaMetadataRetriever below
                }
            }

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, uri)
                val dur = if (extractDuration || cachedDur <= 0L) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                } else {
                    cachedDur
                }

                var chosenBitmapForDisk: Bitmap? = null
                val imageBitmap = cachedThumb ?: run {
                    val firstFrame = retrieveScaledFrame(retriever, 10_000_000L)
                    val chosen = if (firstFrame == null || isDarkFrame(firstFrame)) {
                        val altUs = when {
                            dur > 30_000L -> dur * 180L
                            dur > 0L -> (dur * 350L).coerceAtLeast(1_500_000L)
                            else -> 35_000_000L
                        }
                        val altFrame = retrieveScaledFrame(retriever, altUs)
                        when {
                            altFrame != null && (!isDarkFrame(altFrame) || firstFrame == null) -> altFrame
                            firstFrame != null -> firstFrame
                            else -> fastFallback
                        }
                    } else {
                        firstFrame
                    }
                    chosenBitmapForDisk = chosen
                    chosen?.asImageBitmap()
                }

                if (chosenBitmapForDisk != null || dur > 0L) {
                    saveToDiskCache(appContext, diskKey, chosenBitmapForDisk, dur)
                }
                if (imageBitmap != null) {
                    thumbCache.put(uriString, imageBitmap)
                }
                if (dur > 0L) {
                    durationCache.put(uriString, dur)
                }
                Pair(imageBitmap, dur)
            } catch (_: Exception) {
                val fallbackBmp = if (cachedThumb == null) fastFallback else null
                if (fallbackBmp != null) {
                    saveToDiskCache(appContext, diskKey, fallbackBmp, cachedDur)
                }
                val fallbackImg = cachedThumb ?: fallbackBmp?.asImageBitmap()
                if (fallbackImg != null) {
                    thumbCache.put(uriString, fallbackImg)
                }
                Pair(fallbackImg, cachedDur)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }
    }
}

@Composable
fun rememberEpisodeThumbnail(
    uri: String,
    knownDurationMs: Long
): State<Pair<ImageBitmap?, Long>> {
    val context = LocalContext.current
    val initialImage = remember(uri) { VideoThumbnailLoader.getCachedThumbnail(uri) }
    val initialDuration = remember(uri, knownDurationMs) {
        if (knownDurationMs > 0L) {
            knownDurationMs
        } else {
            VideoThumbnailLoader.getCachedDuration(uri) ?: 0L
        }
    }

    val resultState = remember(uri, initialDuration) {
        mutableStateOf(Pair(initialImage, initialDuration))
    }

    if (initialImage == null || initialDuration <= 0L) {
        LaunchedEffect(uri, initialDuration) {
            val cachedImg = VideoThumbnailLoader.getCachedThumbnail(uri)
            val cachedDur = if (initialDuration > 0L) {
                initialDuration
            } else {
                VideoThumbnailLoader.getCachedDuration(uri) ?: 0L
            }

            if (cachedImg != null && cachedDur > 0L) {
                resultState.value = Pair(cachedImg, cachedDur)
                return@LaunchedEffect
            }

            val needDuration = cachedDur <= 0L
            val (loadedImg, loadedDur) = VideoThumbnailLoader.load(context, uri, needDuration)
            val finalImg = loadedImg ?: cachedImg
            val finalDur = if (cachedDur > 0L) cachedDur else loadedDur
            resultState.value = Pair(finalImg, finalDur)
        }
    }

    return resultState
}
