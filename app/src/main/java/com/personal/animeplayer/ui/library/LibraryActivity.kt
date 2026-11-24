package com.personal.animeplayer.ui.library

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.FileOpResult
import com.personal.animeplayer.player.PlaylistHolder
import com.personal.animeplayer.ui.compose.theme.AniPlayTheme
import com.personal.animeplayer.ui.player.PlayerActivity
import kotlinx.coroutines.launch

class LibraryActivity : AppCompatActivity() {

    private val viewModel: LibraryViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        refreshMediaAccessFlag()
        val grantedNow = permissions.values.any { it } || hasMediaReadPermission()

        if (grantedNow) {
            if (viewModel.state.value.hasScanCompleted) {
                viewModel.refreshLibrary()
            } else {
                viewModel.ensureInitialLoad()
            }
        } else {
            viewModel.markPermissionDenied()
            Toast.makeText(
                this,
                "Izin akses video diperlukan untuk mendeteksi semua video",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private var pendingRenameAction: Pair<Episode, String>? = null
    private var pendingDeleteUris: List<String>? = null

    private var showSplash by mutableStateOf(false)
    private var isPartialMediaAccess by mutableStateOf(false)
    private var inMemoryLastPlayedUri by mutableStateOf<String?>(null)
    private var resumeTick by mutableIntStateOf(0)

    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val pendingRename = pendingRenameAction
            val pendingDelete = pendingDeleteUris
            pendingRenameAction = null
            pendingDeleteUris = null
            if (pendingRename != null) {
                executeRename(pendingRename.first, pendingRename.second)
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

        if (!hasShownSplash && savedInstanceState == null) {
            hasShownSplash = true
            showSplash = true
        }
        inMemoryLastPlayedUri = PlaylistHolder.lastPlayedUri

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val persistedLastWatchedUri by viewModel.lastWatchedUri.collectAsStateWithLifecycle(
                initialValue = null
            )

            AniPlayTheme {
                LibraryScreen(
                    state = state,
                    lastWatchedUri = inMemoryLastPlayedUri ?: persistedLastWatchedUri,
                    resumeTick = resumeTick,
                    showSplash = showSplash,
                    onSplashFinished = { showSplash = false },
                    onExitApp = { finish() },
                    onTabSelected = viewModel::setTab,
                    onRefresh = viewModel::refreshLibrary,
                    onRescanClick = {
                        if (isPartialMediaAccess) {
                            requestAdditionalMediaSelection()
                        } else {
                            checkAndRequestPermissions(forceRefresh = true)
                        }
                    },
                    onSearchQueryChange = viewModel::search,
                    onSortOrderSelected = viewModel::setSortOrder,
                    onClearError = viewModel::clearErrorMessage,
                    onVideoClick = { episode, currentVideos ->
                        PlaylistHolder.setPlaylist(
                            currentVideos,
                            "mediastore://folder/all"
                        )
                        PlayerActivity.start(
                            context = this@LibraryActivity,
                            videoUri = Uri.parse(episode.uri),
                            title = episode.title,
                            seriesFolderUri = "mediastore://folder/all"
                        )
                    },
                    onFolderClick = { series ->
                        EpisodeListActivity.start(
                            this@LibraryActivity,
                            Uri.parse(series.folderUri),
                            series.name
                        )
                    },
                    onShareEpisode = ::shareEpisode,
                    onFetchVideoDetails = viewModel::getVideoDetails,
                    onRenameEpisode = ::executeRename,
                    onDeleteEpisode = ::executeDeleteSingle,
                    onDeleteEpisodes = ::executeDeleteBatch,
                    isPartialMediaAccess = isPartialMediaAccess,
                    onManageMediaAccessClick = ::requestAdditionalMediaSelection
                )
            }
        }

        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        val wasPartial = isPartialMediaAccess
        refreshMediaAccessFlag()
        if (wasPartial && !isPartialMediaAccess && hasMediaReadPermission() && viewModel.state.value.hasScanCompleted) {
            viewModel.refreshLibrary()
        }
        inMemoryLastPlayedUri = PlaylistHolder.lastPlayedUri
        resumeTick++
        viewModel.refreshWatchProgress()
    }

    private fun hasMediaReadPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    private fun refreshMediaAccessFlag() {
        isPartialMediaAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VIDEO
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAdditionalMediaSelection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            )
        } else {
            checkAndRequestPermissions(forceRefresh = true)
        }
    }

    private fun checkAndRequestPermissions(forceRefresh: Boolean = false) {
        refreshMediaAccessFlag()
        if (hasMediaReadPermission()) {
            if (forceRefresh && viewModel.state.value.hasScanCompleted) {
                viewModel.refreshLibrary()
            } else {
                viewModel.ensureInitialLoad()
            }
        } else {
            val permissionsToRequest = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                }
                else -> {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            requestPermissionLauncher.launch(permissionsToRequest)
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
                    Toast.makeText(this@LibraryActivity, res.message, Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this@LibraryActivity, res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun executeRename(episode: Episode, newName: String) {
        lifecycleScope.launch {
            when (val res = viewModel.renameVideo(episode, newName)) {
                is FileOpResult.Success -> {
                    pendingRenameAction = null
                    Toast.makeText(
                        this@LibraryActivity,
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
                    Toast.makeText(this@LibraryActivity, res.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private var hasShownSplash = false
    }
}
