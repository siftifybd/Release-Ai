package com.jarvis.assistant.ui.research

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.databinding.ActivityResearchTranslationBinding
import com.jarvis.assistant.research.ResearchEngine
import com.jarvis.assistant.research.ResearchResult
import com.jarvis.assistant.util.ThemeManager
import java.util.Locale

class ResearchTranslationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResearchTranslationBinding
    private var tts: TextToSpeech? = null
    private var currentResult: ResearchResult? = null
    private var isToBengali = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyTheme(this)
        binding = ActivityResearchTranslationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }

        binding.btnBackResearch.setOnClickListener { finish() }

        binding.btnRunResearch.setOnClickListener {
            val q = binding.etResearchQuery.text.toString().trim()
            if (q.isBlank()) {
                Toast.makeText(this, "Please enter a topic to research", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performResearch(q)
        }

        binding.chipQueryAi.setOnClickListener {
            binding.etResearchQuery.setText("Gemini Multimodal AI")
            performResearch("Gemini Multimodal AI")
        }

        binding.chipQueryQuantum.setOnClickListener {
            binding.etResearchQuery.setText("Quantum Computing Superposition")
            performResearch("Quantum Computing Superposition")
        }

        binding.chipQueryAndroid.setOnClickListener {
            binding.etResearchQuery.setText("Android 15 Modern Architecture")
            performResearch("Android 15 Modern Architecture")
        }

        binding.btnSpeakResearch.setOnClickListener {
            val summary = currentResult?.executiveSummary ?: binding.tvResearchSummary.text.toString()
            tts?.speak(summary, TextToSpeech.QUEUE_FLUSH, null, "research_tts")
        }

        binding.btnCopyResearch.setOnClickListener {
            val textToCopy = currentResult?.let {
                "${it.executiveSummary}\n\nKey Insights:\n" + it.keyFindings.joinToString("\n• ", prefix = "• ")
            } ?: binding.tvResearchSummary.text.toString()

            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("JARVIS Research", textToCopy))
            Toast.makeText(this, "Research copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        binding.btnToggleLanguageMode.setOnClickListener {
            isToBengali = !isToBengali
            binding.btnToggleLanguageMode.text = if (isToBengali) "EN ➔ BN" else "BN ➔ EN"
            val currentIn = binding.etTranslationInput.text.toString()
            if (currentIn.isNotBlank()) {
                doTranslate(currentIn)
            }
        }

        binding.btnTranslateAction.setOnClickListener {
            val input = binding.etTranslationInput.text.toString().trim()
            if (input.isBlank()) {
                Toast.makeText(this, "Enter text to translate", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            doTranslate(input)
        }

        binding.btnTranslateSummary.setOnClickListener {
            val summary = currentResult?.executiveSummary
            if (summary.isNullOrBlank()) {
                Toast.makeText(this, "Run research first to extract summary", Toast.LENGTH_SHORT).show()
            } else {
                binding.etTranslationInput.setText(summary)
                isToBengali = true
                binding.btnToggleLanguageMode.text = "EN ➔ BN"
                doTranslate(summary)
            }
        }

        // Run default research topic
        performResearch("Gemini Multimodal AI")
    }

    private fun performResearch(query: String) {
        val result = ResearchEngine.executeResearch(query)
        currentResult = result
        binding.tvResearchSummary.text = result.executiveSummary
        binding.tvResearchKeyPoints.text = result.keyFindings.joinToString("\n• ", prefix = "• ")

        val sourcesText = StringBuilder("Verified Sources:\n")
        result.sources.forEach {
            sourcesText.append("• [${it.domain}] ${it.title} - ${it.url}\n")
        }
        binding.tvResearchSources.text = sourcesText.toString().trimEnd()
    }

    private fun doTranslate(text: String) {
        val translated = ResearchEngine.translate(text, isToBengali)
        binding.tvTranslationOutput.text = translated
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
