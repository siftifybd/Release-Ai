package com.jarvis.assistant.knowledge

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class KnowledgeItem(
    val id: String,
    val title: String,
    val content: String,
    val category: String, // "Document", "Note", "Voice Journal", "Routine"
    val timestamp: Long = System.currentTimeMillis(),
    val sourceName: String = "Local Memory"
) {
    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
}

object KnowledgeVaultManager {

    private const val PREFS_NAME = "jarvis_knowledge_vault"
    private const val KEY_DOCS = "key_knowledge_items"
    private val gson = Gson()

    private val _items = MutableStateFlow<List<KnowledgeItem>>(emptyList())
    val items: StateFlow<List<KnowledgeItem>> = _items.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_DOCS, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<KnowledgeItem>>() {}.type
                _items.value = gson.fromJson(json, type) ?: defaultKnowledge()
            } catch (e: Exception) {
                _items.value = defaultKnowledge()
            }
        } else {
            _items.value = defaultKnowledge()
            saveToPrefs(context, _items.value)
        }
    }

    fun addItem(context: Context, title: String, content: String, category: String, sourceName: String = "User Note"): KnowledgeItem {
        val item = KnowledgeItem(
            id = UUID.randomUUID().toString().take(8),
            title = title,
            content = content,
            category = category,
            timestamp = System.currentTimeMillis(),
            sourceName = sourceName
        )
        val updated = listOf(item) + _items.value
        _items.value = updated
        saveToPrefs(context, updated)
        return item
    }

    fun search(query: String): List<KnowledgeItem> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return _items.value
        return _items.value.filter {
            it.title.lowercase(Locale.ROOT).contains(q) ||
            it.content.lowercase(Locale.ROOT).contains(q) ||
            it.category.lowercase(Locale.ROOT).contains(q)
        }
    }

    fun deleteItem(context: Context, id: String) {
        val updated = _items.value.filter { it.id != id }
        _items.value = updated
        saveToPrefs(context, updated)
    }

    private fun saveToPrefs(context: Context, list: List<KnowledgeItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DOCS, gson.toJson(list)).apply()
    }

    private fun defaultKnowledge(): List<KnowledgeItem> {
        return listOf(
            KnowledgeItem(
                id = "doc-1",
                title = "Mark 85 Operational Directives",
                content = "Arc Core protocol operates at 100% efficiency. Offline local speech operates via Vosk engine. Cloud cognition runs through Gemini Live low-latency stream.",
                category = "Document",
                sourceName = "Stark_Operating_Manual.pdf"
            ),
            KnowledgeItem(
                id = "doc-2",
                title = "Voice Journal: Morning Reflections",
                content = "Focus for this week: complete study routines on time, maintain regular fitness sessions, and keep phone notifications filtered during focus hours.",
                category = "Voice Journal",
                sourceName = "Audio Recording #04"
            ),
            KnowledgeItem(
                id = "doc-3",
                title = "Emergency Contacts & Protocol",
                content = "Emergency contacts configured: Home, Office, Personal Assistant. Automated WhatsApp and Phone Call actions require owner voice confirmation.",
                category = "Note",
                sourceName = "Personal Security"
            )
        )
    }
}
