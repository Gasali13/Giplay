package com.personal.animeplayer.ui.library

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.personal.animeplayer.R
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.FileOpResult
import com.personal.animeplayer.data.model.SortOrder
import com.personal.animeplayer.databinding.ActivityLibraryBinding
import com.personal.animeplayer.databinding.BottomSheetVideoOptionsBinding
import com.personal.animeplayer.databinding.DialogDeleteConfirmBinding
import com.personal.animeplayer.databinding.DialogRenameVideoBinding
import com.personal.animeplayer.databinding.DialogSortOptionsBinding
import com.personal.animeplayer.databinding.DialogVideoInfoBinding
import com.personal.animeplayer.player.PlaylistHolder
import com.personal.animeplayer.ui.player.PlayerActivity
import com.personal.animeplayer.util.FileUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private val viewModel: LibraryViewModel by viewModels()
    private lateinit var allVideosAdapter: EpisodeAdapter
    private lateinit var seriesAdapter: SeriesAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val videoGranted = permissions[Manifest.permission.READ_MEDIA_VIDEO] == true
        val partialGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
        } else false
        val storageGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true

        if (videoGranted || partialGranted || storageGranted) {
            viewModel.loadAllDeviceVideos()
        } else {
            Toast.makeText(
                this,
                "Izin akses video diperlukan untuk mendeteksi semua video",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private var pendingRenameAction: Pair<Episode, String>? = null
    private var pendingDeleteUris: List<String>? = null
    private var isSearchVisible: Boolean = false
    private var previousRenderedTab: LibraryTab? = null
    private var splashAnimators: MutableList<Animator> = mutableListOf()
    private var isSplashAnimationCompleted = false
    private var isInitialDataLoaded = false
    private var isSplashDismissed = false

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
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val extraBottomPadding = (76 * density).toInt()
            binding.appBar.setPadding(0, insets.top, 0, 0)
            binding.rvAllVideos.setPadding(
                binding.rvAllVideos.paddingLeft,
                binding.rvAllVideos.paddingTop,
                binding.rvAllVideos.paddingRight,
                insets.bottom + extraBottomPadding
            )
            binding.rvSeries.setPadding(
                binding.rvSeries.paddingLeft,
                binding.rvSeries.paddingTop,
                binding.rvSeries.paddingRight,
                insets.bottom + extraBottomPadding
            )
            systemBottomInset = insets.bottom
            updateAlphabetPosition()
            windowInsets
        }

        setupRecyclerViews()
        setupUIEvents()
        setupAlphabetIndex()
        observeViewModel()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.layoutSplashOverlay.visibility == View.VISIBLE && !isSplashDismissed) {
                    finish()
                    return
                }
                if (allVideosAdapter.isSelectionMode) {
                    allVideosAdapter.exitSelectionMode()
                } else if (isSearchVisible) {
                    toggleSearchBar(false)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        setupCinematicSplash(savedInstanceState)
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        PlaylistHolder.lastPlayedUri?.let { allVideosAdapter.setLastWatchedUri(it) }
        checkAndScrollToLastPlayed()
        viewModel.refreshWatchProgress()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, 0)
        if (initialTab in 0..1) {
            binding.tabLayout.getTabAt(initialTab)?.select()
            viewModel.setTab(if (initialTab == 1) LibraryTab.FOLDERS else LibraryTab.ALL_VIDEOS)
        }
        if (intent.getBooleanExtra("extra_open_sort", false)) {
            binding.root.postDelayed({ showSortDialog() }, 300)
        }
    }

    private fun checkAndRequestPermissions() {
        val hasPermission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }

        if (hasPermission) {
            viewModel.loadAllDeviceVideos()
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

    private fun setupRecyclerViews() {
        allVideosAdapter = EpisodeAdapter(
            scope = lifecycleScope,
            onEpisodeClicked = { episode ->
                PlaylistHolder.setPlaylist(
                    allVideosAdapter.currentList,
                    "mediastore://folder/all"
                )
                PlayerActivity.start(
                    context = this,
                    videoUri = Uri.parse(episode.uri),
                    title = episode.title,
                    seriesFolderUri = "mediastore://folder/all"
                )
            },
            onEpisodeDelete = { episode -> showDeleteConfirmDialog(episode) },
            onEpisodeRename = { episode -> showRenameDialog(episode) },
            onEpisodeInfo = { episode -> showVideoInfoDialog(episode) },
            onEpisodeMoreClicked = { episode -> showEpisodeBottomSheet(episode) }
        ).apply {
            onSelectionModeToggled = { active ->
                updateSelectionModeUi(active)
            }
            onSelectionChanged = { count ->
                binding.tvSelectionCount.text = "$count dipilih"
            }
        }
        binding.rvAllVideos.apply {
            layoutManager = LinearLayoutManager(this@LibraryActivity)
            adapter = allVideosAdapter
        }

        seriesAdapter = SeriesAdapter { series ->
            EpisodeListActivity.start(this, Uri.parse(series.folderUri), series.name)
        }
        binding.rvSeries.apply {
            layoutManager = LinearLayoutManager(this@LibraryActivity)
            adapter = seriesAdapter
        }
    }

    private fun setupUIEvents() {
        binding.btnCloseSelection.setOnClickListener {
            allVideosAdapter.exitSelectionMode()
        }

        binding.btnSelectAll.setOnClickListener {
            if (allVideosAdapter.selectedUris.size == allVideosAdapter.currentList.size) {
                allVideosAdapter.exitSelectionMode()
            } else {
                allVideosAdapter.selectAll()
            }
        }

        binding.btnDeleteSelected.setOnClickListener {
            val selected = allVideosAdapter.getSelectedEpisodes()
            if (selected.isNotEmpty()) {
                showBatchDeleteConfirmDialog(selected)
            }
        }

        val updateFabVisibility = {
            val isAllVideos = viewModel.state.value.currentTab == LibraryTab.ALL_VIDEOS
            val activeRv = if (isAllVideos) binding.rvAllVideos else binding.rvSeries
            val lm = activeRv.layoutManager as? LinearLayoutManager
            val firstPos = lm?.findFirstVisibleItemPosition() ?: 0
            val totalItems = activeRv.adapter?.itemCount ?: 0
            if (totalItems > 12 && firstPos > 6) {
                if (binding.fabScrollTop.visibility != View.VISIBLE) {
                    binding.fabScrollTop.alpha = 0f
                    binding.fabScrollTop.scaleX = 0.8f
                    binding.fabScrollTop.scaleY = 0.8f
                    binding.fabScrollTop.visibility = View.VISIBLE
                    binding.fabScrollTop.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
            } else {
                if (binding.fabScrollTop.visibility == View.VISIBLE) {
                    binding.fabScrollTop.animate()
                        .alpha(0f)
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .setDuration(200)
                        .withEndAction { binding.fabScrollTop.visibility = View.GONE }
                        .start()
                }
            }
        }

        binding.fabScrollTop.setOnClickListener {
            binding.fabScrollTop.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            binding.fabScrollTop.animate()
                .scaleX(0.82f)
                .scaleY(0.82f)
                .setDuration(90)
                .withEndAction {
                    binding.fabScrollTop.animate()
                        .scaleX(0f)
                        .scaleY(0f)
                        .alpha(0f)
                        .setDuration(220)
                        .withEndAction {
                            binding.fabScrollTop.visibility = View.GONE
                            binding.fabScrollTop.scaleX = 1f
                            binding.fabScrollTop.scaleY = 1f
                        }
                        .start()
                }
                .start()

            if (viewModel.state.value.currentTab == LibraryTab.ALL_VIDEOS) {
                smoothScrollToTop(binding.rvAllVideos)
            } else {
                smoothScrollToTop(binding.rvSeries)
            }
        }

        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateFabVisibility()
            }
        }

        binding.rvAllVideos.addOnScrollListener(scrollListener)
        binding.rvSeries.addOnScrollListener(scrollListener)

        binding.tabLayout.removeAllTabs()
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Semua Video").setIcon(R.drawable.ic_play_arrow))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Folder").setIcon(R.drawable.ic_folder))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                binding.tabLayout.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                binding.tabLayout.animate()
                    .scaleX(0.985f)
                    .scaleY(0.985f)
                    .setDuration(60)
                    .withEndAction {
                        binding.tabLayout.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(120)
                            .start()
                    }
                    .start()

                when (tab?.position) {
                    0 -> {
                        viewModel.setTab(LibraryTab.ALL_VIDEOS)
                        binding.alphabetIndexView.visibility = View.GONE
                        binding.tvAlphabetBubble.visibility = View.GONE
                    }
                    1 -> {
                        viewModel.setTab(LibraryTab.FOLDERS)
                        binding.alphabetIndexView.visibility = View.GONE
                        binding.tvAlphabetBubble.visibility = View.GONE
                    }
                }
                binding.root.post { updateFabVisibility() }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    smoothScrollToTop(binding.rvAllVideos)
                } else if (tab?.position == 1) {
                    smoothScrollToTop(binding.rvSeries)
                }
            }
        })

        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, 0)
        if (initialTab in 0..1) {
            binding.tabLayout.getTabAt(initialTab)?.select()
            if (initialTab == 1) viewModel.setTab(LibraryTab.FOLDERS)
        }

        binding.btnSort.setOnClickListener {
            showSortDialog()
        }

        if (intent.getBooleanExtra("extra_open_sort", false)) {
            binding.root.postDelayed({ showSortDialog() }, 300)
        }

        binding.btnSearch.setOnClickListener {
            toggleSearchBar(!isSearchVisible)
        }

        binding.btnScanAllEmpty.setOnClickListener {
            checkAndRequestPermissions()
        }

        binding.swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.secondary))
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#1A2035"))
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshLibrary()
        }

        binding.btnScanAllEmpty.setOnClickListener {
            checkAndRequestPermissions()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private var searchAnimator: ValueAnimator? = null

    private fun toggleSearchBar(show: Boolean) {
        if (isSearchVisible == show) return
        isSearchVisible = show
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager

        searchAnimator?.cancel()

        if (show) {
            binding.appBar.setExpanded(true, true)
            binding.tilSearch.visibility = View.VISIBLE
            binding.containerSearch.visibility = View.VISIBLE

            val widthSpec = View.MeasureSpec.makeMeasureSpec(
                binding.appBar.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels,
                View.MeasureSpec.AT_MOST
            )
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            binding.containerSearch.measure(widthSpec, heightSpec)
            val targetHeight = if (binding.containerSearch.measuredHeight > 0) {
                binding.containerSearch.measuredHeight
            } else {
                (58 * resources.displayMetrics.density).toInt()
            }

            binding.containerSearch.layoutParams.height = 0
            binding.containerSearch.requestLayout()

            binding.tilSearch.alpha = 0f
            binding.tilSearch.translationY = -targetHeight * 0.4f

            searchAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260
                interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    binding.containerSearch.layoutParams.height = (targetHeight * fraction).toInt()
                    binding.containerSearch.requestLayout()

                    binding.tilSearch.alpha = fraction
                    binding.tilSearch.translationY = -targetHeight * 0.4f * (1f - fraction)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.containerSearch.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        binding.containerSearch.requestLayout()
                        binding.tilSearch.alpha = 1f
                        binding.tilSearch.translationY = 0f
                    }
                })
                start()
            }

            binding.btnSearch.setColorFilter(ContextCompat.getColor(this, R.color.secondary))
            binding.btnSearch.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).withEndAction {
                binding.btnSearch.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }.start()
            binding.etSearch.requestFocus()
            imm?.showSoftInput(binding.etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } else {
            binding.btnSearch.setColorFilter(ContextCompat.getColor(this, R.color.white))

            val startHeight = binding.containerSearch.height.takeIf { it > 0 }
                ?: (58 * resources.displayMetrics.density).toInt()

            searchAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 220
                interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    binding.containerSearch.layoutParams.height = (startHeight * fraction).toInt()
                    binding.containerSearch.requestLayout()

                    binding.tilSearch.alpha = fraction
                    binding.tilSearch.translationY = -startHeight * 0.4f * (1f - fraction)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.containerSearch.visibility = View.GONE
                        binding.containerSearch.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        binding.containerSearch.requestLayout()
                        binding.tilSearch.alpha = 1f
                        binding.tilSearch.translationY = 0f
                    }
                })
                start()
            }

            binding.etSearch.setText("")
            imm?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
            binding.etSearch.clearFocus()
        }
    }

    private fun smoothScrollToTop(recyclerView: RecyclerView) {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstPos = lm.findFirstVisibleItemPosition()
        if (firstPos <= 0) {
            binding.appBar.setExpanded(true, true)
            return
        }

        binding.appBar.setExpanded(true, true)

        if (firstPos > 25) {
            recyclerView.scrollToPosition(25)
        }

        val smoothScroller = object : LinearSmoothScroller(this) {
            override fun getVerticalSnapPreference(): Int = SNAP_TO_START

            override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                return 35f / displayMetrics.densityDpi
            }

            override fun calculateTimeForDeceleration(dx: Int): Int {
                return (super.calculateTimeForDeceleration(dx) * 1.3f).toInt().coerceAtLeast(180)
            }
        }
        smoothScroller.targetPosition = 0
        lm.startSmoothScroll(smoothScroller)
    }

    private var systemBottomInset: Int = 0

    private fun updateAlphabetPosition() {
        binding.root.post {
            val lineY = binding.appBar.bottom.toFloat()
            val bottomY = (binding.root.height - systemBottomInset).toFloat()
            val availableHeight = bottomY - lineY
            if (availableHeight <= 0) return@post

            val barHeight = binding.alphabetIndexView.height.toFloat()
            if (barHeight <= 0) return@post
            val targetY = lineY + (availableHeight - barHeight) / 2f
            binding.alphabetIndexView.y = targetY
        }
    }

    private var isDraggingAlphabet: Boolean = false

    private val hideAlphabetBarRunnable = Runnable {
        if (!isDraggingAlphabet && binding.alphabetIndexView.visibility == View.VISIBLE) {
            binding.alphabetIndexView.animate()
                .alpha(0f)
                .setDuration(250)
                .withEndAction {
                    binding.alphabetIndexView.visibility = View.GONE
                }
                .start()
        }
    }

    private fun showAlphabetBar() {
        val isNameSort = viewModel.state.value.sortOrder == SortOrder.NAME
        val hasVideos = allVideosAdapter.currentList.isNotEmpty()
        val isAllVideosTab = viewModel.state.value.currentTab == LibraryTab.ALL_VIDEOS
        if (!isNameSort || !hasVideos || !isAllVideosTab) return

        binding.alphabetIndexView.removeCallbacks(hideAlphabetBarRunnable)
        if (binding.alphabetIndexView.visibility != View.VISIBLE || binding.alphabetIndexView.alpha < 1f) {
            updateAlphabetPosition()
            binding.alphabetIndexView.visibility = View.VISIBLE
            binding.alphabetIndexView.animate()
                .alpha(1f)
                .setDuration(150)
                .start()
        }
        scheduleHideAlphabetBar(1500L)
    }

    private fun scheduleHideAlphabetBar(delayMillis: Long = 1200L) {
        binding.alphabetIndexView.removeCallbacks(hideAlphabetBarRunnable)
        binding.alphabetIndexView.postDelayed(hideAlphabetBarRunnable, delayMillis)
    }

    private val hideBubbleRunnable = Runnable {
        binding.tvAlphabetBubble.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.tvAlphabetBubble.visibility = View.GONE
            }
            .start()
    }

    private fun showAlphabetBubble(letter: Char, letterIndex: Int) {
        binding.tvAlphabetBubble.removeCallbacks(hideBubbleRunnable)
        binding.tvAlphabetBubble.text = letter.toString()

        // Align bubble vertically directly next to the selected letter on the alphabet bar
        val letterCenterY = binding.alphabetIndexView.getLetterCenterY(letterIndex)
        val bubbleHeight = if (binding.tvAlphabetBubble.height > 0) {
            binding.tvAlphabetBubble.height.toFloat()
        } else {
            64f * resources.displayMetrics.density
        }
        binding.tvAlphabetBubble.y = binding.alphabetIndexView.y + letterCenterY - (bubbleHeight / 2f)

        if (binding.tvAlphabetBubble.visibility != View.VISIBLE) {
            binding.tvAlphabetBubble.alpha = 0f
            binding.tvAlphabetBubble.visibility = View.VISIBLE
            binding.tvAlphabetBubble.animate()
                .alpha(1f)
                .setDuration(120)
                .start()
        }
    }

    private fun scheduleHideAlphabetBubble(delayMillis: Long = 350L) {
        binding.tvAlphabetBubble.removeCallbacks(hideBubbleRunnable)
        binding.tvAlphabetBubble.postDelayed(hideBubbleRunnable, delayMillis)
    }

    private fun setupAlphabetIndex() {
        binding.appBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateAlphabetPosition()
        }
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateAlphabetPosition()
        }

        binding.alphabetIndexView.onLetterSelected = { letter, index ->
            isDraggingAlphabet = true
            binding.alphabetIndexView.removeCallbacks(hideAlphabetBarRunnable)
            binding.alphabetIndexView.alpha = 1f
            binding.alphabetIndexView.visibility = View.VISIBLE

            showAlphabetBubble(letter, index)

            val currentVideos = allVideosAdapter.currentList
            if (currentVideos.isNotEmpty()) {
                val targetPos = AlphabetIndexView.findPositionForLetter(letter, currentVideos)
                if (targetPos != -1) {
                    (binding.rvAllVideos.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(targetPos, 0)
                }
            }
        }

        binding.alphabetIndexView.onLetterReleased = {
            isDraggingAlphabet = false
            scheduleHideAlphabetBubble(300L)
            scheduleHideAlphabetBar(1200L)
        }

        binding.rvAllVideos.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scheduleHideAlphabetBar(1200L)
                } else {
                    showAlphabetBar()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dx != 0 || dy != 0) {
                    showAlphabetBar()
                    // Keep preview bubble hidden during normal scroll
                    if (binding.tvAlphabetBubble.visibility == View.VISIBLE) {
                        binding.tvAlphabetBubble.visibility = View.GONE
                    }
                    val lm = recyclerView.layoutManager as? LinearLayoutManager
                    val firstPos = lm?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
                    if (firstPos != RecyclerView.NO_POSITION && firstPos in allVideosAdapter.currentList.indices) {
                        val letter = AlphabetIndexView.getLetterForEpisode(allVideosAdapter.currentList[firstPos])
                        binding.alphabetIndexView.setHighlightedLetter(letter)
                    }
                }
            }
        })
    }

    private var previousSortOrder: SortOrder? = null
    private var previousSearchQuery: String? = null

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                if (state.isLoading || !state.hasScanCompleted) {
                    binding.progressLoading.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.GONE
                    return@collectLatest
                }

                binding.swipeRefreshLayout.isRefreshing = false
                binding.progressLoading.visibility = View.GONE

                // Update tab titles without count numbers
                binding.tabLayout.getTabAt(0)?.text = "Semua Video"
                binding.tabLayout.getTabAt(1)?.text = "Folder"

                val isSortChanged = previousSortOrder != null && previousSortOrder != state.sortOrder
                val isSearchChanged = previousSearchQuery != null && previousSearchQuery != state.searchQuery
                val shouldScrollToTop = isSortChanged || isSearchChanged

                previousSortOrder = state.sortOrder
                previousSearchQuery = state.searchQuery

                val isNameSort = state.sortOrder == SortOrder.NAME
                val canShowAlphabet = state.currentTab == LibraryTab.ALL_VIDEOS && isNameSort && state.filteredVideos.isNotEmpty()
                if (!canShowAlphabet) {
                    binding.alphabetIndexView.visibility = View.GONE
                    binding.tvAlphabetBubble.visibility = View.GONE
                }

                val tabChanged = previousRenderedTab != null && previousRenderedTab != state.currentTab
                val oldTab = previousRenderedTab
                previousRenderedTab = state.currentTab

                when (state.currentTab) {
                    LibraryTab.ALL_VIDEOS -> {
                        if (state.filteredVideos.isNotEmpty()) {
                            binding.layoutEmpty.visibility = View.GONE
                            if (tabChanged && oldTab == LibraryTab.FOLDERS) {
                                animateTabTransition(toAllVideos = true)
                            } else {
                                binding.rvSeries.visibility = View.GONE
                                binding.rvAllVideos.visibility = View.VISIBLE
                                binding.rvAllVideos.alpha = 1f
                                binding.rvAllVideos.translationX = 0f
                            }
                            allVideosAdapter.submitList(state.filteredVideos) {
                                if (shouldScrollToTop && state.filteredVideos.isNotEmpty()) {
                                    (binding.rvAllVideos.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                                    binding.rvAllVideos.post {
                                        (binding.rvAllVideos.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                                    }
                                } else {
                                    checkAndScrollToLastPlayed()
                                }
                                if (!isInitialDataLoaded) {
                                    isInitialDataLoaded = true
                                    checkAndDismissSplash()
                                }
                            }
                        } else {
                            binding.rvAllVideos.visibility = View.GONE
                            binding.rvSeries.visibility = View.GONE
                            binding.layoutEmpty.visibility = View.VISIBLE
                            binding.ivEmptyIcon.setImageResource(R.drawable.ic_play_arrow)
                            binding.tvEmptyTitle.text = "Tidak Ada Video"
                            binding.tvEmptyDesc.text = if (state.searchQuery.isNotBlank()) {
                                "Tidak ada video yang cocok dengan \"${state.searchQuery}\""
                            } else {
                                "Tidak ada video ditemukan di penyimpanan perangkat."
                            }
                            if (!isInitialDataLoaded) {
                                isInitialDataLoaded = true
                                checkAndDismissSplash()
                            }
                        }
                    }
                    LibraryTab.FOLDERS -> {
                        if (state.filteredFolders.isNotEmpty()) {
                            binding.layoutEmpty.visibility = View.GONE
                            if (tabChanged && oldTab == LibraryTab.ALL_VIDEOS) {
                                animateTabTransition(toAllVideos = false)
                            } else {
                                binding.rvAllVideos.visibility = View.GONE
                                binding.rvSeries.visibility = View.VISIBLE
                                binding.rvSeries.alpha = 1f
                                binding.rvSeries.translationX = 0f
                            }
                            seriesAdapter.submitList(state.filteredFolders) {
                                if (shouldScrollToTop && state.filteredFolders.isNotEmpty()) {
                                    (binding.rvSeries.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                                    binding.rvSeries.post {
                                        (binding.rvSeries.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                                    }
                                }
                                if (!isInitialDataLoaded) {
                                    isInitialDataLoaded = true
                                    checkAndDismissSplash()
                                }
                            }
                        } else {
                            binding.rvAllVideos.visibility = View.GONE
                            binding.rvSeries.visibility = View.GONE
                            binding.layoutEmpty.visibility = View.VISIBLE
                            binding.ivEmptyIcon.setImageResource(R.drawable.ic_folder)
                            binding.tvEmptyTitle.text = "Tidak Ada Folder"
                            binding.tvEmptyDesc.text = if (state.searchQuery.isNotBlank()) {
                                "Tidak ada folder yang cocok dengan \"${state.searchQuery}\""
                            } else {
                                "Tidak ada folder video ditemukan."
                            }
                            if (!isInitialDataLoaded) {
                                isInitialDataLoaded = true
                                checkAndDismissSplash()
                            }
                        }
                    }
                }

                state.errorMessage?.let { error ->
                    Toast.makeText(this@LibraryActivity, error, Toast.LENGTH_LONG).show()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.lastWatchedUri.collectLatest { uri ->
                val effectiveUri = PlaylistHolder.lastPlayedUri ?: uri
                allVideosAdapter.setLastWatchedUri(effectiveUri)
            }
        }
    }

    private fun checkAndScrollToLastPlayed() {
        val targetUri = PlaylistHolder.lastPlayedUri ?: return
        if (!PlaylistHolder.shouldScrollToLastPlayed) return
        if (binding.tabLayout.selectedTabPosition != 0) return

        val items = allVideosAdapter.currentList
        val targetIndex = items.indexOfFirst { it.uri == targetUri }
        if (targetIndex !in items.indices) return

        PlaylistHolder.consumeShouldScroll()

        binding.rvAllVideos.post {
            val lm = binding.rvAllVideos.layoutManager as? LinearLayoutManager ?: return@post
            val firstVisible = lm.findFirstVisibleItemPosition()
            val lastVisible = lm.findLastVisibleItemPosition()

            if (targetIndex in firstVisible..lastVisible) {
                return@post
            }

            val distance = Math.abs(targetIndex - firstVisible)
            if (distance > 8) {
                val prePosition = if (targetIndex > firstVisible) targetIndex - 3 else targetIndex + 3
                lm.scrollToPositionWithOffset(prePosition.coerceIn(0, items.size - 1), 0)
                binding.rvAllVideos.post {
                    binding.rvAllVideos.smoothScrollToPosition(targetIndex)
                }
            } else {
                binding.rvAllVideos.smoothScrollToPosition(targetIndex)
            }
        }
    }

    private fun animateTabTransition(toAllVideos: Boolean) {
        val outView = if (toAllVideos) binding.rvSeries else binding.rvAllVideos
        val inView = if (toAllVideos) binding.rvAllVideos else binding.rvSeries
        val density = resources.displayMetrics.density
        val slideDistance = 32f * density
        val direction = if (toAllVideos) -1f else 1f

        outView.animate().cancel()
        inView.animate().cancel()

        outView.visibility = View.VISIBLE
        outView.animate()
            .alpha(0f)
            .translationX(-direction * slideDistance * 0.4f)
            .setDuration(160)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                outView.visibility = View.GONE
                outView.alpha = 1f
                outView.translationX = 0f
            }
            .start()

        inView.alpha = 0f
        inView.translationX = direction * slideDistance
        inView.visibility = View.VISIBLE
        inView.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(240)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun showSortDialog() {
        val dialogBinding = DialogSortOptionsBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this, R.style.Theme_AniPlay_BottomSheetDialog)
        dialog.setContentView(dialogBinding.root)

        var selectedOrder = viewModel.state.value.sortOrder

        fun updateSelectionUI(order: SortOrder) {
            selectedOrder = order
            val isName = order == SortOrder.NAME
            val isNewest = order == SortOrder.NEWEST
            val isOldest = order == SortOrder.OLDEST

            // Update Name option
            dialogBinding.layoutSortName.setBackgroundResource(
                if (isName) R.drawable.bg_sort_option_selected else R.drawable.bg_sort_option_normal
            )
            dialogBinding.layoutIconSortName.setBackgroundResource(
                if (isName) R.drawable.bg_sort_icon_selected else R.drawable.bg_sort_icon_normal
            )
            dialogBinding.ivIconSortName.imageTintList = ColorStateList.valueOf(
                if (isName) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8")
            )
            dialogBinding.layoutCheckSortName.visibility = if (isName) View.VISIBLE else View.INVISIBLE
            dialogBinding.ivCheckSortName.visibility = if (isName) View.VISIBLE else View.GONE

            // Update Newest option
            dialogBinding.layoutSortNewest.setBackgroundResource(
                if (isNewest) R.drawable.bg_sort_option_selected else R.drawable.bg_sort_option_normal
            )
            dialogBinding.layoutIconSortNewest.setBackgroundResource(
                if (isNewest) R.drawable.bg_sort_icon_selected else R.drawable.bg_sort_icon_normal
            )
            dialogBinding.ivIconSortNewest.imageTintList = ColorStateList.valueOf(
                if (isNewest) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8")
            )
            dialogBinding.layoutCheckSortNewest.visibility = if (isNewest) View.VISIBLE else View.INVISIBLE
            dialogBinding.ivCheckSortNewest.visibility = if (isNewest) View.VISIBLE else View.GONE

            // Update Oldest option
            dialogBinding.layoutSortOldest.setBackgroundResource(
                if (isOldest) R.drawable.bg_sort_option_selected else R.drawable.bg_sort_option_normal
            )
            dialogBinding.layoutIconSortOldest.setBackgroundResource(
                if (isOldest) R.drawable.bg_sort_icon_selected else R.drawable.bg_sort_icon_normal
            )
            dialogBinding.ivIconSortOldest.imageTintList = ColorStateList.valueOf(
                if (isOldest) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8")
            )
            dialogBinding.layoutCheckSortOldest.visibility = if (isOldest) View.VISIBLE else View.INVISIBLE
            dialogBinding.ivCheckSortOldest.visibility = if (isOldest) View.VISIBLE else View.GONE
        }

        updateSelectionUI(selectedOrder)

        fun onOptionClicked(order: SortOrder, view: View) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            view.animate()
                .scaleX(0.97f)
                .scaleY(0.97f)
                .setDuration(70)
                .withEndAction {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(90).start()
                }
                .start()
            updateSelectionUI(order)
        }

        dialogBinding.layoutSortName.setOnClickListener { onOptionClicked(SortOrder.NAME, it) }
        dialogBinding.layoutSortNewest.setOnClickListener { onOptionClicked(SortOrder.NEWEST, it) }
        dialogBinding.layoutSortOldest.setOnClickListener { onOptionClicked(SortOrder.OLDEST, it) }

        dialogBinding.btnCloseSort.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnCancelSort.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnApplySort.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (selectedOrder != viewModel.state.value.sortOrder) {
                (binding.rvAllVideos.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                (binding.rvSeries.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                viewModel.setSortOrder(selectedOrder)
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteConfirmDialog(episode: Episode) {
        val dialogBinding = DialogDeleteConfirmBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.tvDeleteTitle.text = "Hapus Video?"
        dialogBinding.tvDeleteMessage.text = "Apakah Anda yakin ingin menghapus \"${episode.title}\"? Video akan dihapus secara permanen dari perangkat."

        dialogBinding.btnCancelDelete.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirmDelete.setOnClickListener {
            dialog.dismiss()
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

        dialog.show()
    }

    private fun showRenameDialog(episode: Episode) {
        val dialogBinding = DialogRenameVideoBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.etNewName.setText(episode.title)
        dialogBinding.etNewName.selectAll()

        dialogBinding.btnCancelRename.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirmRename.setOnClickListener {
            val newName = dialogBinding.etNewName.text?.toString()?.trim().orEmpty()
            if (newName.isBlank()) {
                dialogBinding.tilRename.error = "Nama tidak boleh kosong"
                return@setOnClickListener
            }
            val invalidChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
            if (newName.any { it in invalidChars }) {
                dialogBinding.tilRename.error = "Nama file tidak boleh mengandung karakter khusus"
                return@setOnClickListener
            }
            if (newName == episode.title) {
                dialog.dismiss()
                return@setOnClickListener
            }

            dialog.dismiss()
            executeRename(episode, newName)
        }
        dialog.show()
    }

    private fun executeRename(episode: Episode, newName: String) {
        lifecycleScope.launch {
            when (val res = viewModel.renameVideo(episode, newName)) {
                is FileOpResult.Success -> {
                    pendingRenameAction = null
                    Toast.makeText(this@LibraryActivity, "Nama video berhasil diubah", Toast.LENGTH_SHORT).show()
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

    private fun showVideoInfoDialog(episode: Episode) {
        val dialogBinding = DialogVideoInfoBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.tvInfoFilename.text = episode.fileName
        dialogBinding.tvInfoPath.text = "Memuat path..."
        dialogBinding.tvInfoSize.text = FileUtils.formatFileSize(episode.fileSize)
        dialogBinding.tvInfoDuration.text = if (episode.durationMs > 0) FileUtils.formatDuration(episode.durationMs) else "--:--"
        dialogBinding.tvInfoResolution.text = "Memuat..."
        dialogBinding.tvInfoMime.text = "Memuat..."
        dialogBinding.tvInfoHdr.text = "Memuat..."
        dialogBinding.tvInfoColor.text = "Memuat..."
        dialogBinding.tvInfoBitrate.text = "Memuat..."
        dialogBinding.tvInfoDate.text = "Memuat..."

        dialogBinding.btnInfoClose.setOnClickListener { dialog.dismiss() }

        dialog.show()

        lifecycleScope.launch {
            val details = viewModel.getVideoDetails(episode)
            dialogBinding.tvInfoFilename.text = details.fileName
            dialogBinding.tvInfoPath.text = details.path
            dialogBinding.tvInfoSize.text = details.fileSizeFormatted
            dialogBinding.tvInfoDuration.text = details.durationFormatted
            dialogBinding.tvInfoResolution.text = details.resolution
            dialogBinding.tvInfoMime.text = details.mimeType
            dialogBinding.tvInfoHdr.text = details.hdrType
            dialogBinding.tvInfoColor.text = details.colorDetails.ifBlank { "BT.709 (8-bit)" }
            dialogBinding.tvInfoBitrate.text = details.bitrateFormatted
            dialogBinding.tvInfoDate.text = details.dateAddedFormatted
        }
    }

    private fun updateSelectionModeUi(active: Boolean) {
        if (active) {
            binding.layoutSelectionHeader.visibility = View.VISIBLE
            binding.layoutNormalHeader.visibility = View.GONE
            binding.containerSearch.visibility = View.GONE
            binding.tabLayout.visibility = View.GONE
            binding.tabDivider.visibility = View.GONE
        } else {
            binding.layoutSelectionHeader.visibility = View.GONE
            binding.layoutNormalHeader.visibility = View.VISIBLE
            binding.containerSearch.visibility = if (isSearchVisible) View.VISIBLE else View.GONE
            binding.tabLayout.visibility = View.VISIBLE
            binding.tabDivider.visibility = View.VISIBLE
        }
    }

    private fun showEpisodeBottomSheet(episode: Episode) {
        val sheetBinding = BottomSheetVideoOptionsBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this, R.style.Theme_AniPlay_BottomSheetDialog)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.tvBsVideoTitle.text = episode.title

        sheetBinding.optRename.setOnClickListener {
            dialog.dismiss()
            showRenameDialog(episode)
        }

        sheetBinding.optShare.setOnClickListener {
            dialog.dismiss()
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/*"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(episode.uri))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Bagikan Video"))
            } catch (e: Exception) {
                Toast.makeText(this, "Tidak dapat membagikan video", Toast.LENGTH_SHORT).show()
            }
        }

        sheetBinding.optInfo.setOnClickListener {
            dialog.dismiss()
            showVideoInfoDialog(episode)
        }

        sheetBinding.optDelete.setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmDialog(episode)
        }

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true

                bottomSheet.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                (bottomSheet.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    lp.leftMargin = 0
                    lp.rightMargin = 0
                    lp.bottomMargin = 0
                    bottomSheet.layoutParams = lp
                }
                bottomSheet.setBackgroundResource(R.drawable.bg_bottom_sheet_video)
            }
        }

        dialog.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.BOTTOM)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.bs_background)
        }

        ViewCompat.setOnApplyWindowInsetsListener(sheetBinding.root) { view, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(
                0,
                0,
                0,
                navBars.bottom.coerceAtLeast((16 * resources.displayMetrics.density).toInt())
            )
            insets
        }

        dialog.show()
    }

    private fun showBatchDeleteConfirmDialog(episodes: List<Episode>) {
        val count = episodes.size
        val dialogBinding = DialogDeleteConfirmBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.tvDeleteTitle.text = "Hapus $count Video?"
        dialogBinding.tvDeleteMessage.text = "Apakah Anda yakin ingin menghapus $count video terpilih? Video akan dihapus secara permanen dari perangkat."

        dialogBinding.btnCancelDelete.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirmDelete.setOnClickListener {
            dialog.dismiss()
            val uris = episodes.map { it.uri }
            pendingRenameAction = null
            pendingDeleteUris = uris
            allVideosAdapter.exitSelectionMode()
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

        dialog.show()
    }

    private fun setupCinematicSplash(savedInstanceState: Bundle?) {
        if (hasShownSplash || savedInstanceState != null) {
            binding.layoutSplashOverlay.visibility = View.GONE
            isSplashAnimationCompleted = true
            isInitialDataLoaded = true
            isSplashDismissed = true
            return
        }
        hasShownSplash = true
        isSplashAnimationCompleted = false
        isInitialDataLoaded = false
        isSplashDismissed = false

        val overlay = binding.layoutSplashOverlay
        val logo = binding.ivSplashLogo
        val glow = binding.viewSplashGlow
        val title = binding.tvSplashTitle
        val subtitle = binding.tvSplashSubtitle
        val progressContainer = binding.layoutSplashProgressContainer
        val progressBar = binding.viewSplashProgressBar
        val version = binding.tvSplashVersion

        overlay.visibility = View.VISIBLE
        overlay.alpha = 1f

        // Initial state before reveal
        logo.alpha = 0f
        logo.scaleX = 0.72f
        logo.scaleY = 0.72f

        glow.alpha = 0f
        glow.scaleX = 0.7f
        glow.scaleY = 0.7f

        val dp24 = 24 * resources.displayMetrics.density
        val dp16 = 16 * resources.displayMetrics.density

        title.alpha = 0f
        title.translationY = dp24

        subtitle.alpha = 0f
        subtitle.translationY = dp16

        progressContainer.alpha = 0f
        version.alpha = 0f

        progressBar.pivotX = 0f
        progressBar.scaleX = 0f

        overlay.post {
            if (isFinishing || isDestroyed || isSplashDismissed) return@post

            // Phase 1: Brand reveal (Logo bounce, Ambient glow, Title slide)
            val logoAnimX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.72f, 1f).apply {
                duration = 500
                interpolator = OvershootInterpolator(1.15f)
            }
            val logoAnimY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.72f, 1f).apply {
                duration = 500
                interpolator = OvershootInterpolator(1.15f)
            }
            val logoFade = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f).apply {
                duration = 500
                interpolator = OvershootInterpolator(1.15f)
            }

            val glowAnimX = ObjectAnimator.ofFloat(glow, View.SCALE_X, 0.7f, 1f).apply {
                duration = 500
                interpolator = DecelerateInterpolator(1.5f)
            }
            val glowAnimY = ObjectAnimator.ofFloat(glow, View.SCALE_Y, 0.7f, 1f).apply {
                duration = 500
                interpolator = DecelerateInterpolator(1.5f)
            }
            val glowFade = ObjectAnimator.ofFloat(glow, View.ALPHA, 0f, 0.85f).apply {
                duration = 500
                interpolator = DecelerateInterpolator(1.5f)
            }

            val titleFade = ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f).apply {
                duration = 400
                startDelay = 100
                interpolator = DecelerateInterpolator()
            }
            val titleSlide = ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, dp24, 0f).apply {
                duration = 400
                startDelay = 100
                interpolator = DecelerateInterpolator()
            }

            val subtitleFade = ObjectAnimator.ofFloat(subtitle, View.ALPHA, 0f, 1f).apply {
                duration = 400
                startDelay = 160
                interpolator = DecelerateInterpolator()
            }
            val subtitleSlide = ObjectAnimator.ofFloat(subtitle, View.TRANSLATION_Y, dp16, 0f).apply {
                duration = 400
                startDelay = 160
                interpolator = DecelerateInterpolator()
            }

            val progressFade = ObjectAnimator.ofFloat(progressContainer, View.ALPHA, 0f, 1f).apply {
                duration = 300
                startDelay = 200
            }
            val versionFade = ObjectAnimator.ofFloat(version, View.ALPHA, 0f, 0.85f).apply {
                duration = 300
                startDelay = 250
            }

            // Phase 2: Hardware-accelerated capsule progress bar fill (200ms -> 1000ms)
            val progressFillAnim = ObjectAnimator.ofFloat(progressBar, View.SCALE_X, 0f, 1f).apply {
                duration = 800
                startDelay = 200
                interpolator = FastOutSlowInInterpolator()
            }

            // Soft glowing pulse breathing (stays completely within 240dp bounds)
            val glowPulseX = ObjectAnimator.ofFloat(glow, View.SCALE_X, 1f, 1.1f, 1f).apply {
                duration = 800
                startDelay = 200
                interpolator = AccelerateDecelerateInterpolator()
            }
            val glowPulseY = ObjectAnimator.ofFloat(glow, View.SCALE_Y, 1f, 1.1f, 1f).apply {
                duration = 800
                startDelay = 200
                interpolator = AccelerateDecelerateInterpolator()
            }

            val introSet = AnimatorSet().apply {
                playTogether(
                    logoAnimX, logoAnimY, logoFade,
                    glowAnimX, glowAnimY, glowFade,
                    titleFade, titleSlide,
                    subtitleFade, subtitleSlide,
                    progressFade,
                    versionFade,
                    progressFillAnim,
                    glowPulseX,
                    glowPulseY
                )
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        isSplashAnimationCompleted = true
                        checkAndDismissSplash()
                    }
                })
            }

            splashAnimators.add(introSet)
            introSet.start()

            // Safety fallback so splash never hangs
            binding.root.postDelayed({
                if (!isSplashDismissed && viewModel.state.value.hasScanCompleted) {
                    isInitialDataLoaded = true
                    isSplashAnimationCompleted = true
                    checkAndDismissSplash()
                }
            }, 3000)
        }
    }

    private fun checkAndDismissSplash() {
        if (isSplashAnimationCompleted && isInitialDataLoaded && !isSplashDismissed) {
            // Ensure data list is visible and ready behind the overlay before dissolving
            if (viewModel.state.value.currentTab == LibraryTab.ALL_VIDEOS) {
                if (allVideosAdapter.currentList.isNotEmpty()) {
                    binding.rvAllVideos.visibility = View.VISIBLE
                    binding.rvAllVideos.alpha = 1f
                }
            } else {
                if (seriesAdapter.currentList.isNotEmpty()) {
                    binding.rvSeries.visibility = View.VISIBLE
                    binding.rvSeries.alpha = 1f
                }
            }
            binding.progressLoading.visibility = View.GONE
            dismissCinematicSplash()
        }
    }

    private fun dismissCinematicSplash() {
        if (isSplashDismissed || isFinishing || isDestroyed) return
        isSplashDismissed = true

        val overlay = binding.layoutSplashOverlay
        val logo = binding.ivSplashLogo
        val glow = binding.viewSplashGlow
        val title = binding.tvSplashTitle
        val subtitle = binding.tvSplashSubtitle
        val progressContainer = binding.layoutSplashProgressContainer
        val version = binding.tvSplashVersion

        // Phase 3: Dissolve exit transition into library
        val exitLogoScaleX = ObjectAnimator.ofFloat(logo, View.SCALE_X, logo.scaleX, 1.22f)
        val exitLogoScaleY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, logo.scaleY, 1.22f)
        val exitLogoAlpha = ObjectAnimator.ofFloat(logo, View.ALPHA, logo.alpha, 0f)

        val exitGlowScaleX = ObjectAnimator.ofFloat(glow, View.SCALE_X, glow.scaleX, 1.25f)
        val exitGlowScaleY = ObjectAnimator.ofFloat(glow, View.SCALE_Y, glow.scaleY, 1.25f)
        val exitGlowAlpha = ObjectAnimator.ofFloat(glow, View.ALPHA, glow.alpha, 0f)

        val exitTitleAlpha = ObjectAnimator.ofFloat(title, View.ALPHA, title.alpha, 0f)
        val exitSubtitleAlpha = ObjectAnimator.ofFloat(subtitle, View.ALPHA, subtitle.alpha, 0f)
        val exitProgressAlpha = ObjectAnimator.ofFloat(progressContainer, View.ALPHA, progressContainer.alpha, 0f)
        val exitVersionAlpha = ObjectAnimator.ofFloat(version, View.ALPHA, version.alpha, 0f)

        val exitOverlayAlpha = ObjectAnimator.ofFloat(overlay, View.ALPHA, 1f, 0f)

        val exitSet = AnimatorSet().apply {
            playTogether(
                exitLogoScaleX, exitLogoScaleY, exitLogoAlpha,
                exitGlowScaleX, exitGlowScaleY, exitGlowAlpha,
                exitTitleAlpha, exitSubtitleAlpha,
                exitProgressAlpha, exitVersionAlpha,
                exitOverlayAlpha
            )
            duration = 380
            interpolator = FastOutSlowInInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    overlay.visibility = View.GONE
                }
            })
        }

        splashAnimators.add(exitSet)
        exitSet.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        splashAnimators.forEach { it.cancel() }
        splashAnimators.clear()
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"
        private var hasShownSplash = false
    }
}
