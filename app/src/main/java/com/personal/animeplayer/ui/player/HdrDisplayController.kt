package com.personal.animeplayer.ui.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.Window
import com.personal.animeplayer.player.PlaylistHolder

/**
 * Manages HDR/HLG/Dolby Vision window color modes, headroom, badge display,
 * temporary HDR brightness boosting, and heuristic HDR/camera source detection.
 */
class HdrDisplayController(
    private val context: Context,
    private val window: Window,
    private val onHdrBadgeChanged: (isHdr: Boolean, hdrType: String) -> Unit,
    private val getSavedBrightness: () -> Float
) {
    var isHdrActive: Boolean = false
        private set

    var previousSdrBrightness: Float = -1f
        private set

    fun applyHdrState(isHdr: Boolean, hdrType: String) {
        isHdrActive = isHdr
        onHdrBadgeChanged(isHdr, hdrType)
        if (isHdr) {
            // 1. Enable HDR color mode on Window (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.colorMode = ActivityInfo.COLOR_MODE_HDR
            }

            // 2. Set maximum HDR headroom for Android 15+ (API 35+) to enable full AMOLED HDR luminance
            if (Build.VERSION.SDK_INT >= 35) {
                window.setDesiredHdrHeadroom(10000.0f)
            }

            // 3. Auto-boost brightness to 100% for HDR
            val lp = window.attributes
            if (previousSdrBrightness < 0f) {
                val current = lp.screenBrightness
                previousSdrBrightness = if (current in 0.01f..1.0f) {
                    current
                } else {
                    val saved = getSavedBrightness()
                    if (saved in 0.01f..1.0f) saved else 0.5f
                }
            }
            lp.screenBrightness = 1.0f
            window.attributes = lp
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            }
            if (Build.VERSION.SDK_INT >= 35) {
                window.setDesiredHdrHeadroom(0.0f)
            }

            // Restore previous SDR brightness if it was boosted
            if (previousSdrBrightness > 0f) {
                val lp = window.attributes
                lp.screenBrightness = previousSdrBrightness
                window.attributes = lp
                previousSdrBrightness = -1f
            }
        }
    }

    fun applySavedSdrBrightnessIfNeeded(savedBrightness: Float) {
        if (!isHdrActive && savedBrightness in 0.01f..1.0f) {
            val lp = window.attributes
            if (lp.screenBrightness != savedBrightness) {
                lp.screenBrightness = savedBrightness
                window.attributes = lp
            }
        }
    }

    fun getBrightnessToPersist(): Float? {
        val candidate = if (isHdrActive && previousSdrBrightness in 0.01f..1.0f) {
            previousSdrBrightness
        } else {
            window.attributes.screenBrightness
        }
        return candidate.takeIf { it in 0.01f..1.0f }
    }

    fun resetOnDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }
        if (Build.VERSION.SDK_INT >= 35) {
            window.setDesiredHdrHeadroom(0.0f)
        }
    }

    fun isLikelyHdrOrCamera(uri: Uri?, title: String): Boolean {
        if (uri == null) return false
        val titleLower = title.lowercase()
        val uriStrLower = uri.toString().lowercase()
        val pathLower = (uri.path ?: "").lowercase()
        val lastSegmentLower = (uri.lastPathSegment ?: "").lowercase()

        val playlistItem = PlaylistHolder.currentPlaylist.find { it.uri == uri.toString() }
        val playlistFileName = playlistItem?.fileName?.lowercase() ?: ""
        val playlistTitle = playlistItem?.title?.lowercase() ?: ""

        var contentDisplayName = ""
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        contentDisplayName = cursor.getString(0)?.lowercase() ?: ""
                    }
                }
            } catch (_: Exception) {
            }
        }

        return isLikelyHdrOrCameraNames(
            listOf(
                titleLower,
                playlistTitle,
                playlistFileName,
                contentDisplayName,
                uriStrLower,
                pathLower,
                lastSegmentLower
            )
        )
    }

    companion object {
        internal val REGEX_HDR_KEYWORDS = Regex(
            """(?i)(?:^|[^a-z0-9])(hdr10\+|hdr10plus|hdr10|hdr|dolby[\s._-]*vision|dovi|dv|hlg|st2084|pq)(?:$|[^a-z0-9])"""
        )

        private val CAMERA_KEYWORDS = listOf("/dcim/", "/camera/", "vid_")

        internal fun isLikelyHdrOrCameraNames(names: Iterable<String>): Boolean {
            if (names.any { it.isNotBlank() && REGEX_HDR_KEYWORDS.containsMatchIn(it) }) {
                return true
            }
            return names.any { name ->
                val lower = name.lowercase()
                CAMERA_KEYWORDS.any { kw -> lower.contains(kw) }
            }
        }
    }
}
