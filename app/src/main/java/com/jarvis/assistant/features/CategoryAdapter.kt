package com.jarvis.assistant.features

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.databinding.ItemFeatureCategoryBinding

class CategoryAdapter(
    private val categories: List<FeatureCategoryItem>,
    private val onCategoryClick: (FeatureCategoryItem) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(val binding: ItemFeatureCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemFeatureCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val cat = categories[position]
        holder.binding.tvCategoryTitle.text = cat.title
        holder.binding.tvCategoryDesc.text = cat.description
        holder.binding.tvCategoryCount.text = "${cat.featureCount} items"
        holder.binding.tvCategoryIcon.text = when (cat.id) {
            "cat_context" -> "📡"
            "cat_knowledge" -> "🧠"
            "cat_creative" -> "🎨"
            "cat_developer" -> "💻"
            "cat_device" -> "📱"
            "cat_workflow" -> "⚡"
            "cat_communication" -> "💬"
            "cat_learning" -> "🎓"
            "cat_safety" -> "🛡️"
            "cat_forensics" -> "🔍"
            "cat_home" -> "🏠"
            "cat_mobility" -> "🚗"
            "cat_finance" -> "💳"
            "cat_accessibility" -> "♿"
            "cat_media" -> "🎬"
            "cat_time" -> "⏱️"
            "cat_offline" -> "📶"
            "cat_camera" -> "📷"
            "cat_notification" -> "🔔"
            "cat_ecosystem" -> "🌐"
            "cat_emergency" -> "🚨"
            "cat_research" -> "🔬"
            "cat_hud" -> "🎛️"
            "cat_project" -> "📊"
            "cat_privacy" -> "🔐"
            else -> "✨"
        }

        holder.itemView.setOnClickListener {
            onCategoryClick(cat)
        }
    }

    override fun getItemCount(): Int = categories.size
}
