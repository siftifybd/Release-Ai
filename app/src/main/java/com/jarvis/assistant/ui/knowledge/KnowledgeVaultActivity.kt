package com.jarvis.assistant.ui.knowledge

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.R
import com.jarvis.assistant.databinding.ActivityKnowledgeVaultBinding
import com.jarvis.assistant.knowledge.KnowledgeItem
import com.jarvis.assistant.knowledge.KnowledgeVaultManager
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class KnowledgeVaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKnowledgeVaultBinding
    private lateinit var adapter: KnowledgeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        binding = ActivityKnowledgeVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        KnowledgeVaultManager.init(this)

        binding.btnBackKnowledge.setOnClickListener { finish() }

        binding.btnAddDocument.setOnClickListener {
            showAddKnowledgeDialog()
        }

        adapter = KnowledgeAdapter()
        binding.rvKnowledge.layoutManager = LinearLayoutManager(this)
        binding.rvKnowledge.adapter = adapter

        lifecycleScope.launch {
            KnowledgeVaultManager.items.collectLatest { list ->
                val query = binding.etSearchKnowledge.text.toString().trim()
                if (query.isEmpty()) {
                    adapter.submitList(list)
                } else {
                    adapter.submitList(KnowledgeVaultManager.search(query))
                }
            }
        }

        binding.etSearchKnowledge.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.submitList(KnowledgeVaultManager.search(s?.toString() ?: ""))
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun showAddKnowledgeDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val etTitle = EditText(this).apply {
            hint = "Document / Note Title"
        }
        val etContent = EditText(this).apply {
            hint = "Content, study notes or instructions"
            minLines = 3
        }
        val etCategory = EditText(this).apply {
            hint = "Category (Note, Document, Voice Journal)"
        }

        layout.addView(etTitle)
        layout.addView(etContent)
        layout.addView(etCategory)

        AlertDialog.Builder(this)
            .setTitle("Add to Knowledge Vault")
            .setView(layout)
            .setPositiveButton("Save Note") { _, _ ->
                val title = etTitle.text.toString().trim()
                val content = etContent.text.toString().trim()
                val category = etCategory.text.toString().trim()

                if (title.isNotEmpty() && content.isNotEmpty()) {
                    KnowledgeVaultManager.addItem(
                        context = this,
                        title = title,
                        content = content,
                        category = if (category.isNotEmpty()) category else "Personal Note"
                    )
                    Toast.makeText(this, "Saved to Knowledge Vault", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Title and content are required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class KnowledgeAdapter : RecyclerView.Adapter<KnowledgeAdapter.ViewHolder>() {
        private var items = listOf<KnowledgeItem>()

        fun submitList(list: List<KnowledgeItem>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_knowledge, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvCategory: TextView = itemView.findViewById(R.id.tvKnowledgeCategory)
            private val tvDate: TextView = itemView.findViewById(R.id.tvKnowledgeDate)
            private val tvTitle: TextView = itemView.findViewById(R.id.tvKnowledgeTitle)
            private val tvContent: TextView = itemView.findViewById(R.id.tvKnowledgeContent)
            private val tvSource: TextView = itemView.findViewById(R.id.tvKnowledgeSource)

            fun bind(item: KnowledgeItem) {
                tvCategory.text = item.category.uppercase()
                tvDate.text = item.formattedDate
                tvTitle.text = item.title
                tvContent.text = item.content
                tvSource.text = "Source: ${item.sourceName}"
            }
        }
    }
}
