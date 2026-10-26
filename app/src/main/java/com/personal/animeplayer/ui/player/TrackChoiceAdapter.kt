package com.personal.animeplayer.ui.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.personal.animeplayer.R
import com.personal.animeplayer.databinding.ItemTrackChoiceBinding

data class TrackItemDisplay(
    val title: String,
    val subtitle: String,
    val isSelected: Boolean,
    val isSecondaryEnabled: Boolean = false,
    val trackGroupIndex: Int,
    val trackIndex: Int
)

class TrackChoiceAdapter(
    private val items: List<TrackItemDisplay>,
    private val onTrackSelected: (TrackItemDisplay) -> Unit,
    private val onSetAsSecondary: ((TrackItemDisplay) -> Unit)? = null
) : RecyclerView.Adapter<TrackChoiceAdapter.TrackViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemTrackChoiceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TrackViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class TrackViewHolder(private val binding: ItemTrackChoiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TrackItemDisplay) {
            binding.tvTrackTitle.text = item.title
            binding.tvTrackSubtitle.text = item.subtitle
            binding.rbSelected.isChecked = item.isSelected

            if (item.isSelected) {
                binding.root.setBackgroundResource(R.drawable.bg_sort_option_selected)
                binding.tvTrackTitle.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
            } else {
                binding.root.setBackgroundResource(R.drawable.bg_sort_option_normal)
                binding.tvTrackTitle.setTextColor(ContextCompat.getColor(binding.root.context, R.color.on_surface))
            }

            if (onSetAsSecondary != null) {
                binding.btnSetAsSecondary.visibility = View.VISIBLE
                binding.btnSetAsSecondary.setOnClickListener {
                    onSetAsSecondary.invoke(item)
                }
            } else {
                binding.btnSetAsSecondary.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onTrackSelected(item)
            }
        }
    }
}
