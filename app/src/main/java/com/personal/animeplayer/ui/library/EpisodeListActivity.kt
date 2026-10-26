package com.personal.animeplayer.ui.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
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
import android.animation.ValueAnimator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
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
import com.personal.animeplayer.R
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.FileOpResult
import com.personal.animeplayer.databinding.ActivityEpisodeListBinding
import com.personal.animeplayer.databinding.BottomSheetVideoOptionsBinding
import com.personal.animeplayer.databinding.DialogDeleteConfirmBinding
import com.personal.animeplayer.databinding.DialogRenameVideoBinding
import com.personal.animeplayer.databinding.DialogVideoInfoBinding
import com.personal.animeplayer.data.model.SortOrder
import com.personal.animeplayer.databinding.DialogSortOptionsBinding
import com.personal.animeplayer.player.PlaylistHolder
import com.personal.animeplayer.ui.player.PlayerActivity
import com.personal.animeplayer.util.FileUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EpisodeListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEpisodeListBinding
    private val viewModel: EpisodeViewModel by viewModels()
    private lateinit var episodeAdapter: EpisodeAdapter

    private var seriesFolderUri: Uri? = null
    private var seriesName: String = ""
    private var pendingRenameAction: Pair<Episode, String>? = null
    private var pendingDeleteUris: List<String>? = null
    private var isSearchVisible: Boolean = false

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
        binding = ActivityEpisodeListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val extraBottomPadding = (76 * density).toInt()
            binding.appBar.setPadding(0, insets.top, 0, 0)
            binding.rvEpisodes.setPadding(
                binding.rvEpisodes.paddingLeft,
                binding.rvEpisodes.paddingTop,
                binding.rvEpisodes.paddingRight,
                insets.bottom + extraBottomPadding
            )
            systemBottomInset = insets.bottom
            updateAlphabetPosition()
            windowInsets
        }

        parseIntent()
        setupRecyclerView()
        setupUIEvents()
        setupAlphabetIndex()
        observeViewModel()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (episodeAdapter.isSelectionMode) {
                    episodeAdapter.exitSelectionMode()
                } else if (isSearchVisible) {
                    toggleSearchBar(false)
                } else {
                    finish()
                }
            }
        })
    }

    private fun parseIntent() {
        val uriStr = intent.getStringExtra(EXTRA_SERIES_URI)
        seriesName = intent.getStringExtra(EXTRA_SERIES_NAME) ?: "Anime Series"

        if (uriStr != null) {
            seriesFolderUri = Uri.parse(uriStr)
            binding.tvSeriesTitle.text = seriesName
            viewModel.loadEpisodes(seriesFolderUri!!)
        } else {
            Toast.makeText(this, "Folder series tidak valid", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Update last watched indicator immediately from memory if available
        PlaylistHolder.lastPlayedUri?.let { episodeAdapter.setLastWatchedUri(it) }
        // Check and scroll to last played video if returning from player
        checkAndScrollToLastPlayed()
        // Reload episodes on resume to refresh watch progress without full-screen loading flicker
        seriesFolderUri?.let { viewModel.loadEpisodes(it, isRefresh = true) }
    }

    private fun checkAndScrollToLastPlayed() {
        val targetUri = PlaylistHolder.lastPlayedUri ?: return
        if (!PlaylistHolder.shouldScrollToLastPlayed) return

        val items = episodeAdapter.currentList
        val targetIndex = items.indexOfFirst { it.uri == targetUri }
        if (targetIndex !in items.indices) return

        // Consume flag once target is located in current list
        PlaylistHolder.consumeShouldScroll()

        binding.rvEpisodes.post {
            val lm = binding.rvEpisodes.layoutManager as? LinearLayoutManager ?: return@post
            val firstVisible = lm.findFirstVisibleItemPosition()
            val lastVisible = lm.findLastVisibleItemPosition()

            if (targetIndex in firstVisible..lastVisible) {
                return@post
            }

            val distance = Math.abs(targetIndex - firstVisible)
            if (distance > 8) {
                val prePosition = if (targetIndex > firstVisible) targetIndex - 3 else targetIndex + 3
                lm.scrollToPositionWithOffset(prePosition.coerceIn(0, items.size - 1), 0)
                binding.rvEpisodes.post {
                    binding.rvEpisodes.smoothScrollToPosition(targetIndex)
                }
            } else {
                binding.rvEpisodes.smoothScrollToPosition(targetIndex)
            }
        }
    }

    private fun setupRecyclerView() {
        episodeAdapter = EpisodeAdapter(
            scope = lifecycleScope,
            onEpisodeClicked = { episode ->
                PlaylistHolder.setPlaylist(
                    episodeAdapter.currentList,
                    episode.seriesFolderUri
                )
                PlayerActivity.start(
                    context = this,
                    videoUri = Uri.parse(episode.uri),
                    title = episode.title,
                    seriesFolderUri = episode.seriesFolderUri
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

        binding.rvEpisodes.apply {
            layoutManager = LinearLayoutManager(this@EpisodeListActivity)
            adapter = episodeAdapter
        }
    }

    private fun setupUIEvents() {
        binding.btnBackEpisodes.setOnClickListener { finish() }

        binding.btnSearchEpisode.setOnClickListener {
            toggleSearchBar(!isSearchVisible)
        }

        binding.btnSortEpisode.setOnClickListener {
            showSortDialog()
        }

        binding.btnCloseSelection.setOnClickListener {
            episodeAdapter.exitSelectionMode()
        }

        binding.btnSelectAll.setOnClickListener {
            if (episodeAdapter.selectedUris.size == episodeAdapter.currentList.size) {
                episodeAdapter.exitSelectionMode()
            } else {
                episodeAdapter.selectAll()
            }
        }

        binding.btnDeleteSelected.setOnClickListener {
            val selected = episodeAdapter.getSelectedEpisodes()
            if (selected.isNotEmpty()) {
                showBatchDeleteConfirmDialog(selected)
            }
        }

        binding.swipeRefreshEpisodes.setColorSchemeColors(ContextCompat.getColor(this, R.color.secondary))
        binding.swipeRefreshEpisodes.setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#1A2035"))
        binding.swipeRefreshEpisodes.setOnRefreshListener {
            seriesFolderUri?.let { uri ->
                viewModel.loadEpisodes(uri, isRefresh = true)
            } ?: run {
                binding.swipeRefreshEpisodes.isRefreshing = false
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

            smoothScrollToTop(binding.rvEpisodes)
        }

        binding.tvSeriesTitle.setOnClickListener {
            smoothScrollToTop(binding.rvEpisodes)
        }

        binding.rvEpisodes.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val lm = recyclerView.layoutManager as? LinearLayoutManager
                val firstPos = lm?.findFirstVisibleItemPosition() ?: 0
                val totalItems = recyclerView.adapter?.itemCount ?: 0
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
        })

        binding.etSearchEpisode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.searchEpisodes(s?.toString() ?: "")
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
            binding.tilSearchEpisode.visibility = View.VISIBLE
            binding.containerSearchEpisode.visibility = View.VISIBLE

            val widthSpec = View.MeasureSpec.makeMeasureSpec(
                binding.appBar.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels,
                View.MeasureSpec.AT_MOST
            )
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            binding.containerSearchEpisode.measure(widthSpec, heightSpec)
            val targetHeight = if (binding.containerSearchEpisode.measuredHeight > 0) {
                binding.containerSearchEpisode.measuredHeight
            } else {
                (58 * resources.displayMetrics.density).toInt()
            }

            binding.containerSearchEpisode.layoutParams.height = 0
            binding.containerSearchEpisode.requestLayout()

            binding.tilSearchEpisode.alpha = 0f
            binding.tilSearchEpisode.translationY = -targetHeight * 0.4f

            searchAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260
                interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    binding.containerSearchEpisode.layoutParams.height = (targetHeight * fraction).toInt()
                    binding.containerSearchEpisode.requestLayout()

                    binding.tilSearchEpisode.alpha = fraction
                    binding.tilSearchEpisode.translationY = -targetHeight * 0.4f * (1f - fraction)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.containerSearchEpisode.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        binding.containerSearchEpisode.requestLayout()
                        binding.tilSearchEpisode.alpha = 1f
                        binding.tilSearchEpisode.translationY = 0f
                    }
                })
                start()
            }

            binding.btnSearchEpisode.setColorFilter(ContextCompat.getColor(this, R.color.secondary))
            binding.btnSearchEpisode.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).withEndAction {
                binding.btnSearchEpisode.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }.start()

            binding.etSearchEpisode.requestFocus()
            imm?.showSoftInput(binding.etSearchEpisode, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } else {
            binding.btnSearchEpisode.setColorFilter(ContextCompat.getColor(this, R.color.white))

            val startHeight = binding.containerSearchEpisode.height.takeIf { it > 0 }
                ?: (58 * resources.displayMetrics.density).toInt()

            searchAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 220
                interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    binding.containerSearchEpisode.layoutParams.height = (startHeight * fraction).toInt()
                    binding.containerSearchEpisode.requestLayout()

                    binding.tilSearchEpisode.alpha = fraction
                    binding.tilSearchEpisode.translationY = -startHeight * 0.4f * (1f - fraction)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.containerSearchEpisode.visibility = View.GONE
                        binding.containerSearchEpisode.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        binding.containerSearchEpisode.requestLayout()
                        binding.tilSearchEpisode.alpha = 1f
                        binding.tilSearchEpisode.translationY = 0f
                    }
                })
                start()
            }

            binding.etSearchEpisode.setText("")
            imm?.hideSoftInputFromWindow(binding.etSearchEpisode.windowToken, 0)
            binding.etSearchEpisode.clearFocus()
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
        val isNameSort = viewModel.sortOrder == SortOrder.NAME
        val hasEpisodes = episodeAdapter.currentList.isNotEmpty()
        if (!isNameSort || !hasEpisodes) return

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

            val currentEpisodes = episodeAdapter.currentList
            if (currentEpisodes.isNotEmpty()) {
                val targetPos = AlphabetIndexView.findPositionForLetter(letter, currentEpisodes)
                if (targetPos != -1) {
                    (binding.rvEpisodes.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(targetPos, 0)
                }
            }
        }

        binding.alphabetIndexView.onLetterReleased = {
            isDraggingAlphabet = false
            scheduleHideAlphabetBubble(300L)
            scheduleHideAlphabetBar(1200L)
        }

        binding.rvEpisodes.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
                    if (firstPos != RecyclerView.NO_POSITION && firstPos in episodeAdapter.currentList.indices) {
                        val letter = AlphabetIndexView.getLetterForEpisode(episodeAdapter.currentList[firstPos])
                        binding.alphabetIndexView.setHighlightedLetter(letter)
                    }
                }
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is EpisodeUiState.Loading -> {
                        if (!binding.swipeRefreshEpisodes.isRefreshing) {
                            binding.progressLoadingEpisodes.visibility = View.VISIBLE
                            binding.rvEpisodes.visibility = View.GONE
                            binding.tvNoEpisodes.visibility = View.GONE
                        }
                    }
                    is EpisodeUiState.Success -> {
                        binding.swipeRefreshEpisodes.isRefreshing = false
                        binding.progressLoadingEpisodes.visibility = View.GONE
                        binding.rvEpisodes.visibility = View.VISIBLE
                        binding.tvNoEpisodes.visibility = View.GONE
                        val isNameSort = viewModel.sortOrder == SortOrder.NAME
                        val canShowAlphabet = isNameSort && state.episodes.isNotEmpty()
                        if (!canShowAlphabet) {
                            binding.alphabetIndexView.visibility = View.GONE
                            binding.tvAlphabetBubble.visibility = View.GONE
                        }
                        episodeAdapter.submitList(state.episodes) {
                            checkAndScrollToLastPlayed()
                        }
                    }
                    is EpisodeUiState.Empty -> {
                        binding.swipeRefreshEpisodes.isRefreshing = false
                        binding.progressLoadingEpisodes.visibility = View.GONE
                        binding.rvEpisodes.visibility = View.GONE
                        binding.alphabetIndexView.visibility = View.GONE
                        binding.tvAlphabetBubble.visibility = View.GONE
                        binding.tvNoEpisodes.visibility = View.VISIBLE
                        binding.tvNoEpisodes.text = state.message
                    }
                    is EpisodeUiState.Error -> {
                        binding.swipeRefreshEpisodes.isRefreshing = false
                        binding.progressLoadingEpisodes.visibility = View.GONE
                        binding.alphabetIndexView.visibility = View.GONE
                        binding.tvAlphabetBubble.visibility = View.GONE
                        Toast.makeText(this@EpisodeListActivity, state.error, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.sortOrderFlow.collectLatest { sortOrder ->
                val isNameSort = sortOrder == SortOrder.NAME
                val hasEpisodes = episodeAdapter.currentList.isNotEmpty()
                val canShowAlphabet = isNameSort && hasEpisodes
                if (!canShowAlphabet) {
                    binding.alphabetIndexView.visibility = View.GONE
                    binding.tvAlphabetBubble.visibility = View.GONE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.lastWatchedUri.collectLatest { uri ->
                val effectiveUri = PlaylistHolder.lastPlayedUri ?: uri
                episodeAdapter.setLastWatchedUri(effectiveUri)
            }
        }
    }

    private fun showSortDialog() {
        val dialogBinding = DialogSortOptionsBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this, R.style.Theme_AniPlay_BottomSheetDialog)
        dialog.setContentView(dialogBinding.root)

        var selectedOrder = viewModel.sortOrder

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
            if (selectedOrder != viewModel.sortOrder) {
                (binding.rvEpisodes.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                viewModel.setSortOrder(selectedOrder)
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteConfirmDialog(episode: Episode) {
        val folderUri = seriesFolderUri ?: return
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
                when (val res = viewModel.deleteVideo(episode, folderUri)) {
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

        dialog.show()
    }

    private fun showRenameDialog(episode: Episode) {
        val folderUri = seriesFolderUri ?: return
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
            executeRename(episode, newName, folderUri)
        }
        dialog.show()
    }

    private fun executeRename(episode: Episode, newName: String, folderUri: Uri) {
        lifecycleScope.launch {
            when (val res = viewModel.renameVideo(episode, newName, folderUri)) {
                is FileOpResult.Success -> {
                    pendingRenameAction = null
                    Toast.makeText(this@EpisodeListActivity, "Nama video berhasil diubah", Toast.LENGTH_SHORT).show()
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
            binding.containerSearchEpisode.visibility = View.GONE
        } else {
            binding.layoutSelectionHeader.visibility = View.GONE
            binding.layoutNormalHeader.visibility = View.VISIBLE
            binding.containerSearchEpisode.visibility = if (isSearchVisible) View.VISIBLE else View.GONE
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
        val folderUri = seriesFolderUri ?: return
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
            episodeAdapter.exitSelectionMode()
            lifecycleScope.launch {
                when (val res = viewModel.deleteVideos(episodes, folderUri)) {
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

        dialog.show()
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
