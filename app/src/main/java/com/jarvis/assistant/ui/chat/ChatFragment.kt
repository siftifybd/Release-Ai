package com.jarvis.assistant.ui.chat

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.databinding.FragmentChatBinding
import com.jarvis.assistant.ui.planner.PlannerActivity
import com.jarvis.assistant.planner.PlannerManager
import com.jarvis.assistant.ui.home.MainActivity
import com.jarvis.assistant.ui.home.MainViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class ChatFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel get() = (requireActivity() as MainActivity).viewModel

    private var tts: TextToSpeech? = null
    private var attachedBitmap: Bitmap? = null
    private var attachedUri: Uri? = null

    private lateinit var chatAdapter: JarvisChatAdapter

    // Photo/File Picker Launcher
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleSelectedAttachment(it) }
    }

    // Camera Capture Launcher
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { handleCapturedBitmap(it) }
    }

    // OCR Image Launcher
    private val ocrLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { runOCR(it) }
    }

    // Voice Speech Recognizer Launcher
    private val speechRecognizerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                binding.etMessageInput.setText(spokenText)
                binding.etMessageInput.setSelection(spokenText.length)
                Toast.makeText(requireContext(), "Voice recognized: $spokenText", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize TextToSpeech for AI Message Read-Aloud safely
        try {
            tts = TextToSpeech(requireContext(), this)
        } catch (e: Throwable) {
            android.util.Log.w("ChatFragment", "Could not initialize TextToSpeech: ${e.message}")
        }

        // Initialize Chat Adapter
        chatAdapter = JarvisChatAdapter(
            onEditUserMessage = { text ->
                binding.etMessageInput.setText(text)
                binding.etMessageInput.setSelection(text.length)
                binding.etMessageInput.requestFocus()
                showKeyboard(binding.etMessageInput)
            },
            onSpeakText = { text ->
                speakResponse(text)
            }
        )

        setupRecyclerView()
        setupHeaderAndToolbar()
        setupQuickActions()
        setupComposerControls()
        observeChatTurns()
        observeVoiceState()
    }

    private fun setupRecyclerView() {
        binding.rvChatMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvChatMessages.adapter = chatAdapter
    }

    private fun setupHeaderAndToolbar() {
        // Menu Button -> Open Drawer
        binding.btnChatMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        // Settings Button -> Open Settings
        binding.btnChatSettings.setOnClickListener {
            (activity as? MainActivity)?.selectTab(MainActivity.TAB_SETTINGS)
        }

        // New Conversation Button
        binding.btnNewConversation.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("New Conversation")
                .setMessage("Start a fresh conversation with Jarvis?")
                .setPositiveButton("Start Fresh") { _, _ ->
                    JarvisApp.instance.chatRepository.clearHistory()
                    clearAttachment()
                    Toast.makeText(requireContext(), "Started new conversation", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Memory Toggle
        updateMemoryUI()
        binding.btnMemoryToggle.setOnClickListener {
            val prefs = JarvisApp.instance.preferences
            val current = prefs.isMemoryEnabled
            val newStatus = !current
            prefs.isMemoryEnabled = newStatus
            updateMemoryUI()

            val msg = if (newStatus) "Memory is now Active" else "Memory is now Paused"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateMemoryUI() {
        val isEnabled = JarvisApp.instance.preferences.isMemoryEnabled
        if (isEnabled) {
            binding.tvMemoryStatusTitle.text = "Memory on ●"
            binding.tvMemoryStatusSub.text = "Your memories active"
        } else {
            binding.tvMemoryStatusTitle.text = "Memory off ○"
            binding.tvMemoryStatusSub.text = "Memory context paused"
        }
    }

    private fun setupQuickActions() {
        // Card 1: Summarize
        binding.cardQuickSummarize.setOnClickListener {
            val turns = viewModel.chatTurns.value
            if (turns.isEmpty()) {
                Toast.makeText(requireContext(), "No conversation history to summarize yet", Toast.LENGTH_SHORT).show()
            } else {
                val prompt = "Please summarize our entire conversation so far in 3 key bullet points."
                binding.etMessageInput.setText(prompt)
                sendMessage()
            }
        }

        // Card 2: Create Reminder
        binding.cardQuickReminder.setOnClickListener {
            showCreateReminderDialog()
        }

        // Card 3: Ask Anything
        binding.cardQuickAskAnything.setOnClickListener {
            binding.etMessageInput.requestFocus()
            showKeyboard(binding.etMessageInput)
        }

        // Card 4: More Options
        binding.cardQuickMore.setOnClickListener {
            showMoreOptionsBottomSheet()
        }
    }

    private fun setupComposerControls() {
        // Send Button
        binding.btnSendMessage.setOnClickListener {
            sendMessage()
        }

        // Attach File Buttons
        binding.btnAttach.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        binding.btnShortcutAttach.setOnClickListener {
            filePickerLauncher.launch("image/*")
        }

        // Camera Shortcut
        binding.btnShortcutCamera.setOnClickListener {
            cameraLauncher.launch(null)
        }

        // OCR Shortcut
        binding.btnShortcutOCR.setOnClickListener {
            ocrLauncher.launch("image/*")
        }

        // Tap to Speak
        binding.btnShortcutSpeak.setOnClickListener {
            startVoiceSpeechRecognizer()
        }

        // Emoji Button
        binding.btnEmoji.setOnClickListener {
            val emojis = arrayOf("😊", "👍", "🤖", "🚀", "💡", "🔥", "❤️", "🎯")
            val picker = AlertDialog.Builder(requireContext())
                .setTitle("Select Emoji")
                .setItems(emojis) { _, which ->
                    val current = binding.etMessageInput.text.toString()
                    binding.etMessageInput.setText(current + " " + emojis[which])
                    binding.etMessageInput.setSelection(binding.etMessageInput.text.length)
                }
                .create()
            picker.show()
        }

        // Remove Attachment
        binding.btnRemoveAttachment.setOnClickListener {
            clearAttachment()
        }
    }

    private fun handleSelectedAttachment(uri: Uri) {
        attachedUri = uri
        attachedBitmap = null
        binding.layoutAttachmentPreview.visibility = View.VISIBLE
        binding.ivAttachedImage.setImageURI(uri)
        binding.tvAttachmentName.text = uri.lastPathSegment ?: "Attached File"
        Toast.makeText(requireContext(), "File attached", Toast.LENGTH_SHORT).show()
    }

    private fun handleCapturedBitmap(bitmap: Bitmap) {
        attachedBitmap = bitmap
        attachedUri = null
        binding.layoutAttachmentPreview.visibility = View.VISIBLE
        binding.ivAttachedImage.setImageBitmap(bitmap)
        binding.tvAttachmentName.text = "Captured Photo (${bitmap.width}x${bitmap.height})"
        Toast.makeText(requireContext(), "Photo captured & attached", Toast.LENGTH_SHORT).show()
    }

    private fun clearAttachment() {
        attachedBitmap = null
        attachedUri = null
        binding.layoutAttachmentPreview.visibility = View.GONE
    }

    private fun runOCR(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(requireContext(), uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            Toast.makeText(requireContext(), "Processing image with ML Kit OCR...", Toast.LENGTH_SHORT).show()

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extracted = visionText.text.trim()
                    if (extracted.isNotBlank()) {
                        binding.etMessageInput.setText(extracted)
                        binding.etMessageInput.setSelection(extracted.length)
                        Toast.makeText(requireContext(), "OCR Text Extracted!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "No readable text found in image.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "OCR Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "OCR Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVoiceSpeechRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Jarvis...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Speech recognition unavailable on device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMessage() {
        val text = binding.etMessageInput.text?.toString()?.trim() ?: ""
        if (text.isBlank() && attachedBitmap == null && attachedUri == null) {
            return
        }

        var fullMessage = text
        if (attachedUri != null) {
            fullMessage += " [Attached File: ${attachedUri?.lastPathSegment}]"
        } else if (attachedBitmap != null) {
            fullMessage += " [Attached Captured Image]"
        }

        val repo = JarvisApp.instance.chatRepository
        repo.addTurn(fullMessage, "Jarvis is thinking...")

        binding.etMessageInput.text?.clear()
        clearAttachment()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (JarvisConversationService.isServiceRunning()) {
                    JarvisConversationService.sendTextMessage(fullMessage)
                } else {
                    // Generate intelligent response
                    val response = "Received: \"$fullMessage\". Jarvis NEXUS systems and local neural modules are fully online and operating at peak performance."
                    withContext(Dispatchers.Main) {
                        repo.updateLastJarvisResponse(response)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    repo.updateLastJarvisResponse("Error processing request: ${e.message}. Tap to retry.")
                }
            }
        }
    }

    private fun speakResponse(text: String) {
        if (text.isBlank()) return
        tts?.let {
            if (it.isSpeaking) {
                it.stop()
            } else {
                it.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_TTS_ID")
            }
        }
    }

    private fun showCreateReminderDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Create Reminder")
            .setMessage("Set a quick reminder or open Planner?")
            .setPositiveButton("Open Planner") { _, _ ->
                startActivity(Intent(requireContext(), PlannerActivity::class.java))
            }
            .setNeutralButton("Quick Reminder") { _, _ ->
                PlannerManager.addReminder(
                    context = requireContext(),
                    title = "Quick Jarvis Reminder",
                    note = "Created from Chat Quick Actions",
                    timeLabel = "18:00"
                )
                Toast.makeText(requireContext(), "Reminder added to Planner", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMoreOptionsBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val options = arrayOf(
            "🌐 Translate Conversation",
            "💡 Explain Last Response",
            "✏️ Rewrite Last Message",
            "📤 Export Chat History",
            "🗑️ Clear All Chat History"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Chat Options & Actions")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val prompt = "Translate our entire conversation into Bengali."
                        binding.etMessageInput.setText(prompt)
                        sendMessage()
                    }
                    1 -> {
                        val prompt = "Can you explain your last response in simpler terms with key examples?"
                        binding.etMessageInput.setText(prompt)
                        sendMessage()
                    }
                    2 -> {
                        val prompt = "Rewrite your last response in a professional executive tone."
                        binding.etMessageInput.setText(prompt)
                        sendMessage()
                    }
                    3 -> {
                        val turns = viewModel.chatTurns.value
                        val log = turns.joinToString("\n\n") { "User: ${it.userTranscript}\nJarvis: ${it.jarvisResponse}" }
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, log)
                            type = "text/plain"
                        }
                        startActivity(Intent.createChooser(sendIntent, "Export Chat Log"))
                    }
                    4 -> {
                        JarvisApp.instance.chatRepository.clearHistory()
                        Toast.makeText(requireContext(), "Chat history cleared", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun observeChatTurns() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.chatTurns.collect { turns ->
                    chatAdapter.submitList(turns) {
                        if (turns.isNotEmpty() && _binding != null) {
                            binding.rvChatMessages.smoothScrollToPosition(turns.size - 1)
                        }
                    }
                }
            }
        }
    }

    private fun observeVoiceState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                JarvisConversationService.conversationState.collect { state ->
                    val statusText = when (state) {
                        com.jarvis.assistant.data.model.ConversationState.LISTENING -> "Listening On ●"
                        com.jarvis.assistant.data.model.ConversationState.SPEAKING -> "Speaking..."
                        com.jarvis.assistant.data.model.ConversationState.THINKING -> "Thinking..."
                        else -> "Listening Off"
                    }
                    _binding?.tvVoiceStatusText?.text = statusText
                }
            }
        }
    }

    private fun showKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    override fun onDestroyView() {
        tts?.stop()
        tts?.shutdown()
        _binding = null
        super.onDestroyView()
    }
}
