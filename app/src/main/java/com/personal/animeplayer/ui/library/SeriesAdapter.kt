package com.personal.animeplayer.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.personal.animeplayer.data.model.AnimeSeries
import com.personal.animeplayer.databinding.ItemAnimeSeriesBinding

class SeriesAdapter(
    private val onSeriesClicked: (AnimeSeries) -> Unit
) : ListAdapter<AnimeSeries, SeriesAdapter.SeriesViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeriesViewHolder {
        val binding = ItemAnimeSeriesBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SeriesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SeriesViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SeriesViewHolder(private val binding: ItemAnimeSeriesBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(series: AnimeSeries) {
            binding.root.scaleX = 1.0f
            binding.root.scaleY = 1.0f
            binding.tvSeriesName.text = series.name
            binding.chipEpisodeCount.text = "${series.episodeCount} Video"
            binding.root.setOnClickListener { view ->
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                view.animate()
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(70)
                    .withEndAction {
                        view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(90)
                            .withEndAction {
                                onSeriesClicked(series)
                            }
                            .start()
                    }
                    .start()
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AnimeSeries>() {
        override fun areItemsTheSame(oldItem: AnimeSeries, newItem: AnimeSeries): Boolean {
            return oldItem.folderUri == newItem.folderUri
        }

        override fun areContentsTheSame(oldItem: AnimeSeries, newItem: AnimeSeries): Boolean {
            return oldItem == newItem
        }
    }
}
