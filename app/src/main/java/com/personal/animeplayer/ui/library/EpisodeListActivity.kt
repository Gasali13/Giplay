package com.personal.animeplayer.ui.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.personal.animeplayer.R
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.FileOpResult
import com.personal.animeplayer.data.model.SortOrder
import com.personal.animeplayer.player.PlaylistHolder
import com.personal.animeplayer.ui.compose.theme.AniPlayTheme
import com.personal.animeplayer.ui.player.PlayerActivity
import kotlinx.coroutines.launch

class EpisodeListActivity : AppCompatActivity() {

    private val viewModel: EpisodeViewModel by viewModels()

    private var seriesFolderUri: Uri? = null
    private var seriesName: String = ""
    private var pendingRenameAction: Pair<Episode, String>? = null
    private var pendingDeleteUris: List<String>? = null
    private var isFirstResume: Boolean = true

    private var inMemoryLastPlayedUri by mutableStateOf<String?>(null)
    private var resumeTick by mutableIntStateOf(0)

    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val folderUri = seriesFolderUri
        if (result.resultCode == RESULT_OK) {
            val pendingRename = pendingRenameAction
            val pendingDelete = pendingDeleteUris
            pendingRenameAction = null
            pendingDeleteUris = null
            if (pendingRename != null && folderUri != null) {
                executeRename(pendingRename.first, pendingRename.second, folderUri)
            } else if (pendingDelete != null) {
                viewModel.onVideosDeleted(pendingDelete)
            }
        } else {
            pendingRenameAction = null
            pendingDeleteUris = null
            Toast.makeText(this, "Izin ditolak oleh pengguna", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val uriStr = intent.getStringExtra(EXTRA_SERIES_URI)
        seriesName = intent.getStringExtra(EXTRA_SERIES_NAME)
            ?.let { com.personal.animeplayer.util.FileUtils.decodePercentEncoded(it) }
            ?: "Anime Series"
        val folderUri = uriStr?.let { Uri.parse(it) }
        if (folderUri == null) {
            Toast.makeText(this, "Folder series tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        seriesFolderUri = folderUri
        inMemoryLastPlayedUri = PlaylistHolder.lastPlayedUri
        viewModel.loadEpisodes(folderUri)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
            val sortOrder by viewModel.sortOrderFlow.collectAsStateWithLifecycle(
                initialValue = viewModel.sortOrder
            )
            val persistedLastWatchedUri by viewModel.lastWatchedUri.collectAsStateWithLifecycle(
                initialValue = null
            )

            AniPlayTheme {
                EpisodeListScreen(
                    seriesName = seriesName,
                    uiState = uiState,
                    isRefreshing = isRefreshing,
                    sortOrder = sortOrder,
                    lastWatchedUri = inMemoryLastPlayedUri ?: persistedLastWatchedUri,
                    resumeTick = resumeTick,
                    onBack = { finish() },
                    onRefresh = {
                        seriesFolderUri?.let { viewModel.loadEpisodes(it, isRefresh = true) }
                    },
                    onSearchQueryChange = viewModel::searchEpisodes,
                    onSortOrderSelected = viewModel::setSortOrder,
                    onEpisodeClick = { episode, currentList ->
                        PlaylistHolder.setPlaylist(currentList, episode.seriesFolderUri)
                        PlayerActivity.start(
                            context = this@EpisodeListActivity,
                            videoUri = Uri.parse(episode.uri),
                            title = episode.title,
                            seriesFolderUri = episode.seriesFolderUri
                        )
                    },
                    onShareEpisode = ::shareEpisode,
                    onFetchVideoDetails = viewModel::getVideoDetails,
                    onRenameEpisode = { episode, newName ->
                        seriesFolderUri?.let { executeRename(episode, newName, it) }
                    },
                    onDeleteEpisode = ::executeDeleteSingle,
                    onDeleteEpisodes = ::executeDeleteBatch
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        inMemoryLastPlayedUri = PlaylistHolder.lastPlayedUri
        resumeTick++
        if (isFirstResume) {
            isFirstResume = false
        } else {
            seriesFolderUri?.let { viewModel.loadEpisodes(it, isRefresh = true) }
        }
    }

    private fun shareEpisode(episode: Episode) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(episode.uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Bagikan Video"))
        } catch (_: Exception) {
            Toast.makeText(this, "Tidak dapat membagikan video", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeDeleteSingle(episode: Episode) {
        pendingRenameAction = null
        pendingDeleteUris = listOf(episode.uri)
        lifecycleScope.launch {
            when (val res = viewModel.deleteVideo(episode)) {
                is FileOpResult.Success -> {
                    pendingDeleteUris = null
                }
                is FileOpResult.RequiresConsent -> {
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(res.intentSender).build()
                    )
                }
                is FileOpResult.Error -> {
                    pendingDeleteUris = null
                    Toast.makeText(this@EpisodeListActivity, res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun executeDeleteBatch(episodes: List<Episode>) {
        if (episodes.isEmpty()) return
        pendingRenameAction = null
        pendingDeleteUris = episodes.map { it.uri }
        lifecycleScope.launch {
            when (val res = viewModel.deleteVideos(episodes)) {
                is FileOpResult.Success -> {
                    pendingDeleteUris = null
                }
                is FileOpResult.RequiresConsent -> {
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(res.intentSender).build()
                    )
                }
                is FileOpResult.Error -> {
                    pendingDeleteUris = null
                    Toast.makeText(this@EpisodeListActivity, res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun executeRename(episode: Episode, newName: String, folderUri: Uri) {
        lifecycleScope.launch {
            when (val res = viewModel.renameVideo(episode, newName, folderUri)) {
                is FileOpResult.Success -> {
                    pendingRenameAction = null
                    Toast.makeText(
                        this@EpisodeListActivity,
                        "Nama video berhasil diubah",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is FileOpResult.RequiresConsent -> {
                    pendingRenameAction = episode to newName
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(res.intentSender).build()
                    )
                }
                is FileOpResult.Error -> {
                    pendingRenameAction = null
                    Toast.makeText(this@EpisodeListActivity, res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.folder_close_enter,
                R.anim.folder_close_exit
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                R.anim.folder_close_enter,
                R.anim.folder_close_exit
            )
        }
    }

    companion object {
        private const val EXTRA_SERIES_URI = "extra_series_uri"
        private const val EXTRA_SERIES_NAME = "extra_series_name"

        fun start(context: Context, seriesUri: Uri, seriesName: String) {
            val intent = Intent(context, EpisodeListActivity::class.java).apply {
                putExtra(EXTRA_SERIES_URI, seriesUri.toString())
                putExtra(EXTRA_SERIES_NAME, seriesName)
            }
            val options = ActivityOptionsCompat.makeCustomAnimation(
                context,
                R.anim.folder_open_enter,
                R.anim.folder_open_exit
            )
            context.startActivity(intent, options.toBundle())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                (context as? Activity)?.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.folder_open_enter,
                    R.anim.folder_open_exit
                )
            } else {
                @Suppress("DEPRECATION")
                (context as? Activity)?.overridePendingTransition(
                    R.anim.folder_open_enter,
                    R.anim.folder_open_exit
                )
            }
        }
    }
}
