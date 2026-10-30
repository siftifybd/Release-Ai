package com.jarvis.assistant.ui.backup

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.backup.BackupManager
import com.jarvis.assistant.databinding.ActivityBackupRestoreBinding
import com.jarvis.assistant.util.ThemeManager
import java.io.BufferedReader
import java.io.InputStreamReader

class BackupRestoreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupRestoreBinding

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            saveBackupToUri(uri)
        }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            restoreBackupFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        binding = ActivityBackupRestoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackBackup.setOnClickListener { finish() }

        binding.btnExportBackup.setOnClickListener {
            val fileName = "JARVIS_Backup_${System.currentTimeMillis()}.json"
            createDocumentLauncher.launch(fileName)
        }

        binding.btnImportBackup.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
    }

    private fun saveBackupToUri(uri: Uri) {
        val jsonContent = BackupManager.generateBackupJson(
            context = this,
            includeMemories = binding.cbExportMemories.isChecked,
            includeReminders = binding.cbExportReminders.isChecked,
            includePrefs = binding.cbExportPrefs.isChecked
        )

        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(jsonContent.toByteArray(Charsets.UTF_8))
            }
            binding.tvRestoreStatusOutput.text = "STATUS: Backup export complete!\nSaved to: ${uri.lastPathSegment}"
            Toast.makeText(this, "Backup exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            binding.tvRestoreStatusOutput.text = "ERROR: Failed to save file: ${e.localizedMessage}"
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreBackupFromUri(uri: Uri) {
        try {
            val jsonContent = contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: ""

            val result = BackupManager.restoreBackupJson(this, jsonContent)
            if (result.success) {
                binding.tvRestoreStatusOutput.text = "STATUS: Restore Successful!\n${result.message}"
                Toast.makeText(this, "Protocols restored", Toast.LENGTH_SHORT).show()
            } else {
                binding.tvRestoreStatusOutput.text = "ERROR: ${result.message}"
                Toast.makeText(this, "Restore failed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            binding.tvRestoreStatusOutput.text = "ERROR: Unable to read file: ${e.localizedMessage}"
        }
    }
}
