package com.personal.animeplayer.ui.library

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.personal.animeplayer.R
import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.databinding.ItemEpisodeBinding
import com.personal.animeplayer.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EpisodeAdapter(
    private val scope: CoroutineScope,
    private val onEpisodeClicked: (Episode) -> Unit,
    private val onEpisodeDelete: ((Episode) -> Unit)? = null,
    private val onEpisodeRename: ((Episode) -> Unit)? = null,
    private val onEpisodeInfo: ((Episode) -> Unit)? = null,
    private val onEpisodeMoreClicked: ((Episode) -> Unit)? = null
) : ListAdapter<Episode, EpisodeAdapter.EpisodeViewHolder>(DiffCallback) {

    // In-memory thumbnail cache (max 60 items)
    private val thumbCache = object : LruCache<String, Bitmap>(60) {}
    private val durationCache = object : LruCache<String, Long>(300) {}

    // Multi-Selection State
    var isSelectionMode: Boolean = false
        private set

    val selectedUris = mutableSetOf<String>()

    var lastWatchedUri: String? = null
        private set

    fun setLastWatchedUri(uri: String?) {
        if (lastWatchedUri != uri) {
            val oldUri = lastWatchedUri
            lastWatchedUri = uri
            val oldPos = currentList.indexOfFirst { it.uri == oldUri }
            val newPos = currentList.indexOfFirst { it.uri == uri }
            if (oldPos != -1) notifyItemChanged(oldPos)
            if (newPos != -1) notifyItemChanged(newPos)
            if (oldPos == -1 && newPos == -1 && uri != null) {
                notifyDataSetChanged()
            }
        }
    }

    var onSelectionChanged: ((count: Int) -> Unit)? = null
    var onSelectionModeToggled: ((active: Boolean) -> Unit)? = null

    fun enterSelectionMode(initialEpisode: Episode) {
        if (!isSelectionMode) {
            isSelectionMode = true
            selectedUris.clear()
            selectedUris.add(initialEpisode.uri)
            notifyItemRangeChanged(0, currentList.size)
            onSelectionModeToggled?.invoke(true)
            onSelectionChanged?.invoke(selectedUris.size)
        }
    }

    fun exitSelectionMode() {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedUris.clear()
            notifyItemRangeChanged(0, currentList.size)
            onSelectionModeToggled?.invoke(false)
            onSelectionChanged?.invoke(0)
        }
    }

    fun toggleSelection(episode: Episode) {
        if (selectedUris.contains(episode.uri)) {
            selectedUris.remove(episode.uri)
            if (selectedUris.isEmpty()) {
                exitSelectionMode()
                return
            }
        } else {
            selectedUris.add(episode.uri)
        }
        val pos = currentList.indexOf(episode)
        if (pos != -1) notifyItemChanged(pos)
        onSelectionChanged?.invoke(selectedUris.size)
    }

    fun selectAll() {
        if (!isSelectionMode) {
            isSelectionMode = true
            onSelectionModeToggled?.invoke(true)
        }
        selectedUris.clear()
        currentList.forEach { selectedUris.add(it.uri) }
        notifyItemRangeChanged(0, currentList.size)
        onSelectionChanged?.invoke(selectedUris.size)
    }

    fun getSelectedEpisodes(): List<Episode> {
        return currentList.filter { it.uri in selectedUris }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val binding = ItemEpisodeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EpisodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EpisodeViewHolder(private val binding: ItemEpisodeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var thumbJob: Job? = null

        fun bind(episode: Episode) {
            binding.root.scaleX = 1.0f
            binding.root.scaleY = 1.0f
            binding.tvEpisodeTitle.text = episode.title

            val effectiveDuration = if (episode.durationMs > 0) {
                episode.durationMs
            } else {
                durationCache.get(episode.uri) ?: 0L
            }

            binding.tvEpisodeDuration.text = if (effectiveDuration > 0) {
                FileUtils.formatBadgeDuration(effectiveDuration)
            } else {
                "--.--"
            }

            val isShort = effectiveDuration in 1 until 60_000L
            if (!isShort && episode.progressPercentage > 0) {
                binding.indicatorWatchProgress.visibility = View.VISIBLE
                binding.indicatorWatchProgress.progress = episode.progressPercentage
            } else {
                binding.indicatorWatchProgress.visibility = View.GONE
            }

            // Metadata Subtitle (Date Added & File Size)
            val metadataParts = mutableListOf<String>()
            if (episode.dateAdded > 0) {
                val timeMs = if (episode.dateAdded > 2_500_000_000_000L) {
                    episode.dateAdded / 1000L
                } else {
                    episode.dateAdded
                }
                val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                metadataParts.add(sdf.format(java.util.Date(timeMs)))
            }
            if (episode.fileSize > 0) {
                metadataParts.add(FileUtils.formatFileSize(episode.fileSize))
            }
            if (metadataParts.isNotEmpty()) {
                binding.tvEpisodeSubtitle.text = metadataParts.joinToString(" • ")
                binding.tvEpisodeSubtitle.visibility = View.VISIBLE
            } else {
                binding.tvEpisodeSubtitle.visibility = View.GONE
            }

            // Last-Watched Visual Indicator: Title text in Apple Blue
            val isLastWatched = (episode.uri == lastWatchedUri)
            binding.tvEpisodeTitle.setTextColor(
                if (isLastWatched) 0xFF0A84FF.toInt() else 0xFFFFFFFF.toInt()
            )

            // Selection Mode Visuals (Frameless by default; highlight when selected)
            val isSelected = selectedUris.contains(episode.uri)
            val strokeDensity = binding.root.context.resources.displayMetrics.density
            if (isSelectionMode) {
                binding.cbSelect.visibility = View.VISIBLE
                binding.btnEpisodeMore.visibility = View.GONE
                binding.cbSelect.isChecked = isSelected
                binding.cardVideoItem.strokeColor = if (isSelected) 0xFF0084FF.toInt() else 0
                binding.cardVideoItem.strokeWidth = if (isSelected) (1.5f * strokeDensity).toInt() else 0
                binding.cardVideoItem.setCardBackgroundColor(
                    if (isSelected) 0xFF162A4D.toInt() else 0
                )
            } else {
                binding.cbSelect.visibility = View.GONE
                binding.btnEpisodeMore.visibility = View.VISIBLE
                binding.cardVideoItem.strokeColor = 0
                binding.cardVideoItem.strokeWidth = 0
                binding.cardVideoItem.setCardBackgroundColor(0)
            }

            // Thumbnail loading & duration retrieval
            thumbJob?.cancel()
            val cached = thumbCache.get(episode.uri)
            if (cached != null) {
                binding.ivEpisodeThumb.setImageBitmap(cached)
                binding.ivEpisodeThumb.imageTintList = null
            } else {
                binding.ivEpisodeThumb.setImageResource(R.drawable.ic_play_arrow)
                thumbJob = scope.launch(Dispatchers.IO) {
                    val needDuration = effectiveDuration <= 0
                    val (bitmap, durMs) = extractThumbnailAndDuration(episode.uri, needDuration)
                    if (bitmap != null) {
                        thumbCache.put(episode.uri, bitmap)
                    }
                    if (durMs > 0) {
                        durationCache.put(episode.uri, durMs)
                    }
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            binding.ivEpisodeThumb.setImageBitmap(bitmap)
                            binding.ivEpisodeThumb.imageTintList = null
                        }
                        if (durMs > 0 && binding.tvEpisodeDuration.text == "--.--") {
                            binding.tvEpisodeDuration.text = FileUtils.formatBadgeDuration(durMs)
                        }
                        if (durMs in 1 until 60_000L) {
                            binding.indicatorWatchProgress.visibility = View.GONE
                        }
                    }
                }
            }

            binding.btnEpisodeMore.setOnClickListener { v ->
                if (onEpisodeMoreClicked != null) {
                    onEpisodeMoreClicked.invoke(episode)
                } else {
                    val context = v.context
                    val popup = androidx.appcompat.widget.PopupMenu(context, v)
                    popup.menu.add("Hapus").setOnMenuItemClickListener {
                        onEpisodeDelete?.invoke(episode)
                        true
                    }
                    popup.menu.add("Ubah Nama").setOnMenuItemClickListener {
                        onEpisodeRename?.invoke(episode)
                        true
                    }
                    popup.menu.add("Bagikan").setOnMenuItemClickListener {
                        try {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "video/*"
                                putExtra(android.content.Intent.EXTRA_STREAM, Uri.parse(episode.uri))
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Bagikan Video"))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Tidak dapat membagikan video", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    popup.menu.add("Info Video Lengkap").setOnMenuItemClickListener {
                        onEpisodeInfo?.invoke(episode)
                        true
                    }
                    popup.show()
                }
            }

            binding.cbSelect.setOnClickListener {
                toggleSelection(episode)
            }

            binding.root.setOnClickListener { view ->
                if (isSelectionMode) {
                    toggleSelection(episode)
                } else {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    view.animate()
                        .scaleX(0.97f)
                        .scaleY(0.97f)
                        .setDuration(70)
                        .withEndAction {
                            view.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(90)
                                .withEndAction {
                                    onEpisodeClicked(episode)
                                }
                                .start()
                        }
                        .start()
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    enterSelectionMode(episode)
                } else {
                    toggleSelection(episode)
                }
                true
            }
        }

        private fun extractThumbnailAndDuration(uriString: String, extractDuration: Boolean): Pair<Bitmap?, Long> {
            val retriever = MediaMetadataRetriever()
            return try {
                val context = binding.root.context
                retriever.setDataSource(context, Uri.parse(uriString))
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(10_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 360, 202)
                } else {
                    retriever.getFrameAtTime(10_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
                        Bitmap.createScaledBitmap(it, 360, 202, true)
                    }
                }
                val dur = if (extractDuration) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } else 0L
                Pair(bitmap, dur)
            } catch (e: Exception) {
                Pair(null, 0L)
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {}
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Episode>() {
        override fun areItemsTheSame(oldItem: Episode, newItem: Episode): Boolean {
            return oldItem.uri == newItem.uri
        }

        override fun areContentsTheSame(oldItem: Episode, newItem: Episode): Boolean {
            return oldItem == newItem
        }
    }
}
