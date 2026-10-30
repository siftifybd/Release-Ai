package com.jarvis.assistant.features

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FeatureHubUiState(
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val activeFilter: String = "ALL", // ALL, FAVORITES, PERMISSIONS, HIGH_RISK
    val pinnedFeatureIds: Set<String> = emptySet(),
    val recentFeatureIds: List<String> = emptyList(),
    val selectedFeatureForDetail: JarvisFeatureItem? = null,
    val lastExecutionLog: String? = null,
    val isExecuting: Boolean = false
)

class FeatureHubViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("jarvis_500_features_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(FeatureHubUiState())
    val uiState: StateFlow<FeatureHubUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        val pinned = prefs.getStringSet("pinned_feature_ids", emptySet()) ?: emptySet()
        val recentCsv = prefs.getString("recent_feature_ids", "") ?: ""
        val recentList = if (recentCsv.isBlank()) emptyList() else recentCsv.split(",").take(10)

        _uiState.value = _uiState.value.copy(
            pinnedFeatureIds = pinned,
            recentFeatureIds = recentList
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSelectedCategory(category: String?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun setActiveFilter(filter: String) {
        _uiState.value = _uiState.value.copy(activeFilter = filter)
    }

    fun togglePinFeature(featureId: String) {
        val current = _uiState.value.pinnedFeatureIds.toMutableSet()
        if (current.contains(featureId)) {
            current.remove(featureId)
        } else {
            current.add(featureId)
        }
        prefs.edit().putStringSet("pinned_feature_ids", current).apply()
        _uiState.value = _uiState.value.copy(pinnedFeatureIds = current)
    }

    fun selectFeatureDetail(feature: JarvisFeatureItem?) {
        _uiState.value = _uiState.value.copy(selectedFeatureForDetail = feature, lastExecutionLog = null)
        if (feature != null) {
            recordRecentFeature(feature.id)
        }
    }

    private fun recordRecentFeature(featureId: String) {
        val currentRecent = _uiState.value.recentFeatureIds.toMutableList()
        currentRecent.remove(featureId)
        currentRecent.add(0, featureId)
        val trimmed = currentRecent.take(10)
        prefs.edit().putString("recent_feature_ids", trimmed.joinToString(",")).apply()
        _uiState.value = _uiState.value.copy(recentFeatureIds = trimmed)
    }

    fun executeFeature(feature: JarvisFeatureItem, isDryRun: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExecuting = true)
            val log = FeatureExecutionEngine.execute(getApplication(), feature, isDryRun)
            recordRecentFeature(feature.id)
            _uiState.value = _uiState.value.copy(
                isExecuting = false,
                lastExecutionLog = log
            )
        }
    }

    fun getFilteredFeatures(): List<JarvisFeatureItem> {
        val state = _uiState.value
        var list = if (state.selectedCategory != null) {
            FeatureRegistry.getFeaturesForCategory(state.selectedCategory)
        } else {
            FeatureRegistry.ALL_FEATURES
        }

        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        it.category.lowercase().contains(q) ||
                        it.requiredPermission.lowercase().contains(q) ||
                        it.description.lowercase().contains(q)
            }
        }

        return when (state.activeFilter) {
            "FAVORITES" -> list.filter { state.pinnedFeatureIds.contains(it.id) }
            "PERMISSIONS" -> list.filter { it.requiredPermission != "None" }
            "HIGH_RISK" -> list.filter { it.riskLevel == FeatureRiskLevel.HIGH }
            else -> list
        }
    }
}
