package com.personal.animeplayer.util

import java.util.Locale

object FileUtils {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "webm", "mov", "flv", "ts", "m2ts", "3gp", "wmv", "ogv"
    )

    private val SUBTITLE_EXTENSIONS = setOf(
        "ass", "ssa", "srt", "vtt", "sub"
    )

    fun isSupportedVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in VIDEO_EXTENSIONS
    }

    fun isSupportedSubtitleFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in SUBTITLE_EXTENSIONS
    }

    fun getBaseName(fileName: String): String {
        return fileName.substringBeforeLast('.')
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "00:00"
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun formatBadgeDuration(durationMs: Long): String {
        if (durationMs <= 0) return "00.00"
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d.%02d.%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d.%02d", minutes, seconds)
        }
    }

    fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val kb = sizeInBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.getDefault(), "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            else -> String.format(Locale.getDefault(), "%.0f KB", kb)
        }
    }
}
