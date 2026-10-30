package com.jarvis.assistant.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.automation.command.CommandExecutor
import com.jarvis.assistant.automation.command.CommandNormalizer
import com.jarvis.assistant.automation.command.CommandParser
import com.jarvis.assistant.databinding.ActivityLanguagePackBinding
import com.jarvis.assistant.util.ThemeManager

class LanguagePackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLanguagePackBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        binding = ActivityLanguagePackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackLanguagePack.setOnClickListener { finish() }

        binding.btnTestBanglaExecute.setOnClickListener {
            val input = binding.etTestBanglaCommand.text.toString().trim()
            if (input.isBlank()) {
                Toast.makeText(this, "Enter a Bangla or Banglish phrase", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val normalized = CommandNormalizer.normalize(input)
            val parsed = CommandParser.parse(input)

            val resultText = StringBuilder()
            resultText.append("Input: \"$input\"\n")
            resultText.append("Normalized: \"$normalized\"\n")
            if (parsed.command != null) {
                resultText.append("Matched Action: ${parsed.command::class.java.simpleName}\n")
                resultText.append("Confidence: ${(parsed.confidence * 100).toInt()}%")
                // Execute action
                CommandExecutor.execute(this, parsed.command)
                Toast.makeText(this, "Executed: ${parsed.command::class.java.simpleName}", Toast.LENGTH_SHORT).show()
            } else {
                resultText.append("Status: Query requires Cloud AI dialogue.")
            }

            binding.tvBanglaTestResult.text = resultText.toString()
        }
    }
}
