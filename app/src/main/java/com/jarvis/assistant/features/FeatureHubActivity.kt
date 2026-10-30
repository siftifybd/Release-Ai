package com.jarvis.assistant.features

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.R
import com.jarvis.assistant.databinding.ActivityFeatureHubBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FeatureHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeatureHubBinding
    private val viewModel: FeatureHubViewModel by viewModels()

    private var categoryAdapter: CategoryAdapter? = null
    private var featureAdapter: FeatureAdapter? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, FeatureHubActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeatureHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnResetCategory.setOnClickListener {
            viewModel.setSelectedCategory(null)
            viewModel.setSearchQuery("")
            binding.etSearch.setText("")
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipAll.setOnClickListener { viewModel.setActiveFilter("ALL") }
        binding.chipStarred.setOnClickListener { viewModel.setActiveFilter("FAVORITES") }
        binding.chipPermissions.setOnClickListener { viewModel.setActiveFilter("PERMISSIONS") }
        binding.chipHighRisk.setOnClickListener { viewModel.setActiveFilter("HIGH_RISK") }

        categoryAdapter = CategoryAdapter(FeatureRegistry.CATEGORIES) { cat ->
            viewModel.setSelectedCategory(cat.title)
        }

        featureAdapter = FeatureAdapter(
            features = emptyList(),
            pinnedIds = emptySet(),
            onFeatureClick = { feat ->
                showFeatureDetailDialog(feat)
            },
            onPinToggle = { feat ->
                viewModel.togglePinFeature(feat.id)
            }
        )
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                updateFilterChipsUI(state.activeFilter)

                val filteredList = viewModel.getFilteredFeatures()

                if (state.selectedCategory == null && state.searchQuery.isBlank() && state.activeFilter == "ALL") {
                    // Show 25 Category Cards Grid
                    binding.tvSectionHeader.text = "25 CATEGORIES DIRECTORY"
                    binding.btnResetCategory.visibility = View.GONE
                    binding.rvFeatures.layoutManager = GridLayoutManager(this@FeatureHubActivity, 2)
                    binding.rvFeatures.adapter = categoryAdapter
                } else {
                    // Show Features List
                    val headerText = if (state.selectedCategory != null) {
                        "CATEGORY: ${state.selectedCategory?.uppercase()} (${filteredList.size} ITEMS)"
                    } else {
                        "FOUND ${filteredList.size} FEATURES"
                    }
                    binding.tvSectionHeader.text = headerText
                    binding.btnResetCategory.visibility = View.VISIBLE
                    binding.rvFeatures.layoutManager = LinearLayoutManager(this@FeatureHubActivity)
                    binding.rvFeatures.adapter = featureAdapter
                    featureAdapter?.updateData(filteredList, state.pinnedFeatureIds)
                }
            }
        }
    }

    private fun updateFilterChipsUI(activeFilter: String) {
        val selectedBg = Color.parseColor("#FF2A4B")
        val unselectedBg = Color.parseColor("#160306")

        binding.chipAll.setBackgroundColor(if (activeFilter == "ALL") selectedBg else unselectedBg)
        binding.chipStarred.setBackgroundColor(if (activeFilter == "FAVORITES") selectedBg else unselectedBg)
        binding.chipPermissions.setBackgroundColor(if (activeFilter == "PERMISSIONS") selectedBg else unselectedBg)
        binding.chipHighRisk.setBackgroundColor(if (activeFilter == "HIGH_RISK") selectedBg else unselectedBg)
    }

    private fun isPermissionGranted(permissionName: String): Boolean {
        if (permissionName == "None" || permissionName.isBlank()) return true
        val manifestPerm = when (permissionName) {
            "RECORD_AUDIO" -> android.Manifest.permission.RECORD_AUDIO
            "CAMERA" -> android.Manifest.permission.CAMERA
            "ACCESS_FINE_LOCATION" -> android.Manifest.permission.ACCESS_FINE_LOCATION
            "READ_CONTACTS" -> android.Manifest.permission.READ_CONTACTS
            "POST_NOTIFICATIONS" -> if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.POST_NOTIFICATIONS else null
            "BLUETOOTH_CONNECT" -> if (android.os.Build.VERSION.SDK_INT >= 31) android.Manifest.permission.BLUETOOTH_CONNECT else null
            "READ_CALENDAR" -> android.Manifest.permission.READ_CALENDAR
            "WRITE_CALENDAR" -> android.Manifest.permission.WRITE_CALENDAR
            "READ_SMS" -> android.Manifest.permission.READ_SMS
            "SEND_SMS" -> android.Manifest.permission.SEND_SMS
            "CALL_PHONE" -> android.Manifest.permission.CALL_PHONE
            "READ_CALL_LOG" -> android.Manifest.permission.READ_CALL_LOG
            else -> null
        } ?: return true

        return ContextCompat.checkSelfPermission(this, manifestPerm) == PackageManager.PERMISSION_GRANTED
    }

    private fun showFeatureDetailDialog(feature: JarvisFeatureItem) {
        val hasPerm = isPermissionGranted(feature.requiredPermission)
        val statusText = if (hasPerm) "PERMITTED / READY" else "NEEDS PERMISSION: ${feature.requiredPermission}"
        val statusColor = if (hasPerm) Color.parseColor("#10B981") else Color.parseColor("#EF4444")

        val dialogView = layoutInflater.inflate(R.layout.dialog_feature_detail, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvCategory = dialogView.findViewById<TextView>(R.id.tvCategory)
        val tvRiskLevel = dialogView.findViewById<TextView>(R.id.tvRiskLevel)
        val tvPermission = dialogView.findViewById<TextView>(R.id.tvPermission)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvStatus)
        val tvDescription = dialogView.findViewById<TextView>(R.id.tvDescription)

        val btnDryRun = dialogView.findViewById<Button>(R.id.btnDryRun)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)
        val btnExecute = dialogView.findViewById<Button>(R.id.btnExecute)

        tvTitle.text = "#${feature.id} • ${feature.title}"
        tvCategory.text = "Category: ${feature.category}"
        tvRiskLevel.text = "Risk Level: ${feature.riskLevel}"
        tvPermission.text = "Required Permission: ${feature.requiredPermission}"
        tvStatus.text = "Status: $statusText"
        tvStatus.setTextColor(statusColor)
        tvDescription.text = feature.description

        val dialog = MaterialAlertDialogBuilder(this, R.style.JarvisAlertDialogTheme)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnExecute.setOnClickListener {
            dialog.dismiss()
            viewModel.executeFeature(feature, isDryRun = false)
            Toast.makeText(this, "Executed: ${feature.title}", Toast.LENGTH_SHORT).show()
        }

        btnDryRun.setOnClickListener {
            dialog.dismiss()
            viewModel.executeFeature(feature, isDryRun = true)
            Toast.makeText(this, "Dry-Run Tested: ${feature.title}", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
