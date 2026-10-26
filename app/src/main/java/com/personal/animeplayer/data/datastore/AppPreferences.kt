package com.personal.animeplayer.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personal.animeplayer.data.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aniplay_preferences")

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_ROOT_FOLDER_URI = stringPreferencesKey("root_folder_uri")
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        val KEY_SUBTITLE_SCALE = intPreferencesKey("sub_scale_percent")
        val KEY_SUBTITLE_FONT_SIZE = floatPreferencesKey("sub_font_size")
        val KEY_SUBTITLE_DELAY_MS = longPreferencesKey("sub_delay_ms")
        val KEY_DUAL_SUB_ENABLED = booleanPreferencesKey("dual_sub_enabled")
        val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val KEY_SAVED_BRIGHTNESS = floatPreferencesKey("saved_brightness")
        val KEY_LOOP_ENABLED = booleanPreferencesKey("loop_enabled")
        val KEY_SKIP_OP_ED_ENABLED = booleanPreferencesKey("skip_op_ed_enabled")
        val KEY_LAST_WATCHED_URI = stringPreferencesKey("last_watched_uri")
        val KEY_PLAYER_ENGINE = stringPreferencesKey("player_engine")
        val KEY_DECODER_MODE = stringPreferencesKey("video_decoder_mode")
        val KEY_SONG_DETECTION_MODE = booleanPreferencesKey("song_detection_mode")
    }

    val rootFolderUri: Flow<String?> = context.dataStore.data.map { it[KEY_ROOT_FOLDER_URI] }

    val sortOrder: Flow<SortOrder> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_SORT_ORDER] ?: SortOrder.NAME.name
        try {
            SortOrder.valueOf(name)
        } catch (e: Exception) {
            SortOrder.NAME
        }
    }

    val subtitleScale: Flow<Int> = context.dataStore.data.map { it[KEY_SUBTITLE_SCALE] ?: 100 }
    val subtitleFontSize: Flow<Float> = context.dataStore.data.map { it[KEY_SUBTITLE_FONT_SIZE] ?: 21f }
    val subtitleDelayMs: Flow<Long> = context.dataStore.data.map { it[KEY_SUBTITLE_DELAY_MS] ?: 0L }
    val isDualSubEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_DUAL_SUB_ENABLED] ?: false }
    val playbackSpeed: Flow<Float> = context.dataStore.data.map { it[KEY_PLAYBACK_SPEED] ?: 1.0f }
    val savedBrightness: Flow<Float> = context.dataStore.data.map { it[KEY_SAVED_BRIGHTNESS] ?: -1f }
    val isLoopEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOOP_ENABLED] ?: false }
    val isSkipOpEdEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SKIP_OP_ED_ENABLED] ?: true }
    val lastWatchedUri: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_WATCHED_URI] }
    val playerEngine: Flow<String> = context.dataStore.data.map { it[KEY_PLAYER_ENGINE] ?: "auto" }
    val decoderMode: Flow<String> = context.dataStore.data.map { it[KEY_DECODER_MODE] ?: "mediacodec" }
    val isSongDetectionEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SONG_DETECTION_MODE] ?: true }

    suspend fun setRootFolderUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) prefs[KEY_ROOT_FOLDER_URI] = uri else prefs.remove(KEY_ROOT_FOLDER_URI)
        }
    }

    suspend fun setSortOrder(order: SortOrder) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SORT_ORDER] = order.name
        }
    }

    suspend fun setSubtitleScale(scale: Int) {
        context.dataStore.edit { it[KEY_SUBTITLE_SCALE] = scale }
    }

    suspend fun setSubtitleFontSize(size: Float) {
        context.dataStore.edit { it[KEY_SUBTITLE_FONT_SIZE] = size }
    }

    suspend fun setSubtitleDelayMs(delayMs: Long) {
        context.dataStore.edit { it[KEY_SUBTITLE_DELAY_MS] = delayMs }
    }

    suspend fun setDualSubEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DUAL_SUB_ENABLED] = enabled }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[KEY_PLAYBACK_SPEED] = speed }
    }

    suspend fun setSavedBrightness(brightness: Float) {
        context.dataStore.edit { it[KEY_SAVED_BRIGHTNESS] = brightness }
    }

    suspend fun setLoopEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LOOP_ENABLED] = enabled }
    }

    suspend fun setSkipOpEdEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SKIP_OP_ED_ENABLED] = enabled }
    }

    suspend fun setLastWatchedUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri != null) {
                prefs[KEY_LAST_WATCHED_URI] = uri
            } else {
                prefs.remove(KEY_LAST_WATCHED_URI)
            }
        }
    }

    suspend fun setPlayerEngine(engine: String) {
        context.dataStore.edit { it[KEY_PLAYER_ENGINE] = engine }
    }

    suspend fun setDecoderMode(mode: String) {
        context.dataStore.edit { it[KEY_DECODER_MODE] = mode }
    }

    suspend fun setSongDetectionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SONG_DETECTION_MODE] = enabled }
    }
}
