package com.jarvis.assistant.ui.workflow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.R
import com.jarvis.assistant.automation.workflow.WorkflowManager
import com.jarvis.assistant.automation.workflow.WorkflowRoutine
import com.jarvis.assistant.automation.workflow.WorkflowStep
import com.jarvis.assistant.databinding.ActivityWorkflowStudioBinding
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorkflowStudioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkflowStudioBinding
    private lateinit var adapter: WorkflowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        binding = ActivityWorkflowStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WorkflowManager.init(this)

        binding.btnBackWorkflow.setOnClickListener { finish() }

        binding.btnNewRoutine.setOnClickListener {
            showCreateRoutineDialog()
        }

        adapter = WorkflowAdapter { routine ->
            binding.bannerExecutionProgress.visibility = View.VISIBLE
            binding.tvExecutingRoutineStatus.text = "Starting ${routine.name}..."

            WorkflowManager.executeRoutine(
                context = this,
                routine = routine,
                onStepProgress = { stepIdx, total, stepName ->
                    binding.tvExecutingRoutineStatus.text = "Step $stepIdx/$total: $stepName"
                },
                onComplete = { success, msg ->
                    binding.bannerExecutionProgress.visibility = View.GONE
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            )
        }

        binding.rvWorkflows.layoutManager = LinearLayoutManager(this)
        binding.rvWorkflows.adapter = adapter

        lifecycleScope.launch {
            WorkflowManager.routines.collectLatest { list ->
                adapter.submitList(list)
            }
        }
    }

    private fun showCreateRoutineDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val etName = EditText(this).apply {
            hint = "Routine Name (e.g. Work Session)"
        }
        val etBanglaName = EditText(this).apply {
            hint = "বাংলা নাম (e.g. কাজের প্রস্তুতি)"
        }
        val etCommands = EditText(this).apply {
            hint = "Comma-separated commands (e.g. weather, open youtube)"
        }

        layout.addView(etName)
        layout.addView(etBanglaName)
        layout.addView(etCommands)

        AlertDialog.Builder(this)
            .setTitle("Create Custom Routine")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val bnName = etBanglaName.text.toString().trim()
                val cmdList = etCommands.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }

                if (name.isNotEmpty() && cmdList.isNotEmpty()) {
                    val steps = cmdList.map { cmd ->
                        WorkflowStep(title = "Execute $cmd", commandText = cmd)
                    }
                    WorkflowManager.addCustomRoutine(
                        context = this,
                        name = name,
                        banglaName = if (bnName.isNotEmpty()) bnName else name,
                        description = "Custom automated sequence with ${steps.size} actions.",
                        steps = steps
                    )
                    Toast.makeText(this, "Routine '$name' saved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter name and at least 1 command", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class WorkflowAdapter(private val onPlayClick: (WorkflowRoutine) -> Unit) : RecyclerView.Adapter<WorkflowAdapter.ViewHolder>() {
        private var items = listOf<WorkflowRoutine>()

        fun submitList(list: List<WorkflowRoutine>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_workflow_routine, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], onPlayClick)
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvEmoji: TextView = itemView.findViewById(R.id.tvRoutineEmoji)
            private val tvName: TextView = itemView.findViewById(R.id.tvRoutineName)
            private val tvBanglaName: TextView = itemView.findViewById(R.id.tvRoutineBanglaName)
            private val tvDesc: TextView = itemView.findViewById(R.id.tvRoutineDescription)
            private val tvSteps: TextView = itemView.findViewById(R.id.tvRoutineStepsSummary)
            private val btnPlay: Button = itemView.findViewById(R.id.btnPlayRoutine)

            fun bind(item: WorkflowRoutine, onPlayClick: (WorkflowRoutine) -> Unit) {
                tvEmoji.text = item.iconEmoji
                tvName.text = item.name
                tvBanglaName.text = item.banglaName
                tvDesc.text = item.description
                tvSteps.text = "Sequence: ${item.steps.joinToString(" ➔ ") { it.commandText }}"

                btnPlay.setOnClickListener {
                    onPlayClick(item)
                }
            }
        }
    }
}
