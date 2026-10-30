package com.jarvis.assistant.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.R
import com.jarvis.assistant.context.AssistantProfile
import com.jarvis.assistant.context.ContextSnapshot
import com.jarvis.assistant.context.SmartContextEngine
import com.jarvis.assistant.databinding.ActivityProfileSelectionBinding
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSelectionBinding
    private lateinit var adapter: ProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        binding = ActivityProfileSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SmartContextEngine.init(this)

        binding.btnBackProfile.setOnClickListener { finish() }

        adapter = ProfileAdapter { selected ->
            SmartContextEngine.setProfile(this, selected)
            Toast.makeText(this, "Protocol '${selected.title}' activated", Toast.LENGTH_SHORT).show()
        }

        binding.rvProfiles.layoutManager = LinearLayoutManager(this)
        binding.rvProfiles.adapter = adapter
        adapter.submitList(AssistantProfile.values().toList())

        lifecycleScope.launch {
            SmartContextEngine.currentContext.collectLatest { ctx ->
                updateTelemetryUI(ctx)
                adapter.setActiveProfile(ctx.activeProfile)
            }
        }
    }

    private fun updateTelemetryUI(ctx: ContextSnapshot) {
        binding.tvSignalBattery.text = "🔋 Battery: ${ctx.batteryPercent}%${if (ctx.isCharging) " (Charging)" else ""}"
        binding.tvSignalNetwork.text = if (ctx.isWifiConnected) "📶 Wi-Fi: Active" else "📶 Cellular / Offline"
        binding.tvSignalAudio.text = if (ctx.isHeadsetConnected) "🎧 Headset Connected" else "🔊 Speaker Output"
        binding.tvSignalProfile.text = "${ctx.activeProfile.emoji} ${ctx.activeProfile.title}"
    }

    class ProfileAdapter(private val onSelect: (AssistantProfile) -> Unit) : RecyclerView.Adapter<ProfileAdapter.ViewHolder>() {
        private var items = listOf<AssistantProfile>()
        private var active: AssistantProfile = AssistantProfile.STANDARD

        fun submitList(list: List<AssistantProfile>) {
            items = list
            notifyDataSetChanged()
        }

        fun setActiveProfile(profile: AssistantProfile) {
            active = profile
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_profile_card, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], items[position] == active, onSelect)
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvEmoji: TextView = itemView.findViewById(R.id.tvProfileEmoji)
            private val tvTitle: TextView = itemView.findViewById(R.id.tvProfileTitle)
            private val tvBanglaTitle: TextView = itemView.findViewById(R.id.tvProfileBanglaTitle)
            private val tvDesc: TextView = itemView.findViewById(R.id.tvProfileDesc)
            private val tvBadge: TextView = itemView.findViewById(R.id.tvProfileActiveBadge)

            fun bind(item: AssistantProfile, isActive: Boolean, onSelect: (AssistantProfile) -> Unit) {
                tvEmoji.text = item.emoji
                tvTitle.text = item.title
                tvBanglaTitle.text = item.banglaTitle
                tvDesc.text = item.description
                tvBadge.visibility = if (isActive) View.VISIBLE else View.GONE

                itemView.setOnClickListener {
                    onSelect(item)
                }
            }
        }
    }
}
