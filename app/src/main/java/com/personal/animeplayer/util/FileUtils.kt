package com.personal.animeplayer.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
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

    fun getDisplayTitle(fileName: String): String {
        return decodePercentEncoded(getBaseName(fileName))
    }

    /**
     * Safely decodes UTF-8 percent-encoded sequences (e.g. `%20`, `%E3%80%8C`) in browser-downloaded
     * filenames without turning `+` into space (preserving `HDR10+`) and without crashing on literal
     * `%` signs (`100%`) or browser-truncated trailing escapes (`...%E`).
     */
    fun decodePercentEncoded(raw: String): String {
        if ('%' !in raw) return raw

        val sb = StringBuilder(raw.length)
        val byteBuffer = ByteArrayOutputStream()
        var hadValidEscape = false
        var i = 0
        val len = raw.length

        fun flushBytes(isAtEnd: Boolean) {
            if (byteBuffer.size() == 0) return
            val bytes = byteBuffer.toByteArray()
            byteBuffer.reset()
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()
            if (isAtEnd && decoded.endsWith('\uFFFD')) {
                sb.append(decoded.dropLastWhile { it == '\uFFFD' })
            } else {
                sb.append(decoded)
            }
        }

        while (i < len) {
            val ch = raw[i]
            if (ch == '%' && i + 2 < len) {
                val hi = Character.digit(raw[i + 1], 16)
                val lo = Character.digit(raw[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    hadValidEscape = true
                    byteBuffer.write((hi shl 4) or lo)
                    i += 3
                    continue
                }
            }
            flushBytes(isAtEnd = false)
            if (ch == '%' && hadValidEscape) {
                val remaining = len - i
                if (remaining == 1 || (remaining == 2 && Character.digit(raw[i + 1], 16) >= 0)) {
                    break
                }
            }
            sb.append(ch)
            i++
        }
        flushBytes(isAtEnd = true)

        val result = sb.toString().trim()
        return if (result.isNotEmpty()) result else raw
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
