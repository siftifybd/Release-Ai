package com.jarvis.assistant.ui.audit

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.R
import com.jarvis.assistant.automation.agent.ReceiptStatus
import com.jarvis.assistant.automation.agent.TaskReceipt
import com.jarvis.assistant.automation.agent.TaskReceiptManager
import com.jarvis.assistant.databinding.ActivityTaskReceiptBinding
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TaskReceiptActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskReceiptBinding
    private lateinit var adapter: TaskReceiptAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        binding = ActivityTaskReceiptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TaskReceiptManager.init(this)

        binding.btnBackReceipt.setOnClickListener { finish() }

        binding.btnClearReceipts.setOnClickListener {
            TaskReceiptManager.clearReceipts(this)
        }

        adapter = TaskReceiptAdapter()
        binding.rvTaskReceipts.layoutManager = LinearLayoutManager(this)
        binding.rvTaskReceipts.adapter = adapter

        lifecycleScope.launch {
            TaskReceiptManager.receipts.collectLatest { list ->
                adapter.submitList(list)
            }
        }
    }

    class TaskReceiptAdapter : RecyclerView.Adapter<TaskReceiptAdapter.ViewHolder>() {
        private var items = listOf<TaskReceipt>()

        fun submitList(list: List<TaskReceipt>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_task_receipt, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvId: TextView = itemView.findViewById(R.id.tvReceiptId)
            private val tvStatusBadge: TextView = itemView.findViewById(R.id.tvReceiptStatusBadge)
            private val tvTaskName: TextView = itemView.findViewById(R.id.tvReceiptTaskName)
            private val tvTimeDuration: TextView = itemView.findViewById(R.id.tvReceiptTimeAndDuration)
            private val tvSteps: TextView = itemView.findViewById(R.id.tvReceiptSteps)
            private val tvEvidence: TextView = itemView.findViewById(R.id.tvReceiptEvidence)

            fun bind(item: TaskReceipt) {
                tvId.text = "#${item.id}"
                tvTaskName.text = item.taskName
                tvTimeDuration.text = "${item.formattedTime} • Duration: ${item.durationMs}ms"
                tvStatusBadge.text = item.statusBadgeText

                when (item.status) {
                    ReceiptStatus.VERIFIED -> {
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_jarvis_status_badge_green)
                    }
                    ReceiptStatus.PARTIALLY_VERIFIED -> {
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_jarvis_status_badge)
                    }
                    ReceiptStatus.FAILED -> {
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_jarvis_error_card)
                    }
                    ReceiptStatus.IN_PROGRESS -> {
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_jarvis_chip)
                    }
                }

                if (item.stepsExecuted.isNotEmpty()) {
                    tvSteps.visibility = View.VISIBLE
                    tvSteps.text = item.stepsExecuted.joinToString("\n") { "• $it" }
                } else {
                    tvSteps.visibility = View.GONE
                }

                if (item.evidenceSummary.isNotBlank()) {
                    tvEvidence.visibility = View.VISIBLE
                    tvEvidence.text = "Evidence: ${item.evidenceSummary}"
                } else {
                    tvEvidence.visibility = View.GONE
                }
            }
        }
    }
}
