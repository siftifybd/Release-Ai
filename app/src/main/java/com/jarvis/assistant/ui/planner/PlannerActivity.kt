package com.jarvis.assistant.ui.planner

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.R
import com.jarvis.assistant.databinding.ActivityPlannerBinding
import com.jarvis.assistant.planner.PlannerManager
import com.jarvis.assistant.planner.ReminderItem
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class PlannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlannerBinding
    private lateinit var adapter: PlannerAdapter
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        binding = ActivityPlannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PlannerManager.init(this)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }

        binding.btnBackPlanner.setOnClickListener { finish() }

        binding.btnVoiceBriefingPlanner.setOnClickListener {
            val briefing = PlannerManager.generateBriefingText()
            tts?.speak(briefing, TextToSpeech.QUEUE_FLUSH, null, "planner_briefing")
        }

        binding.btnAddNewTask.setOnClickListener {
            val title = binding.etNewTaskTitle.text.toString().trim()
            val time = binding.etNewTaskTime.text.toString().trim().ifBlank { "18:00" }
            if (title.isBlank()) {
                Toast.makeText(this, "Please enter a task title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PlannerManager.addReminder(
                context = this,
                title = title,
                timeLabel = time
            )
            binding.etNewTaskTitle.text?.clear()
            binding.etNewTaskTime.text?.clear()
            Toast.makeText(this, "Protocol added to schedule", Toast.LENGTH_SHORT).show()
        }

        adapter = PlannerAdapter(
            onToggleDone = { item -> PlannerManager.toggleCompleted(this, item.id) },
            onDelete = { item -> PlannerManager.deleteReminder(this, item.id) }
        )

        binding.rvPlannerTasks.layoutManager = LinearLayoutManager(this)
        binding.rvPlannerTasks.adapter = adapter

        lifecycleScope.launch {
            PlannerManager.reminders.collectLatest { list ->
                adapter.submitList(list)
                val completed = list.count { it.isCompleted }
                val pending = list.size - completed
                binding.tvPlannerCompletedCount.text = completed.toString()
                binding.tvPlannerPendingCount.text = pending.toString()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }

    class PlannerAdapter(
        private val onToggleDone: (ReminderItem) -> Unit,
        private val onDelete: (ReminderItem) -> Unit
    ) : RecyclerView.Adapter<PlannerAdapter.ViewHolder>() {

        private var items = listOf<ReminderItem>()

        fun submitList(list: List<ReminderItem>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_planner_task, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], onToggleDone, onDelete)
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val cbDone: CheckBox = itemView.findViewById(R.id.cbTaskDone)
            private val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
            private val tvPriority: TextView = itemView.findViewById(R.id.tvTaskPriority)
            private val tvNote: TextView = itemView.findViewById(R.id.tvTaskNote)
            private val tvTime: TextView = itemView.findViewById(R.id.tvTaskTime)
            private val tvRecurrence: TextView = itemView.findViewById(R.id.tvTaskRecurrence)
            private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteTask)

            fun bind(
                item: ReminderItem,
                onToggleDone: (ReminderItem) -> Unit,
                onDelete: (ReminderItem) -> Unit
            ) {
                cbDone.setOnCheckedChangeListener(null)
                cbDone.isChecked = item.isCompleted
                tvTitle.text = item.title
                tvPriority.text = item.priority

                if (item.isCompleted) {
                    tvTitle.paintFlags = tvTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    tvTitle.alpha = 0.5f
                } else {
                    tvTitle.paintFlags = tvTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    tvTitle.alpha = 1.0f
                }

                if (item.note.isNotBlank()) {
                    tvNote.visibility = View.VISIBLE
                    tvNote.text = item.note
                } else {
                    tvNote.visibility = View.GONE
                }

                tvTime.text = "🕒 ${item.timeLabel}"
                tvRecurrence.text = "🔁 ${item.recurrence}"

                cbDone.setOnCheckedChangeListener { _, _ -> onToggleDone(item) }
                btnDelete.setOnClickListener { onDelete(item) }
            }
        }
    }
}
