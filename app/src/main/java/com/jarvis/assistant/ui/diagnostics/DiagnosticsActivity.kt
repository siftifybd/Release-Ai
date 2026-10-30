package com.jarvis.assistant.ui.diagnostics

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.R
import com.jarvis.assistant.databinding.ActivityDiagnosticsBinding
import com.jarvis.assistant.diagnostics.DeviceHealthChecker
import com.jarvis.assistant.diagnostics.DiagnosticItem
import com.jarvis.assistant.util.ThemeManager

class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiagnosticsBinding
    private lateinit var adapter: DiagnosticsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackDiagnostics.setOnClickListener { finish() }

        binding.btnReScanDiagnostics.setOnClickListener {
            runDiagnostics()
        }

        adapter = DiagnosticsAdapter { item ->
            if (item.settingsAction != null) {
                try {
                    val intent = if (item.settingsAction == android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                        Intent(item.settingsAction).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                    } else {
                        Intent(item.settingsAction)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to app settings
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    } catch (e2: Exception) {}
                }
            }
        }

        binding.rvDiagnostics.layoutManager = LinearLayoutManager(this)
        binding.rvDiagnostics.adapter = adapter

        runDiagnostics()
    }

    override fun onResume() {
        super.onResume()
        runDiagnostics()
    }

    private fun runDiagnostics() {
        val summary = DeviceHealthChecker.runDiagnostics(this)
        binding.tvHealthScorePercent.text = "${summary.scorePercent}%"
        binding.tvHealthStatusTitle.text = summary.overallStatus
        adapter.submitList(summary.items)
    }

    class DiagnosticsAdapter(private val onActionClick: (DiagnosticItem) -> Unit) : RecyclerView.Adapter<DiagnosticsAdapter.ViewHolder>() {
        private var items = listOf<DiagnosticItem>()

        fun submitList(list: List<DiagnosticItem>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_diagnostic_item, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], onActionClick)
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvTitle: TextView = itemView.findViewById(R.id.tvDiagnosticTitle)
            private val tvBadge: TextView = itemView.findViewById(R.id.tvDiagnosticStatusBadge)
            private val tvSub: TextView = itemView.findViewById(R.id.tvDiagnosticSubtitle)
            private val btnAction: Button = itemView.findViewById(R.id.btnDiagnosticAction)

            fun bind(item: DiagnosticItem, onActionClick: (DiagnosticItem) -> Unit) {
                tvTitle.text = item.title
                tvSub.text = item.subtitle
                tvBadge.text = item.statusText

                if (item.isHealthy) {
                    tvBadge.setBackgroundResource(R.drawable.bg_jarvis_status_badge_green)
                    btnAction.visibility = View.GONE
                } else {
                    tvBadge.setBackgroundResource(R.drawable.bg_jarvis_error_card)
                    if (item.actionLabel != null) {
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = item.actionLabel
                        btnAction.setOnClickListener { onActionClick(item) }
                    } else {
                        btnAction.visibility = View.GONE
                    }
                }
            }
        }
    }
}
