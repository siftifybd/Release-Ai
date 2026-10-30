package com.jarvis.assistant.features

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.databinding.ItemFeatureRowBinding

class FeatureAdapter(
    private var features: List<JarvisFeatureItem>,
    private var pinnedIds: Set<String>,
    private val onFeatureClick: (JarvisFeatureItem) -> Unit,
    private val onPinToggle: (JarvisFeatureItem) -> Unit
) : RecyclerView.Adapter<FeatureAdapter.FeatureViewHolder>() {

    class FeatureViewHolder(val binding: ItemFeatureRowBinding) : RecyclerView.ViewHolder(binding.root)

    fun updateData(newFeatures: List<JarvisFeatureItem>, newPinnedIds: Set<String>) {
        this.features = newFeatures
        this.pinnedIds = newPinnedIds
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureViewHolder {
        val binding = ItemFeatureRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeatureViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeatureViewHolder, position: Int) {
        val item = features[position]
        val isStarred = pinnedIds.contains(item.id)

        holder.binding.tvFeatureId.text = "#${item.id}"
        holder.binding.tvFeatureTitle.text = item.title
        holder.binding.tvFeatureCategory.text = item.category
        holder.binding.tvRiskBadge.text = item.riskLevel.name
        holder.binding.tvPermBadge.text = item.requiredPermission

        val riskColor = when (item.riskLevel) {
            FeatureRiskLevel.LOW -> Color.parseColor("#4CAF50")
            FeatureRiskLevel.MEDIUM -> Color.parseColor("#FF9800")
            FeatureRiskLevel.HIGH -> Color.parseColor("#FF2A4B")
        }
        holder.binding.tvRiskBadge.setTextColor(riskColor)

        if (isStarred) {
            holder.binding.btnStar.setColorFilter(Color.parseColor("#FFD700"))
        } else {
            holder.binding.btnStar.setColorFilter(Color.parseColor("#666666"))
        }

        holder.itemView.setOnClickListener {
            onFeatureClick(item)
        }

        holder.binding.btnStar.setOnClickListener {
            onPinToggle(item)
        }
    }

    override fun getItemCount(): Int = features.size
}
