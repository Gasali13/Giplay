package com.personal.animeplayer.ui.library

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.animeplayer.R
import com.personal.animeplayer.data.model.AnimeSeries
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.SortOrder
import com.personal.animeplayer.data.model.VideoDetailInfo
import com.personal.animeplayer.player.PlaylistHolder
import com.personal.animeplayer.ui.compose.components.AlphabetIndexHelper
import com.personal.animeplayer.ui.compose.components.AlphabetIndexOverlay
import com.personal.animeplayer.ui.compose.components.CinematicSplashOverlay
import com.personal.animeplayer.ui.compose.components.DeleteConfirmDialog
import com.personal.animeplayer.ui.compose.components.EpisodeListItem
import com.personal.animeplayer.ui.compose.components.EpisodeOptionsBottomSheet
import com.personal.animeplayer.ui.compose.components.LiquidGlassCircleButton
import com.personal.animeplayer.ui.compose.components.RenameVideoDialog
import com.personal.animeplayer.ui.compose.components.ScrollToTopFab
import com.personal.animeplayer.ui.compose.components.SearchCapsuleBar
import com.personal.animeplayer.ui.compose.components.SegmentedTabSwitcher
import com.personal.animeplayer.ui.compose.components.SeriesListItem
import com.personal.animeplayer.ui.compose.components.SortOptionsBottomSheet
import com.personal.animeplayer.ui.compose.components.VideoInfoDialog
import com.personal.animeplayer.ui.compose.components.liquidGlassBackdrop
import com.personal.animeplayer.ui.compose.components.liquidGlassHeaderBlur
import com.personal.animeplayer.ui.compose.components.rememberLiquidGlassBackdropState
import com.personal.animeplayer.ui.compose.theme.AniPlayColors
import com.personal.animeplayer.ui.compose.theme.IosMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryState,
    lastWatchedUri: String?,
    resumeTick: Int,
    showSplash: Boolean,
    onSplashFinished: () -> Unit,
    onExitApp: () -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onRefresh: () -> Unit,
    onRescanClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderSelected: (SortOrder) -> Unit,
    onClearError: () -> Unit,
    onVideoClick: (Episode, List<Episode>) -> Unit,
    onFolderClick: (AnimeSeries) -> Unit,
    onShareEpisode: (Episode) -> Unit,
    onFetchVideoDetails: suspend (Episode) -> VideoDetailInfo,
    onRenameEpisode: (Episode, String) -> Unit,
    onDeleteEpisode: (Episode) -> Unit,
    onDeleteEpisodes: (List<Episode>) -> Unit,
    isPartialMediaAccess: Boolean = false,
    onManageMediaAccessClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val allVideosListState = rememberLazyListState()
    val foldersListState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

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

    val scrollToTopForTab: (LibraryTab) -> Unit = { tab ->
        coroutineScope.launch {
            val targetState = if (tab == LibraryTab.ALL_VIDEOS) allVideosListState else foldersListState
            if (targetState.firstVisibleItemIndex > 25) {
                targetState.scrollToItem(25)
            }
            targetState.animateScrollToItem(0)
        }
    }

    BackHandler(enabled = showSplash || isSelectionMode || isSearchVisible) {
        when {
            showSplash -> onExitApp()
            isSelectionMode -> exitSelectionMode()
            isSearchVisible -> {
                isSearchVisible = false
                if (searchQuery.isNotEmpty()) {
                    searchQuery = ""
                    onSearchQueryChange("")
                }
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            onClearError()
        }
    }

    LaunchedEffect(state.filteredVideos, state.hasScanCompleted) {
        if (isSelectionMode && state.hasScanCompleted) {
            val validUris = state.filteredVideos.map { it.uri }.toSet()
            val remaining = selectedUris.intersect(validUris)
            if (remaining.isEmpty()) {
                exitSelectionMode()
            } else if (remaining != selectedUris) {
                selectedUris = remaining
            }
        }
    }

    var lastSortOrder by remember { mutableStateOf(state.sortOrder) }
    var lastSearchQuery by remember { mutableStateOf(state.searchQuery) }
    LaunchedEffect(state.sortOrder, state.searchQuery) {
        val sortChanged = lastSortOrder != state.sortOrder
        val queryChanged = lastSearchQuery != state.searchQuery
        lastSortOrder = state.sortOrder
        lastSearchQuery = state.searchQuery
        if (sortChanged || queryChanged) {
            allVideosListState.scrollToItem(0)
            foldersListState.scrollToItem(0)
        }
    }

    LaunchedEffect(resumeTick, state.filteredVideos, state.currentTab) {
        if (state.currentTab != LibraryTab.ALL_VIDEOS) return@LaunchedEffect
        val targetUri = PlaylistHolder.lastPlayedUri ?: return@LaunchedEffect
        if (!PlaylistHolder.shouldScrollToLastPlayed) return@LaunchedEffect
        val targetIndex = state.filteredVideos.indexOfFirst { it.uri == targetUri }
        if (targetIndex !in state.filteredVideos.indices) return@LaunchedEffect

        PlaylistHolder.consumeShouldScroll()
        val visibleIndices = allVideosListState.layoutInfo.visibleItemsInfo.map { it.index }
        if (targetIndex in visibleIndices) return@LaunchedEffect

        val firstVisible = allVideosListState.firstVisibleItemIndex
        val distance = abs(targetIndex - firstVisible)
        if (distance > 8) {
            val prePos = (if (targetIndex > firstVisible) targetIndex - 3 else targetIndex + 3)
                .coerceIn(0, state.filteredVideos.lastIndex)
            allVideosListState.scrollToItem(prePos)
        }
        allVideosListState.animateScrollToItem(targetIndex)
    }

    val canShowAlphabet = state.hasScanCompleted &&
        state.currentTab == LibraryTab.ALL_VIDEOS &&
        state.sortOrder == SortOrder.NAME &&
        state.filteredVideos.isNotEmpty()

    var showAlphabetBar by remember { mutableStateOf(false) }
    var isDraggingAlphabet by remember { mutableStateOf(false) }
    var highlightedLetter by remember { mutableStateOf('#') }

    LaunchedEffect(canShowAlphabet) {
        if (!canShowAlphabet) {
            showAlphabetBar = false
            isDraggingAlphabet = false
        }
    }

    LaunchedEffect(
        allVideosListState.firstVisibleItemIndex,
        state.filteredVideos,
        isDraggingAlphabet,
        canShowAlphabet
    ) {
        if (canShowAlphabet && !isDraggingAlphabet) {
            val idx = allVideosListState.firstVisibleItemIndex
            if (idx in state.filteredVideos.indices) {
                highlightedLetter = AlphabetIndexHelper.getLetterForEpisode(state.filteredVideos[idx])
            }
        }
    }

    LaunchedEffect(allVideosListState.isScrollInProgress, isDraggingAlphabet, canShowAlphabet) {
        if (!canShowAlphabet) {
            showAlphabetBar = false
            return@LaunchedEffect
        }
        if (allVideosListState.isScrollInProgress || isDraggingAlphabet) {
            showAlphabetBar = true
        } else if (showAlphabetBar) {
            delay(1200L)
            if (!isDraggingAlphabet && !allVideosListState.isScrollInProgress) {
                showAlphabetBar = false
            }
        }
    }

    val currentState by rememberUpdatedState(state)
    val showScrollToTop by remember {
        derivedStateOf {
            if (currentState.currentTab == LibraryTab.ALL_VIDEOS) {
                currentState.filteredVideos.size > 12 && allVideosListState.firstVisibleItemIndex > 6
            } else {
                currentState.filteredFolders.size > 12 && foldersListState.firstVisibleItemIndex > 6
            }
        }
    }

    val pagerState = rememberPagerState(
        initialPage = if (state.currentTab == LibraryTab.FOLDERS) 1 else 0,
        pageCount = { 2 }
    )
    LaunchedEffect(state.currentTab) {
        val targetPage = if (state.currentTab == LibraryTab.FOLDERS) 1 else 0
        if (pagerState.currentPage != targetPage || pagerState.currentPageOffsetFraction != 0f) {
            pagerState.animateScrollToPage(
                page = targetPage,
                animationSpec = IosMotion.page
            )
        }
    }

    var isFoldersPrewarmed by remember { mutableStateOf(state.currentTab == LibraryTab.FOLDERS) }
    LaunchedEffect(state.hasScanCompleted, state.currentTab, showSplash) {
        if (state.currentTab == LibraryTab.FOLDERS || (state.hasScanCompleted && showSplash)) {
            isFoldersPrewarmed = true
        } else if (state.hasScanCompleted && !isFoldersPrewarmed) {
            delay(120L)
            isFoldersPrewarmed = true
        }
    }

    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val backdropState = rememberLiquidGlassBackdropState {
        val activeList = if (state.currentTab == LibraryTab.ALL_VIDEOS) {
            allVideosListState
        } else {
            foldersListState
        }
        activeList.firstVisibleItemIndex * 31 +
            activeList.firstVisibleItemScrollOffset +
            (pullToRefreshState.distanceFraction * 1000f).toInt() * 5 +
            pagerState.currentPage * 7 +
            (pagerState.currentPageOffsetFraction * 200f).toInt() * 11 +
            state.filteredVideos.size * 13 +
            state.filteredFolders.size * 17
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
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                state = pullToRefreshState,
                indicator = {
                    Indicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = (headerHeightDp - 12.dp).coerceAtLeast(0.dp)),
                        isRefreshing = state.isRefreshing,
                        containerColor = AniPlayColors.RefreshContainer,
                        color = AniPlayColors.Secondary,
                        state = pullToRefreshState
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) {
                if (!state.hasScanCompleted) {
                    if (!showSplash && !state.isRefreshing) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AniPlayColors.Secondary)
                        }
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = false,
                        key = { it },
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> {
                                if (state.filteredVideos.isNotEmpty()) {
                                    LazyColumn(
                                        state = allVideosListState,
                                        contentPadding = PaddingValues(
                                            top = (headerHeightDp - 10.dp).coerceAtLeast(0.dp),
                                            bottom = navBottomPadding + 76.dp
                                        ),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        if (isPartialMediaAccess) {
                                            item(key = "partial_media_access_banner") {
                                                PartialMediaAccessBanner(
                                                    onManageClick = onManageMediaAccessClick
                                                )
                                            }
                                        }
                                        items(
                                            items = state.filteredVideos,
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
                                                        onVideoClick(episode, state.filteredVideos)
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
                                } else {
                                    LibraryEmptyState(
                                        iconRes = R.drawable.ic_play_arrow,
                                        title = "Tidak Ada Video",
                                        description = when {
                                            state.searchQuery.isNotBlank() ->
                                                "Tidak ada video yang cocok dengan \"${state.searchQuery}\""
                                            isPartialMediaAccess ->
                                                "Akses video terbatas aktif. Pilih video tambahan atau izinkan akses penuh."
                                            else ->
                                                "Tidak ada video ditemukan di penyimpanan perangkat."
                                        },
                                        actionLabel = if (isPartialMediaAccess && state.searchQuery.isBlank()) {
                                            "Pilih Video"
                                        } else {
                                            stringResource(R.string.action_rescan)
                                        },
                                        onRescanClick = onRescanClick,
                                        topPadding = headerHeightDp
                                    )
                                }
                            }

                            1 -> {
                                if (!isFoldersPrewarmed) {
                                    Box(modifier = Modifier.fillMaxSize())
                                } else if (state.filteredFolders.isNotEmpty()) {
                                    LazyColumn(
                                        state = foldersListState,
                                        contentPadding = PaddingValues(
                                            top = (headerHeightDp - 10.dp).coerceAtLeast(0.dp),
                                            bottom = navBottomPadding + 76.dp
                                        ),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        if (isPartialMediaAccess) {
                                            item(key = "partial_media_access_folder_banner") {
                                                PartialMediaAccessBanner(
                                                    onManageClick = onManageMediaAccessClick
                                                )
                                            }
                                        }
                                        items(
                                            items = state.filteredFolders,
                                            key = { it.folderUri }
                                        ) { series ->
                                            SeriesListItem(
                                                series = series,
                                                onClick = { onFolderClick(series) }
                                            )
                                        }
                                    }
                                } else {
                                    LibraryEmptyState(
                                        iconRes = R.drawable.ic_folder,
                                        title = "Tidak Ada Folder",
                                        description = when {
                                            state.searchQuery.isNotBlank() ->
                                                "Tidak ada folder yang cocok dengan \"${state.searchQuery}\""
                                            isPartialMediaAccess ->
                                                "Akses video terbatas aktif. Pilih video tambahan atau izinkan akses penuh."
                                            else ->
                                                "Tidak ada folder video ditemukan."
                                        },
                                        actionLabel = if (isPartialMediaAccess && state.searchQuery.isBlank()) {
                                            "Pilih Video"
                                        } else {
                                            stringResource(R.string.action_rescan)
                                        },
                                        onRescanClick = onRescanClick,
                                        topPadding = headerHeightDp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Frosted Real-Time Blurred Top AppBar: Header + Search Bar + Segmented Tabs
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .onSizeChanged { headerHeightPx = it.height }
                .liquidGlassHeaderBlur(
                    backdropState = backdropState,
                    blurRadius = 26.dp,
                    bottomFadeHeight = 46.dp,
                    bottomExtend = 18.dp
                )
                .statusBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp)
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
                                if (selectedUris.size == state.filteredVideos.size) {
                                    exitSelectionMode()
                                } else {
                                    selectedUris = state.filteredVideos.map { it.uri }.toSet()
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
                                val selectedList = state.filteredVideos.filter { it.uri in selectedUris }
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
                        Text(
                            text = stringResource(R.string.app_name),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.weight(1f))

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

                        Spacer(modifier = Modifier.width(8.dp))

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
                    placeholder = stringResource(R.string.search_hint)
                )
            }

            if (!isSelectionMode) {
                SegmentedTabSwitcher(
                    selectedTab = state.currentTab,
                    onTabSelected = { tab ->
                        showAlphabetBar = false
                        isDraggingAlphabet = false
                        onTabSelected(tab)
                    },
                    onTabReselected = scrollToTopForTab
                )
            }
        }

        // 3. Floating Scroll-to-Top Liquid Glass Button
        ScrollToTopFab(
            visible = showScrollToTop,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                scrollToTopForTab(state.currentTab)
            },
            backdropState = backdropState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 24.dp)
        )

        // 4. Alphabet Fast-Scroll Bar & Lens Bubble Overlay
        val canMountAlphabet = state.hasScanCompleted &&
            state.sortOrder == SortOrder.NAME &&
            state.filteredVideos.isNotEmpty()
        if (canMountAlphabet) {
            AlphabetIndexOverlay(
                visible = canShowAlphabet && showAlphabetBar,
                highlightedLetter = highlightedLetter,
                onLetterSelected = { letter ->
                    highlightedLetter = letter
                    val targetPos = AlphabetIndexHelper.findPositionForLetter(
                        letter,
                        state.filteredVideos
                    )
                    if (targetPos != -1) {
                        coroutineScope.launch {
                            allVideosListState.scrollToItem(targetPos)
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
            currentOrder = state.sortOrder,
            onDismiss = { showSortSheet = false },
            onApplyOrder = { selectedOrder ->
                coroutineScope.launch {
                    allVideosListState.scrollToItem(0)
                    foldersListState.scrollToItem(0)
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

        // 10. Cinematic Splash Overlay on initial cold start
        if (showSplash) {
            CinematicSplashOverlay(
                loadingProgress = state.startupProgress,
                statusText = state.startupStatus,
                isDataReady = state.isStartupReady,
                onFinished = onSplashFinished
            )
        }
    }
}

@Composable
private fun PartialMediaAccessBanner(
    onManageClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(shape)
            .background(Color(0xFF161922))
            .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
            .clickable(onClick = onManageClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Akses video terbatas aktif",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Hanya video yang dipilih yang ditampilkan",
                color = AniPlayColors.OnSurfaceMuted,
                fontSize = 11.5.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Pilih Video",
            color = AniPlayColors.Secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LibraryEmptyState(
    @DrawableRes iconRes: Int,
    title: String,
    description: String,
    actionLabel: String = stringResource(R.string.action_rescan),
    onRescanClick: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 32.dp,
                top = topPadding + 32.dp,
                end = 32.dp,
                bottom = 32.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = AniPlayColors.Primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                color = AniPlayColors.OnSurfaceMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRescanClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AniPlayColors.Primary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_arrow),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = actionLabel)
            }
        }
    }
}
