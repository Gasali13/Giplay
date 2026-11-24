package com.personal.animeplayer.ui.library

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.animeplayer.R
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.SortOrder
import com.personal.animeplayer.data.model.VideoDetailInfo
import com.personal.animeplayer.player.PlaylistHolder
import com.personal.animeplayer.ui.compose.components.AlphabetIndexHelper
import com.personal.animeplayer.ui.compose.components.AlphabetIndexOverlay
import com.personal.animeplayer.ui.compose.components.DeleteConfirmDialog
import com.personal.animeplayer.ui.compose.components.EpisodeListItem
import com.personal.animeplayer.ui.compose.components.EpisodeOptionsBottomSheet
import com.personal.animeplayer.ui.compose.components.LiquidGlassCircleButton
import com.personal.animeplayer.ui.compose.components.RenameVideoDialog
import com.personal.animeplayer.ui.compose.components.ScrollToTopFab
import com.personal.animeplayer.ui.compose.components.SearchCapsuleBar
import com.personal.animeplayer.ui.compose.components.SortOptionsBottomSheet
import com.personal.animeplayer.ui.compose.components.VideoInfoDialog
import com.personal.animeplayer.ui.compose.components.liquidGlassBackdrop
import com.personal.animeplayer.ui.compose.components.liquidGlassHeaderBlur
import com.personal.animeplayer.ui.compose.components.rememberLiquidGlassBackdropState
import com.personal.animeplayer.ui.compose.theme.AniPlayColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListScreen(
    seriesName: String,
    uiState: EpisodeUiState,
    isRefreshing: Boolean,
    sortOrder: SortOrder,
    lastWatchedUri: String?,
    resumeTick: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderSelected: (SortOrder) -> Unit,
    onEpisodeClick: (Episode, List<Episode>) -> Unit,
    onShareEpisode: (Episode) -> Unit,
    onFetchVideoDetails: suspend (Episode) -> VideoDetailInfo,
    onRenameEpisode: (Episode, String) -> Unit,
    onDeleteEpisode: (Episode) -> Unit,
    onDeleteEpisodes: (List<Episode>) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    val episodes = (uiState as? EpisodeUiState.Success)?.episodes.orEmpty()

    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showSortSheet by remember { mutableStateOf(false) }
    var optionsEpisode by remember { mutableStateOf<Episode?>(null) }
    var renameEpisode by remember { mutableStateOf<Episode?>(null) }
    var infoEpisode by remember { mutableStateOf<Episode?>(null) }
    var deleteSingleEpisode by remember { mutableStateOf<Episode?>(null) }
    var deleteBatchEpisodes by remember { mutableStateOf<List<Episode>?>(null) }

    val exitSelectionMode = {
        isSelectionMode = false
        selectedUris = emptySet()
    }

    val toggleEpisodeSelection: (Episode) -> Unit = { episode ->
        val updated = if (episode.uri in selectedUris) {
            selectedUris - episode.uri
        } else {
            selectedUris + episode.uri
        }
        if (updated.isEmpty()) {
            exitSelectionMode()
        } else {
            selectedUris = updated
        }
    }

    val scrollToTop: () -> Unit = {
        coroutineScope.launch {
            if (listState.firstVisibleItemIndex > 25) {
                listState.scrollToItem(25)
            }
            listState.animateScrollToItem(0)
        }
    }

    BackHandler {
        when {
            isSelectionMode -> exitSelectionMode()
            isSearchVisible -> {
                isSearchVisible = false
                if (searchQuery.isNotEmpty()) {
                    searchQuery = ""
                    onSearchQueryChange("")
                }
            }
            else -> onBack()
        }
    }

    // Reconcile selected items if underlying list changes
    LaunchedEffect(uiState) {
        if (uiState is EpisodeUiState.Error) {
            Toast.makeText(context, uiState.error, Toast.LENGTH_LONG).show()
        }
        if (isSelectionMode && uiState !is EpisodeUiState.Loading) {
            val validUris = episodes.map { it.uri }.toSet()
            val remaining = selectedUris.intersect(validUris)
            if (remaining.isEmpty()) {
                exitSelectionMode()
            } else if (remaining != selectedUris) {
                selectedUris = remaining
            }
        }
    }

    // Scroll to last played video when returning from PlayerActivity
    LaunchedEffect(resumeTick, episodes) {
        val targetUri = PlaylistHolder.lastPlayedUri ?: return@LaunchedEffect
        if (!PlaylistHolder.shouldScrollToLastPlayed) return@LaunchedEffect
        val targetIndex = episodes.indexOfFirst { it.uri == targetUri }
        if (targetIndex !in episodes.indices) return@LaunchedEffect

        PlaylistHolder.consumeShouldScroll()
        val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (targetIndex in visibleIndices) return@LaunchedEffect

        val firstVisible = listState.firstVisibleItemIndex
        val distance = abs(targetIndex - firstVisible)
        if (distance > 8) {
            val prePosition = (if (targetIndex > firstVisible) targetIndex - 3 else targetIndex + 3)
                .coerceIn(0, episodes.lastIndex)
            listState.scrollToItem(prePosition)
        }
        listState.animateScrollToItem(targetIndex)
    }

    // Alphabet index bar visibility & active letter sync
    val canShowAlphabet = sortOrder == SortOrder.NAME && episodes.isNotEmpty()
    var showAlphabetBar by remember { mutableStateOf(false) }
    var isDraggingAlphabet by remember { mutableStateOf(false) }
    var highlightedLetter by remember { mutableStateOf('#') }

    LaunchedEffect(canShowAlphabet) {
        if (!canShowAlphabet) {
            showAlphabetBar = false
            isDraggingAlphabet = false
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, episodes, isDraggingAlphabet, canShowAlphabet) {
        if (canShowAlphabet && !isDraggingAlphabet) {
            val idx = listState.firstVisibleItemIndex
            if (idx in episodes.indices) {
                highlightedLetter = AlphabetIndexHelper.getLetterForEpisode(episodes[idx])
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress, isDraggingAlphabet, canShowAlphabet) {
        if (!canShowAlphabet) {
            showAlphabetBar = false
            return@LaunchedEffect
        }
        if (listState.isScrollInProgress || isDraggingAlphabet) {
            showAlphabetBar = true
        } else if (showAlphabetBar) {
            delay(1200L)
            if (!isDraggingAlphabet && !listState.isScrollInProgress) {
                showAlphabetBar = false
            }
        }
    }

    val showScrollToTop by remember(episodes.size) {
        derivedStateOf {
            episodes.size > 12 && listState.firstVisibleItemIndex > 6
        }
    }

    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val backdropState = rememberLiquidGlassBackdropState {
        listState.firstVisibleItemIndex * 31 +
            listState.firstVisibleItemScrollOffset +
            (pullToRefreshState.distanceFraction * 1000f).toInt() * 5 +
            episodes.size * 13
    }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(LocalDensity.current) { headerHeightPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AniPlayColors.Background)
    ) {
        // 1. Full-screen Scrollable Backdrop Layer (captured for live frosted header blur & overlays)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AniPlayColors.Background)
                .liquidGlassBackdrop(backdropState)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                state = pullToRefreshState,
                indicator = {
                    Indicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = (headerHeightDp - 12.dp).coerceAtLeast(0.dp)),
                        isRefreshing = isRefreshing,
                        containerColor = AniPlayColors.RefreshContainer,
                        color = AniPlayColors.Secondary,
                        state = pullToRefreshState
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when (uiState) {
                    is EpisodeUiState.Loading -> {
                        if (!isRefreshing) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = AniPlayColors.Secondary)
                            }
                        }
                    }

                    is EpisodeUiState.Success -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(
                                top = (headerHeightDp - 10.dp).coerceAtLeast(0.dp),
                                bottom = navBottomPadding + 76.dp
                            ),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = uiState.episodes,
                                key = { it.uri }
                            ) { episode ->
                                EpisodeListItem(
                                    episode = episode,
                                    isLastWatched = episode.uri == lastWatchedUri,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = episode.uri in selectedUris,
                                    onClick = {
                                        if (isSelectionMode) {
                                            toggleEpisodeSelection(episode)
                                        } else {
                                            onEpisodeClick(episode, uiState.episodes)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedUris = setOf(episode.uri)
                                        } else {
                                            toggleEpisodeSelection(episode)
                                        }
                                    },
                                    onMoreClick = { optionsEpisode = episode }
                                )
                            }
                        }
                    }

                    is EpisodeUiState.Empty -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    start = 24.dp,
                                    top = headerHeightDp + 24.dp,
                                    end = 24.dp,
                                    bottom = 24.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.message,
                                color = AniPlayColors.OnSurfaceMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is EpisodeUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    start = 24.dp,
                                    top = headerHeightDp + 24.dp,
                                    end = 24.dp,
                                    bottom = 24.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.error,
                                color = AniPlayColors.OnSurfaceMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 2. Frosted Real-Time Blurred Top Header & Search Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .onSizeChanged { headerHeightPx = it.height }
                .liquidGlassHeaderBlur(
                    backdropState = backdropState,
                    blurRadius = 26.dp,
                    bottomFadeHeight = 44.dp,
                    bottomExtend = 16.dp
                )
                .statusBarsPadding()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 18.dp)
        ) {
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassCircleButton(
                        iconRes = R.drawable.ic_close,
                        contentDescription = stringResource(R.string.cd_close_selection),
                        onClick = exitSelectionMode,
                        size = 40.dp,
                        iconSize = 22.dp
                    )

                    Text(
                        text = "${selectedUris.size} dipilih",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 14.dp)
                    )

                    TextButton(
                        onClick = {
                            if (selectedUris.size == episodes.size) {
                                exitSelectionMode()
                            } else {
                                selectedUris = episodes.map { it.uri }.toSet()
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.action_select_all),
                            color = AniPlayColors.Secondary,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    LiquidGlassCircleButton(
                        iconRes = R.drawable.ic_delete,
                        contentDescription = stringResource(R.string.cd_delete_selected),
                        onClick = {
                            val selectedList = episodes.filter { it.uri in selectedUris }
                            if (selectedList.isNotEmpty()) {
                                deleteBatchEpisodes = selectedList
                            }
                        },
                        size = 40.dp,
                        iconSize = 22.dp,
                        iconTint = AniPlayColors.Danger,
                        tintColor = Color(0x523B1218)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassCircleButton(
                        iconRes = R.drawable.ic_arrow_back,
                        contentDescription = stringResource(R.string.action_back),
                        onClick = onBack,
                        size = 40.dp,
                        iconSize = 22.dp
                    )

                    Text(
                        text = seriesName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, end = 8.dp)
                            .clickable(onClick = scrollToTop)
                    )

                    LiquidGlassCircleButton(
                        iconRes = R.drawable.ic_search,
                        contentDescription = stringResource(R.string.search_hint),
                        onClick = {
                            val nextVisible = !isSearchVisible
                            isSearchVisible = nextVisible
                            if (!nextVisible && searchQuery.isNotEmpty()) {
                                searchQuery = ""
                                onSearchQueryChange("")
                            }
                        },
                        size = 40.dp,
                        iconSize = 22.dp,
                        iconTint = if (isSearchVisible) AniPlayColors.Secondary else Color.White
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    LiquidGlassCircleButton(
                        iconRes = R.drawable.ic_sort,
                        contentDescription = stringResource(R.string.sort_by),
                        onClick = { showSortSheet = true },
                        size = 40.dp,
                        iconSize = 22.dp
                    )
                }
            }

            SearchCapsuleBar(
                visible = isSearchVisible && !isSelectionMode,
                query = searchQuery,
                onQueryChange = { newQuery ->
                    searchQuery = newQuery
                    onSearchQueryChange(newQuery)
                },
                placeholder = stringResource(R.string.search_episode_hint)
            )
        }

        // 3. Floating Scroll-to-Top Liquid Glass Button
        ScrollToTopFab(
            visible = showScrollToTop,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                scrollToTop()
            },
            backdropState = backdropState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 24.dp)
        )

        // 4. Alphabet Fast-Scroll Bar & Lens Bubble Overlay
        if (canShowAlphabet) {
            AlphabetIndexOverlay(
                visible = showAlphabetBar,
                highlightedLetter = highlightedLetter,
                onLetterSelected = { letter ->
                    highlightedLetter = letter
                    val targetPos = AlphabetIndexHelper.findPositionForLetter(letter, episodes)
                    if (targetPos != -1) {
                        coroutineScope.launch {
                            listState.scrollToItem(targetPos)
                        }
                    }
                },
                onDragStateChanged = { dragging ->
                    isDraggingAlphabet = dragging
                },
                backdropState = backdropState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = (headerHeightDp - 12.dp).coerceAtLeast(0.dp))
                    .navigationBarsPadding()
            )
        }
        // 5. Floating Liquid Glass Sort Options Sheet
        SortOptionsBottomSheet(
            visible = showSortSheet,
            currentOrder = sortOrder,
            onDismiss = { showSortSheet = false },
            onApplyOrder = { selectedOrder ->
                coroutineScope.launch {
                    listState.scrollToItem(0)
                }
                onSortOrderSelected(selectedOrder)
            },
            backdropState = backdropState
        )

        // 6. Floating Liquid Glass Video Options Sheet (3-dot menu)
        EpisodeOptionsBottomSheet(
            episode = optionsEpisode,
            onDismiss = { optionsEpisode = null },
            onRename = { renameEpisode = it },
            onShare = { onShareEpisode(it) },
            onInfo = { infoEpisode = it },
            onDelete = { deleteSingleEpisode = it },
            backdropState = backdropState
        )

        // 7. Floating Liquid Glass Rename Video Dialog
        RenameVideoDialog(
            episode = renameEpisode,
            onDismiss = { renameEpisode = null },
            onConfirmRename = { ep, newName -> onRenameEpisode(ep, newName) },
            backdropState = backdropState
        )

        // 8. Floating Liquid Glass Video Info Dialog
        VideoInfoDialog(
            episode = infoEpisode,
            fetchDetails = onFetchVideoDetails,
            onDismiss = { infoEpisode = null },
            backdropState = backdropState
        )

        // 9. Floating Liquid Glass Delete Confirmation Dialogs
        val singleDeleteEp = deleteSingleEpisode
        DeleteConfirmDialog(
            visible = singleDeleteEp != null,
            title = "Hapus Video?",
            message = if (singleDeleteEp != null) {
                "Apakah Anda yakin ingin menghapus \"${singleDeleteEp.title}\"? Video akan dihapus secara permanen dari perangkat."
            } else {
                ""
            },
            onDismiss = { deleteSingleEpisode = null },
            onConfirm = { singleDeleteEp?.let(onDeleteEpisode) },
            backdropState = backdropState
        )

        val batchDeleteEps = deleteBatchEpisodes
        DeleteConfirmDialog(
            visible = batchDeleteEps != null,
            title = "Hapus ${batchDeleteEps?.size ?: 0} Video?",
            message = "Apakah Anda yakin ingin menghapus ${batchDeleteEps?.size ?: 0} video terpilih? Video akan dihapus secara permanen dari perangkat.",
            onDismiss = { deleteBatchEpisodes = null },
            onConfirm = {
                exitSelectionMode()
                batchDeleteEps?.let(onDeleteEpisodes)
            },
            backdropState = backdropState
        )
    }
}
