package com.personal.animeplayer.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.personal.animeplayer.data.database.AnimeDatabase
import com.personal.animeplayer.data.model.AnimeSeries
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.FileOpResult
import com.personal.animeplayer.data.model.MediaScanResult
import com.personal.animeplayer.data.model.VideoDetailInfo
import com.personal.animeplayer.util.FileUtils
import com.personal.animeplayer.util.NaturalOrderComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.personal.animeplayer.player.PlaylistHolder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaRepository(private val context: Context) {

    private val db = AnimeDatabase.getInstance(context)
    private val progressDao = db.playbackProgressDao()
    private val naturalOrderComparator = NaturalOrderComparator()

    /**
     * Scans ALL videos on the device using MediaStore.
     * Returns MediaScanResult containing both the flat list of all videos and grouped folders.
     */
    suspend fun scanAllDeviceVideos(): MediaScanResult = withContext(Dispatchers.IO) {
        try {
            progressDao.deleteShortVideosProgress()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val progressMap = try {
            progressDao.getAllProgress().associateBy { it.videoUri }
        } catch (e: Exception) {
            emptyMap()
        }
        val seriesMap = mutableMapOf<String, MutableList<Episode>>()
        val bucketNames = mutableMapOf<String, String>()
        val allVideosList = mutableListOf<Episode>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} ASC, ${MediaStore.Video.Media.DISPLAY_NAME} ASC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val trashedCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED) else -1
                val pendingCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING) else -1

                while (cursor.moveToNext()) {
                    if (trashedCol >= 0 && cursor.getInt(trashedCol) != 0) continue
                    if (pendingCol >= 0 && cursor.getInt(pendingCol) != 0) continue
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()
                    if (PlaylistHolder.deletedUris.contains(contentUri)) continue

                    val fileName = cursor.getString(nameCol) ?: continue
                    val bucketId = cursor.getString(bucketIdCol) ?: "other"
                    val bucketName = cursor.getString(bucketNameCol) ?: "Video"
                    val durationMs = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000L

                    if (FileUtils.isSupportedVideoFile(fileName)) {

                        val progress = progressMap[contentUri]
                        val effectiveDuration = if (durationMs > 0) durationMs else (progress?.durationMs ?: 0L)
                        val isShort = effectiveDuration in 1 until 60_000L
                        val episode = Episode(
                            uri = contentUri,
                            title = FileUtils.getBaseName(fileName),
                            fileName = fileName,
                            seriesFolderUri = "mediastore://folder/$bucketId",
                            durationMs = effectiveDuration,
                            lastPositionMs = if (isShort) 0L else (progress?.lastPositionMs ?: 0L),
                            progressPercentage = if (isShort) 0 else (progress?.progressPercentage ?: 0),
                            fileSize = size,
                            dateAdded = dateAdded
                        )

                        allVideosList.add(episode)
                        bucketNames[bucketId] = bucketName
                        seriesMap.getOrPut(bucketId) { mutableListOf() }.add(episode)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        allVideosList.sortWith { e1, e2 -> naturalOrderComparator.compare(e1.fileName, e2.fileName) }

        val folders = seriesMap.map { (bucketId, episodes) ->
            val sorted = episodes.sortedWith { e1, e2 -> naturalOrderComparator.compare(e1.fileName, e2.fileName) }
            val latestDate = episodes.maxOfOrNull { it.dateAdded } ?: 0L
            AnimeSeries(
                folderUri = "mediastore://folder/$bucketId",
                name = bucketNames[bucketId] ?: "Folder Video",
                episodeCount = sorted.size,
                firstEpisodeUri = sorted.firstOrNull()?.uri,
                lastModified = latestDate
            )
        }.sortedWith { s1, s2 -> naturalOrderComparator.compare(s1.name, s2.name) }

        MediaScanResult(allVideos = allVideosList, folders = folders)
    }

    /**
     * Scans the user-selected SAF root folder on-demand.
     * Returns MediaScanResult containing all videos found and subfolders as series.
     */
    suspend fun scanAnimeLibrary(rootFolderUri: Uri): MediaScanResult = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, rootFolderUri) ?: return@withContext MediaScanResult()
        if (!rootDoc.isDirectory) return@withContext MediaScanResult()

        val progressMap = try {
            progressDao.getAllProgress().associateBy { it.videoUri }
        } catch (e: Exception) {
            emptyMap()
        }

        val seriesList = mutableListOf<AnimeSeries>()
        val allVideosList = mutableListOf<Episode>()

        rootDoc.listFiles().forEach { file ->
            if (file.isDirectory) {
                val subEpisodes = mutableListOf<Episode>()
                file.listFiles().forEach { subFile ->
                    if (subFile.isFile && subFile.name?.let { FileUtils.isSupportedVideoFile(it) } == true) {
                        val uriStr = subFile.uri.toString()
                        if (PlaylistHolder.deletedUris.contains(uriStr)) return@forEach
                        val fileName = subFile.name ?: "Video"
                        val progress = progressMap[uriStr]
                        val effectiveDuration = progress?.durationMs ?: 0L
                        val isShort = effectiveDuration in 1 until 60_000L
                        subEpisodes.add(
                            Episode(
                                uri = uriStr,
                                title = FileUtils.getBaseName(fileName),
                                fileName = fileName,
                                seriesFolderUri = file.uri.toString(),
                                durationMs = effectiveDuration,
                                lastPositionMs = if (isShort) 0L else (progress?.lastPositionMs ?: 0L),
                                progressPercentage = if (isShort) 0 else (progress?.progressPercentage ?: 0),
                                fileSize = subFile.length()
                            )
                        )
                    }
                }
                if (subEpisodes.isNotEmpty()) {
                    subEpisodes.sortWith { e1, e2 -> naturalOrderComparator.compare(e1.fileName, e2.fileName) }
                    allVideosList.addAll(subEpisodes)
                    seriesList.add(
                        AnimeSeries(
                            folderUri = file.uri.toString(),
                            name = file.name ?: "Unknown Series",
                            episodeCount = subEpisodes.size,
                            firstEpisodeUri = subEpisodes.firstOrNull()?.uri
                        )
                    )
                }
            } else if (file.isFile && file.name?.let { FileUtils.isSupportedVideoFile(it) } == true) {
                val uriStr = file.uri.toString()
                if (PlaylistHolder.deletedUris.contains(uriStr)) return@forEach
                val fileName = file.name ?: "Video"
                val progress = progressMap[uriStr]
                val effectiveDuration = progress?.durationMs ?: 0L
                val isShort = effectiveDuration in 1 until 60_000L
                allVideosList.add(
                    Episode(
                        uri = uriStr,
                        title = FileUtils.getBaseName(fileName),
                        fileName = fileName,
                        seriesFolderUri = rootDoc.uri.toString(),
                        durationMs = effectiveDuration,
                        lastPositionMs = if (isShort) 0L else (progress?.lastPositionMs ?: 0L),
                        progressPercentage = if (isShort) 0 else (progress?.progressPercentage ?: 0),
                        fileSize = file.length()
                    )
                )
            }
        }

        allVideosList.sortWith { e1, e2 -> naturalOrderComparator.compare(e1.fileName, e2.fileName) }
        val sortedSeries = seriesList.sortedWith { s1, s2 -> naturalOrderComparator.compare(s1.name, s2.name) }

        MediaScanResult(allVideos = allVideosList, folders = sortedSeries)
    }

    suspend fun refreshProgress(episodes: List<Episode>): List<Episode> = withContext(Dispatchers.IO) {
        try {
            progressDao.deleteShortVideosProgress()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val progressMap = try {
            progressDao.getAllProgress().associateBy { it.videoUri }
        } catch (e: Exception) {
            emptyMap()
        }
        episodes.map { ep ->
            val progress = progressMap[ep.uri]
            val dur = if (ep.durationMs > 0) ep.durationMs else (progress?.durationMs ?: 0L)
            val isShort = dur in 1 until 60_000L
            ep.copy(
                lastPositionMs = if (isShort) 0L else (progress?.lastPositionMs ?: ep.lastPositionMs),
                progressPercentage = if (isShort) 0 else (progress?.progressPercentage ?: ep.progressPercentage),
                durationMs = dur
            )
        }
    }

    /**
     * Fetches episodes inside a series folder with watch progress and natural sorting.
     */
    suspend fun getEpisodesForSeries(seriesFolderUri: Uri): List<Episode> = withContext(Dispatchers.IO) {
        if (seriesFolderUri.scheme == "mediastore") {
            val bucketId = seriesFolderUri.lastPathSegment ?: seriesFolderUri.host ?: ""
            return@withContext getMediaStoreEpisodes(bucketId)
        }

        val seriesDoc = DocumentFile.fromTreeUri(context, seriesFolderUri) ?: return@withContext emptyList()
        val files = seriesDoc.listFiles().filter { it.isFile && it.name?.let { name -> FileUtils.isSupportedVideoFile(name) } == true }

        val progressMap = try {
            progressDao.getAllProgress().associateBy { it.videoUri }
        } catch (e: Exception) {
            emptyMap()
        }

        val episodes = files.map { file ->
            val uriStr = file.uri.toString()
            val fileName = file.name ?: "Episode"
            val progress = progressMap[uriStr]
            val dur = progress?.durationMs ?: 0L
            val isShort = dur in 1 until 60_000L

            Episode(
                uri = uriStr,
                title = FileUtils.getBaseName(fileName),
                fileName = fileName,
                seriesFolderUri = seriesFolderUri.toString(),
                durationMs = dur,
                lastPositionMs = if (isShort) 0L else (progress?.lastPositionMs ?: 0L),
                progressPercentage = if (isShort) 0 else (progress?.progressPercentage ?: 0),
                fileSize = file.length()
            )
        }

        episodes.sortedWith { e1, e2 -> naturalOrderComparator.compare(e1.fileName, e2.fileName) }
    }

    private suspend fun getMediaStoreEpisodes(bucketId: String): List<Episode> {
        val list = mutableListOf<Episode>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )

        val selection = if (bucketId == "all") null else "${MediaStore.Video.Media.BUCKET_ID} = ?"
        val selectionArgs = if (bucketId == "all") null else arrayOf(bucketId)
        val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

        val progressMap = try {
            progressDao.getAllProgress().associateBy { it.videoUri }
        } catch (e: Exception) {
            emptyMap()
        }

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val trashedCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED) else -1
                val pendingCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING) else -1

                while (cursor.moveToNext()) {
                    if (trashedCol >= 0 && cursor.getInt(trashedCol) != 0) continue
                    if (pendingCol >= 0 && cursor.getInt(pendingCol) != 0) continue
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()
                    if (PlaylistHolder.deletedUris.contains(contentUri)) continue

                    val fileName = cursor.getString(nameCol) ?: continue
                    val durationMs = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000L

                    if (FileUtils.isSupportedVideoFile(fileName)) {
                        val progress = progressMap[contentUri]
                        val dur = if (durationMs > 0) durationMs else (progress?.durationMs ?: 0L)
                        val isShort = dur in 1 until 60_000L
                        list.add(
                            Episode(
                                uri = contentUri,
                                title = FileUtils.getBaseName(fileName),
                                fileName = fileName,
                                seriesFolderUri = "mediastore://folder/$bucketId",
                                durationMs = dur,
                                lastPositionMs = if (isShort) 0L else (progress?.lastPositionMs ?: 0L),
                                progressPercentage = if (isShort) 0 else (progress?.progressPercentage ?: 0),
                                fileSize = size,
                                dateAdded = dateAdded
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedWith { e1, e2 -> naturalOrderComparator.compare(e1.fileName, e2.fileName) }
    }

    /**
     * Finds matching external subtitle files in the same folder as the video.
     * E.g. for "video.mkv", finds "video.ass", "video.srt", "video.ind.ass", etc.
     */
    suspend fun findMatchingSubtitles(videoUri: Uri, videoFileName: String): List<DocumentFile> = withContext(Dispatchers.IO) {
        val videoBaseName = FileUtils.getBaseName(videoFileName).lowercase()
        val results = mutableListOf<DocumentFile>()

        // 1. If file URI or resolvable file path from MediaStore
        var filePath: String? = null
        if (videoUri.scheme == "file") {
            filePath = videoUri.path
        } else if (videoUri.scheme == "content" && videoUri.authority == MediaStore.AUTHORITY) {
            try {
                context.contentResolver.query(
                    videoUri,
                    arrayOf(MediaStore.Video.Media.DATA),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                        if (idx != -1) filePath = cursor.getString(idx)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val resolvedPath = filePath
        if (!resolvedPath.isNullOrBlank()) {
            val file = File(resolvedPath)
            val parentDir = file.parentFile
            if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
                parentDir.listFiles()?.forEach { candidate ->
                    if (candidate.isFile && FileUtils.isSupportedSubtitleFile(candidate.name)) {
                        val subBaseName = FileUtils.getBaseName(candidate.name).lowercase()
                        if (subBaseName == videoBaseName || subBaseName.startsWith(videoBaseName)) {
                            results.add(DocumentFile.fromFile(candidate))
                        }
                    }
                }
                if (results.isNotEmpty()) {
                    return@withContext results
                }
            }
        }

        // 2. Fallback for SAF Document / Tree URI
        try {
            if (videoUri.scheme == "content") {
                val doc = DocumentFile.fromSingleUri(context, videoUri)
                val parentDoc = doc?.parentFile
                parentDoc?.listFiles()?.forEach { f ->
                    val name = f.name
                    if (f.isFile && name != null && FileUtils.isSupportedSubtitleFile(name)) {
                        val subBaseName = FileUtils.getBaseName(name).lowercase()
                        if (subBaseName == videoBaseName || subBaseName.startsWith(videoBaseName)) {
                            results.add(f)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        results
    }

    suspend fun getVideoDetails(episode: Episode): VideoDetailInfo = withContext(Dispatchers.IO) {
        val uri = Uri.parse(episode.uri)
        var path = ""
        var durationMs = episode.durationMs
        var width = 0
        var height = 0
        var mimeType = ""
        var bitrate = 0L
        var dateAdded = episode.dateAdded

        // 1. Try MediaStore query for path & metadata if content uri
        if (uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
            val proj = arrayOf(
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.BITRATE,
                MediaStore.Video.Media.DATE_ADDED
            )
            try {
                context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val dataIdx = c.getColumnIndex(MediaStore.Video.Media.DATA)
                        if (dataIdx != -1) path = c.getString(dataIdx) ?: ""

                        val mimeIdx = c.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                        if (mimeIdx != -1) mimeType = c.getString(mimeIdx) ?: ""

                        val wIdx = c.getColumnIndex(MediaStore.Video.Media.WIDTH)
                        if (wIdx != -1) width = c.getInt(wIdx)

                        val hIdx = c.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                        if (hIdx != -1) height = c.getInt(hIdx)

                        val durIdx = c.getColumnIndex(MediaStore.Video.Media.DURATION)
                        if (durIdx != -1 && durationMs <= 0) durationMs = c.getLong(durIdx)

                        val bitIdx = c.getColumnIndex(MediaStore.Video.Media.BITRATE)
                        if (bitIdx != -1) bitrate = c.getLong(bitIdx)

                        val dIdx = c.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                        if (dIdx != -1 && dateAdded <= 0) dateAdded = c.getLong(dIdx) * 1000L
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (path.isBlank()) {
            path = uri.path ?: episode.fileName
        }

        // 2. Extract with MediaMetadataRetriever if needed
        if (width <= 0 || height <= 0 || bitrate <= 0 || mimeType.isBlank() || durationMs <= 0) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                if (width <= 0) width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                if (height <= 0) height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                if (durationMs <= 0) durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (bitrate <= 0) bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                if (mimeType.isBlank()) mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { retriever.release() } catch (e: Exception) {}
            }
        }

        val resolutionStr = if (width > 0 && height > 0) {
            val label = when {
                width >= 3840 || height >= 2160 -> " (4K UHD)"
                width >= 2560 || height >= 1440 -> " (2K QHD)"
                width >= 1920 || height >= 1080 -> " (Full HD)"
                width >= 1280 || height >= 720 -> " (HD)"
                width >= 854 || height >= 480 -> " (480p)"
                else -> ""
            }
            "$width × $height$label"
        } else {
            "Tidak diketahui"
        }

        val bitrateStr = if (bitrate > 0) {
            String.format(Locale.getDefault(), "%,d kbps", bitrate / 1000)
        } else {
            "Tidak diketahui"
        }

        val mimeStr = if (mimeType.isNotBlank()) mimeType else {
            val ext = episode.fileName.substringAfterLast('.', "").uppercase()
            if (ext.isNotBlank()) "video/$ext" else "Tidak diketahui"
        }

        val dateStr = if (dateAdded > 0) {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(dateAdded))
        } else {
            "Tidak diketahui"
        }

        val sizeExact = if (episode.fileSize > 0) {
            "${FileUtils.formatFileSize(episode.fileSize)} (${String.format(Locale.getDefault(), "%,d", episode.fileSize)} B)"
        } else {
            "0 B"
        }

        var detectedHdr = "SDR"
        var colorDetails = "BT.709 (8-bit)"

        try {
            val extractor = MediaExtractor()
            if (uri.scheme == "content") {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                }
            } else {
                extractor.setDataSource(path)
            }

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (trackMime.startsWith("video/")) {
                    val transfer = if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else -1
                    val standard = if (format.containsKey(MediaFormat.KEY_COLOR_STANDARD)) format.getInteger(MediaFormat.KEY_COLOR_STANDARD) else -1
                    val hasHdr10Plus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        format.containsKey(MediaFormat.KEY_HDR10_PLUS_INFO)
                    } else {
                        format.containsKey("hdr10-plus-info")
                    } || episode.fileName.contains("HDR10PLUS", ignoreCase = true) || episode.fileName.contains("HDR10+", ignoreCase = true)

                    val isPq = transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == 6
                    val isHlg = transfer == MediaFormat.COLOR_TRANSFER_HLG || transfer == 7
                    val isBt2020 = standard == MediaFormat.COLOR_STANDARD_BT2020 || standard == 6

                    if (hasHdr10Plus && (isPq || isBt2020 || episode.fileName.contains("HDR10PLUS", ignoreCase = true))) {
                        detectedHdr = "HDR10+"
                        colorDetails = "PQ (ST 2084) / BT.2020 10-bit Dinamis"
                    } else if (isPq) {
                        detectedHdr = "HDR10"
                        colorDetails = "PQ (ST 2084) / BT.2020 10-bit"
                    } else if (isHlg) {
                        detectedHdr = "HLG"
                        colorDetails = "Hybrid Log-Gamma / BT.2020"
                    } else if (episode.fileName.contains("HDR10+", ignoreCase = true) || episode.fileName.contains("HDR10PLUS", ignoreCase = true)) {
                        detectedHdr = "HDR10+"
                        colorDetails = "SMPTE ST 2094-40 Dinamis"
                    } else if (episode.fileName.contains("HDR", ignoreCase = true)) {
                        detectedHdr = "HDR10"
                        colorDetails = "BT.2020 10-bit"
                    }
                    break
                }
            }
            extractor.release()
        } catch (e: Exception) {
            if (episode.fileName.contains("HDR10PLUS", ignoreCase = true) || episode.fileName.contains("HDR10+", ignoreCase = true)) {
                detectedHdr = "HDR10+"
                colorDetails = "SMPTE ST 2094-40 Dinamis"
            } else if (episode.fileName.contains("HDR", ignoreCase = true)) {
                detectedHdr = "HDR10"
                colorDetails = "BT.2020 10-bit"
            }
        }

        VideoDetailInfo(
            title = episode.title,
            fileName = episode.fileName,
            path = path,
            fileSizeFormatted = sizeExact,
            durationFormatted = FileUtils.formatDuration(durationMs),
            resolution = resolutionStr,
            mimeType = mimeStr,
            bitrateFormatted = bitrateStr,
            dateAddedFormatted = dateStr,
            hdrType = detectedHdr,
            colorDetails = colorDetails
        )
    }

    suspend fun deleteVideo(episode: Episode): FileOpResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(episode.uri)
        try {
            // Check SAF Document Uri
            if (uri.authority?.contains("documents") == true) {
                val doc = DocumentFile.fromSingleUri(context, uri)
                if (doc != null && doc.delete()) {
                    progressDao.deleteProgress(episode.uri)
                    return@withContext FileOpResult.Success
                }
            }

            // Direct file delete if accessible
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists() && file.delete()) {
                    progressDao.deleteProgress(episode.uri)
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                    return@withContext FileOpResult.Success
                }
            }

            // MediaStore / ContentResolver delete
            val deletedRows = context.contentResolver.delete(uri, null, null)
            if (deletedRows > 0) {
                progressDao.deleteProgress(episode.uri)
                FileOpResult.Success
            } else {
                FileOpResult.Error("Gagal menghapus file.")
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                FileOpResult.RequiresConsent(pendingIntent.intentSender)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                FileOpResult.RequiresConsent(e.userAction.actionIntent.intentSender)
            } else {
                FileOpResult.Error(e.localizedMessage ?: "Izin tidak mencukupi untuk menghapus file.")
            }
        } catch (e: Exception) {
            FileOpResult.Error(e.localizedMessage ?: "Terjadi kesalahan saat menghapus video.")
        }
    }

    suspend fun deleteVideos(episodes: List<Episode>): FileOpResult = withContext(Dispatchers.IO) {
        if (episodes.isEmpty()) return@withContext FileOpResult.Success
        if (episodes.size == 1) return@withContext deleteVideo(episodes.first())

        val mediaStoreUris = mutableListOf<Uri>()
        var errorCount = 0

        for (episode in episodes) {
            val uri = Uri.parse(episode.uri)
            try {
                if (uri.authority?.contains("documents") == true) {
                    val doc = DocumentFile.fromSingleUri(context, uri)
                    if (doc != null && doc.delete()) {
                        progressDao.deleteProgress(episode.uri)
                    } else {
                        errorCount++
                    }
                } else if (uri.scheme == "file") {
                    val file = File(uri.path ?: "")
                    if (file.exists() && file.delete()) {
                        progressDao.deleteProgress(episode.uri)
                        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                    } else {
                        errorCount++
                    }
                } else {
                    mediaStoreUris.add(uri)
                }
            } catch (e: Exception) {
                errorCount++
            }
        }

        if (mediaStoreUris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, mediaStoreUris)
                return@withContext FileOpResult.RequiresConsent(pendingIntent.intentSender)
            } else {
                for (uri in mediaStoreUris) {
                    try {
                        val rows = context.contentResolver.delete(uri, null, null)
                        if (rows > 0) {
                            progressDao.deleteProgress(uri.toString())
                        } else {
                            errorCount++
                        }
                    } catch (e: SecurityException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                            return@withContext FileOpResult.RequiresConsent(e.userAction.actionIntent.intentSender)
                        }
                        errorCount++
                    } catch (e: Exception) {
                        errorCount++
                    }
                }
            }
        }

        if (errorCount < episodes.size) {
            FileOpResult.Success
        } else {
            FileOpResult.Error("Gagal menghapus video terpilih.")
        }
    }

    suspend fun renameVideo(episode: Episode, newName: String): FileOpResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(episode.uri)
        val oldExt = episode.fileName.substringAfterLast('.', "")
        val (cleanName, fullNewFileName) = if (FileUtils.isSupportedVideoFile(newName.trim())) {
            val nameWithoutExt = FileUtils.getBaseName(newName.trim())
            val ext = newName.trim().substringAfterLast('.', "")
            Pair(nameWithoutExt, "$nameWithoutExt.$ext")
        } else if (oldExt.isNotBlank() && newName.trim().endsWith(".$oldExt", ignoreCase = true)) {
            val nameWithoutExt = newName.trim().dropLast(oldExt.length + 1).trim()
            Pair(nameWithoutExt, "$nameWithoutExt.$oldExt")
        } else {
            val trimmed = newName.trim()
            val fullName = if (oldExt.isNotBlank()) "$trimmed.$oldExt" else trimmed
            Pair(trimmed, fullName)
        }

        try {
            // 1. Check SAF Document Uri
            if (uri.authority?.contains("documents") == true) {
                val renamedUri = DocumentsContract.renameDocument(context.contentResolver, uri, fullNewFileName)
                if (renamedUri != null) {
                    if (renamedUri.toString() != episode.uri) {
                        progressDao.getProgress(episode.uri)?.let { p ->
                            progressDao.insertOrUpdate(p.copy(videoUri = renamedUri.toString()))
                            progressDao.deleteProgress(episode.uri)
                        }
                    }
                    return@withContext FileOpResult.Success
                }
            }

            // 2. Try direct file rename if writable
            var directFileSuccess = false
            var originalFilePath: String? = null
            try {
                if (uri.scheme == "file") {
                    originalFilePath = uri.path
                } else if (uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
                    context.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(MediaStore.Video.Media.DATA)
                            if (idx != -1) originalFilePath = c.getString(idx)
                        }
                    }
                }

                val path = originalFilePath
                if (!path.isNullOrBlank()) {
                    val oldFile = File(path)
                    if (oldFile.exists() && oldFile.canWrite()) {
                        val newFile = File(oldFile.parentFile, fullNewFileName)
                        if (oldFile.renameTo(newFile)) {
                            directFileSuccess = true
                            MediaScannerConnection.scanFile(context, arrayOf(oldFile.absolutePath, newFile.absolutePath), null, null)
                            if (uri.scheme == "file") {
                                val newUriStr = Uri.fromFile(newFile).toString()
                                progressDao.getProgress(episode.uri)?.let { p ->
                                    progressDao.insertOrUpdate(p.copy(videoUri = newUriStr))
                                    progressDao.deleteProgress(episode.uri)
                                }
                                return@withContext FileOpResult.Success
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore and proceed to MediaStore update
            }

            // 3. MediaStore update
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fullNewFileName)
                put(MediaStore.MediaColumns.TITLE, cleanName)
            }
            var updated = 0
            try {
                updated = context.contentResolver.update(uri, values, null, null)
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                // In case TITLE is restricted by OEM MediaProvider
                val nameOnly = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fullNewFileName)
                }
                updated = context.contentResolver.update(uri, nameOnly, null, null)
            }

            if (updated > 0 || directFileSuccess) {
                // Scan the file to guarantee media caches update
                try {
                    context.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val path = c.getString(0)
                            if (!path.isNullOrBlank()) {
                                MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                            }
                        }
                    }
                } catch (e: Exception) {}

                FileOpResult.Success
            } else {
                FileOpResult.Error("Gagal mengubah nama video.")
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                FileOpResult.RequiresConsent(pendingIntent.intentSender)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                FileOpResult.RequiresConsent(e.userAction.actionIntent.intentSender)
            } else {
                FileOpResult.Error(e.localizedMessage ?: "Izin tidak mencukupi untuk mengubah nama file.")
            }
        } catch (e: Exception) {
            FileOpResult.Error(e.localizedMessage ?: "Terjadi kesalahan saat mengubah nama video.")
        }
    }

    suspend fun deleteProgress(videoUri: String) = withContext(Dispatchers.IO) {
        try {
            progressDao.deleteProgress(videoUri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteProgressList(videoUris: Collection<String>) = withContext(Dispatchers.IO) {
        try {
            progressDao.deleteProgressList(videoUris.toList())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

