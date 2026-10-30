package com.jarvis.assistant.ui.notifications

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.R
import com.jarvis.assistant.databinding.ActivityNotificationDigestBinding
import com.jarvis.assistant.notifications.DigestNotification
import com.jarvis.assistant.notifications.NotificationDigestManager
import com.jarvis.assistant.notifications.NotificationPriority
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class NotificationDigestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationDigestBinding
    private lateinit var adapter: DigestAdapter
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        binding = ActivityNotificationDigestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationDigestManager.init(this)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }

        binding.btnBackDigest.setOnClickListener { finish() }

        binding.btnGrantListenerAccess.setOnClickListener {
            try {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Could not open Notification Listener settings", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearDigest.setOnClickListener {
            NotificationDigestManager.clearAll(this)
            android.widget.Toast.makeText(this, "Notification digest cleared", android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.btnReadAloudDigest.setOnClickListener {
            val briefing = NotificationDigestManager.generateBriefingText()
            tts?.speak(briefing, TextToSpeech.QUEUE_FLUSH, null, "digest_briefing")
        }

        adapter = DigestAdapter()
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter

        lifecycleScope.launch {
            NotificationDigestManager.notifications.collectLatest { list ->
                adapter.submitList(list)
            }
        }

        checkListenerPermission()
    }

    override fun onResume() {
        super.onResume()
        checkListenerPermission()
    }

    private fun checkListenerPermission() {
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val isGranted = flat != null && flat.contains(packageName)
        if (isGranted) {
            binding.tvListenerTitle.text = "Notification Access: ACTIVE"
            binding.tvListenerSubtitle.text = "JARVIS is currently receiving and organizing alerts."
            binding.btnGrantListenerAccess.text = "SETTINGS"
            binding.ivListenerStatus.setColorFilter(android.graphics.Color.parseColor("#00E676"))
        } else {
            binding.tvListenerTitle.text = "Notification Access: SETUP NEEDED"
            binding.tvListenerSubtitle.text = "Enable notification access in Android Settings to receive live alerts."
            binding.btnGrantListenerAccess.text = "GRANT ACCESS"
            binding.ivListenerStatus.setColorFilter(android.graphics.Color.parseColor("#FF2A4B"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }

    class DigestAdapter : RecyclerView.Adapter<DigestAdapter.ViewHolder>() {
        private var items = listOf<DigestNotification>()

        fun submitList(list: List<DigestNotification>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_digest_notification, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvApp: TextView = itemView.findViewById(R.id.tvNotifApp)
            private val tvPriority: TextView = itemView.findViewById(R.id.tvNotifPriority)
            private val tvTitle: TextView = itemView.findViewById(R.id.tvNotifTitle)
            private val tvSummary: TextView = itemView.findViewById(R.id.tvNotifSummary)

            fun bind(item: DigestNotification) {
                tvApp.text = item.appName
                tvTitle.text = item.title
                tvSummary.text = item.summary
                tvPriority.text = item.priority.name

                when (item.priority) {
                    NotificationPriority.IMPORTANT -> {
                        tvPriority.setBackgroundResource(R.drawable.bg_jarvis_status_badge)
                    }
                    NotificationPriority.LATER -> {
                        tvPriority.setBackgroundResource(R.drawable.bg_jarvis_chip)
                    }
                    NotificationPriority.SILENT -> {
                        tvPriority.setBackgroundResource(R.drawable.bg_jarvis_icon_square)
                    }
                }
            }
        }
    }
}
