package com.jarvis.assistant.ui.home

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jarvis.assistant.R
import com.jarvis.assistant.background.JarvisConversationService
import com.jarvis.assistant.data.model.ConversationState
import com.jarvis.assistant.databinding.ActivityMainBinding
import com.jarvis.assistant.ui.chat.ChatFragment
import com.jarvis.assistant.ui.settings.SettingsFragment
import com.jarvis.assistant.util.PermissionHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAB_HOME = 0
        const val TAB_CHAT = 1
        const val TAB_SETTINGS = 2
        const val TAB_UPDATES = 3
    }

    private lateinit var binding: ActivityMainBinding
    val viewModel: MainViewModel by viewModels()
    private var activeSnackbar: Snackbar? = null

    private var currentTab = TAB_HOME
    private val homeFragment by lazy { HomeFragment() }
    private val chatFragment by lazy { ChatFragment() }
    private val settingsFragment by lazy { SettingsFragment() }
    private val updatesFragment by lazy { UpdatesFragment() }

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            checkPermissionsAndStart()
        } else {
            Toast.makeText(
                this,
                "Microphone permission is required for voice conversation.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            checkPermissionsAndStart()
        } else {
            Toast.makeText(
                this,
                "Notification permission is required to run in the background.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val mediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
    }

    val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            try {
                if (!JarvisConversationService.isSessionOn.value || JarvisConversationService.connectionStatus.value != "LIVE") {
                    Toast.makeText(this, "Start Jarvis and wait for LIVE before sharing your screen.", Toast.LENGTH_LONG).show()
                    return@registerForActivityResult
                }
                if (!JarvisConversationService.elevateToMediaProjection()) {
                    Toast.makeText(this, "Could not start Android's screen-capture service.", Toast.LENGTH_LONG).show()
                    return@registerForActivityResult
                }
                val projection = mediaProjectionManager.getMediaProjection(result.resultCode, result.data!!)
                val metrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
                val started = JarvisConversationService.startScreenCapture(projection, metrics)
                if (!started) {
                    JarvisConversationService.demoteFromMediaProjection()
                    Toast.makeText(this, "Android did not start screen capture. Please try again.", Toast.LENGTH_LONG).show()
                    return@registerForActivityResult
                }
                Toast.makeText(this, "Checking screen capture…", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    delay(2500L)
                    val captureManager = com.jarvis.assistant.vision.ScreenCaptureManager.getInstance()
                    if (!captureManager.isSharing.value) return@launch
                    if (captureManager.hasReceivedFrame.value) {
                        Toast.makeText(this@MainActivity, "Screen sharing is live. Jarvis is receiving screen frames.", Toast.LENGTH_LONG).show()
                    } else {
                        JarvisConversationService.stopScreenCapture()
                        Toast.makeText(this@MainActivity, "No screen frames arrived, so sharing was stopped. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error starting MediaProjection: ${e.message}", e)
                JarvisConversationService.demoteFromMediaProjection()
                Toast.makeText(this, "Screen capture failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    private var appliedThemeKey: String = ""
    private var appliedCustomHex: String = ""

    fun toggleScreenShare() {
        if (com.jarvis.assistant.vision.ScreenCaptureManager.getInstance().isSharing.value) {
            JarvisConversationService.stopScreenCapture()
            Toast.makeText(this, "Screen sharing stopped.", Toast.LENGTH_SHORT).show()
        } else {
            if (!JarvisConversationService.isSessionOn.value || JarvisConversationService.connectionStatus.value != "LIVE") {
                Toast.makeText(this, "Start Jarvis and wait for LIVE before sharing your screen.", Toast.LENGTH_LONG).show()
                return
            }
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        val prefs = com.jarvis.assistant.JarvisApp.instance.preferences
        appliedThemeKey = prefs.appTheme
        appliedCustomHex = prefs.customThemeColor
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(binding.rootMainLayout)

        currentTab = savedInstanceState?.getInt("CURRENT_TAB", TAB_HOME) ?: TAB_HOME

        val fm = supportFragmentManager
        val existingHome = fm.findFragmentByTag("TAB_0") as? HomeFragment
        val existingChat = fm.findFragmentByTag("TAB_1") as? ChatFragment
        val existingSettings = fm.findFragmentByTag("TAB_2") as? SettingsFragment
        val existingUpdates = fm.findFragmentByTag("TAB_3") as? UpdatesFragment

        val activeHome = existingHome ?: homeFragment
        val activeChat = existingChat ?: chatFragment
        val activeSettings = existingSettings ?: settingsFragment
        val activeUpdates = existingUpdates ?: updatesFragment

        val allTabs = listOf(
            TAB_HOME to activeHome,
            TAB_CHAT to activeChat,
            TAB_SETTINGS to activeSettings,
            TAB_UPDATES to activeUpdates
        )

        val trans = fm.beginTransaction()
        allTabs.forEach { (tabId, frag) ->
            if (!frag.isAdded) {
                trans.add(R.id.fragmentContainer, frag, "TAB_$tabId")
            }
            if (tabId == currentTab) {
                trans.show(frag)
            } else {
                trans.hide(frag)
            }
        }
        trans.commitAllowingStateLoss()

        setupNavigationDock()
        setupDrawerNavigation()
        updateDockUI(currentTab)
        observeViewModel()
        handleShareIntent(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                    closeDrawer()
                } else if (currentTab != TAB_HOME) {
                    selectTab(TAB_HOME)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                selectTab(TAB_CHAT)
                Toast.makeText(this, "Content shared to JARVIS for analysis", Toast.LENGTH_SHORT).show()
                viewModel.addSharedContent(sharedText)
            }
        }
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
    }

    fun closeDrawer() {
        binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
    }

    private fun setupDrawerNavigation() {
        com.jarvis.assistant.automation.agent.TaskReceiptManager.init(this)
        com.jarvis.assistant.automation.workflow.WorkflowManager.init(this)
        com.jarvis.assistant.knowledge.KnowledgeVaultManager.init(this)
        com.jarvis.assistant.notifications.NotificationDigestManager.init(this)
        com.jarvis.assistant.context.SmartContextEngine.init(this)

        val userName = com.jarvis.assistant.JarvisApp.instance.preferences.userName
        if (userName.isNotBlank()) {
            binding.drawerMenu.tvDrawerGreeting.text = "Welcome, $userName"
        }

        binding.drawerMenu.btnCloseDrawer.setOnClickListener { closeDrawer() }
        binding.drawerMenu.tvDrawerCloseAction.setOnClickListener { closeDrawer() }

        binding.drawerMenu.navDrawerNexus.setOnClickListener {
            closeDrawer()
            com.jarvis.assistant.nexus.ui.NexusActivity.start(this)
        }

        binding.drawerMenu.navDrawer500FeatureHub.setOnClickListener {
            closeDrawer()
            com.jarvis.assistant.features.FeatureHubActivity.start(this)
        }

        binding.drawerMenu.navDrawerBanglaPack.setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, com.jarvis.assistant.ui.settings.LanguagePackActivity::class.java))
        }

        binding.drawerMenu.navDrawerTaskReceipts.setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, com.jarvis.assistant.ui.audit.TaskReceiptActivity::class.java))
        }

        binding.drawerMenu.navDrawerWorkflows.setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, com.jarvis.assistant.ui.workflow.WorkflowStudioActivity::class.java))
        }

        binding.drawerMenu.navDrawerKnowledgeVault.setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, com.jarvis.assistant.ui.knowledge.KnowledgeVaultActivity::class.java))
        }

        binding.drawerMenu.navDrawerNotificationDigest.setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, com.jarvis.assistant.ui.notifications.NotificationDigestActivity::class.java))
        }

        binding.drawerMenu.navDrawerContextProfiles.setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, com.jarvis.assistant.ui.settings.ProfileSelectionActivity::class.java))
        }

        binding.drawerMenu.navDrawerDiagnostics.setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, com.jarvis.assistant.ui.diagnostics.DiagnosticsActivity::class.java))
        }

        binding.drawerMenu.navDrawerCameraCopilot.setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, com.jarvis.assistant.ui.vision.CameraCopilotActivity::class.java))
        }

        binding.drawerMenu.navDrawerPhoneLock.setOnClickListener {
            closeDrawer()
            com.jarvis.assistant.security.DeviceAdminPermissionHelper.openSetupFlow(this)
        }

        updateDrawerLockStatus()

        binding.drawerMenu.navDrawerMemoryCore.setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, com.jarvis.assistant.ui.memory.MemoryActivity::class.java))
        }

        binding.drawerMenu.navDrawerSettings.setOnClickListener {
            closeDrawer()
            selectTab(TAB_SETTINGS)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("CURRENT_TAB", currentTab)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSettings()
        updateDrawerLockStatus()
        val prefs = com.jarvis.assistant.JarvisApp.instance.preferences
        if (appliedThemeKey != prefs.appTheme || appliedCustomHex != prefs.customThemeColor) {
            appliedThemeKey = prefs.appTheme
            appliedCustomHex = prefs.customThemeColor
            recreate()
        }
    }

    private fun updateDrawerLockStatus() {
        try {
            val isAdminActive = com.jarvis.assistant.security.DeviceAdminPermissionHelper.isAdminActive(this)
            if (isAdminActive) {
                binding.drawerMenu.tvDrawerLockStatus.text = "ACTIVE"
                binding.drawerMenu.tvDrawerLockStatus.setTextColor(android.graphics.Color.parseColor("#00E676"))
                binding.drawerMenu.tvDrawerLockStatus.setBackgroundResource(com.jarvis.assistant.R.drawable.bg_jarvis_status_badge_green)
            } else {
                binding.drawerMenu.tvDrawerLockStatus.text = "SETUP"
                binding.drawerMenu.tvDrawerLockStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"))
                binding.drawerMenu.tvDrawerLockStatus.setBackgroundResource(com.jarvis.assistant.R.drawable.bg_jarvis_status_badge)
            }
        } catch (ignored: Exception) {}
    }

    private fun setupNavigationDock() {
        binding.tabHome.setOnClickListener { selectTab(TAB_HOME) }
        binding.tabChat.setOnClickListener { selectTab(TAB_CHAT) }
        binding.tabSettings.setOnClickListener { selectTab(TAB_SETTINGS) }
        binding.tabUpdates.setOnClickListener { selectTab(TAB_UPDATES) }

        binding.tabMic.setOnClickListener {
            handleMicOrOrbClick()
        }

        binding.tabMic.setOnLongClickListener {
            handleHoldMicOrOrb()
            true
        }
    }

    fun selectTab(tab: Int) {
        val previousTab = currentTab
        currentTab = tab

        val tabs = listOf(
            TAB_HOME to homeFragment,
            TAB_CHAT to chatFragment,
            TAB_SETTINGS to settingsFragment,
            TAB_UPDATES to updatesFragment
        )

        val trans = supportFragmentManager.beginTransaction()
        tabs.forEach { (tabId, frag) ->
            if (frag.isAdded) {
                if (tabId == tab) {
                    trans.show(frag)
                } else {
                    trans.hide(frag)
                }
            }
        }
        trans.commitAllowingStateLoss()

        tabs.forEach { (tabId, frag) ->
            val v = frag.view
            if (v != null) {
                if (tabId == tab) {
                    v.visibility = View.VISIBLE
                    com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(v)
                    if (previousTab != tab) {
                        v.alpha = 0f
                        v.translationX = if (tab > previousTab) 18f else -18f
                        v.animate().cancel()
                        v.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(190L)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .start()
                    } else {
                        v.alpha = 1f
                        v.translationX = 0f
                    }
                } else {
                    v.animate().cancel()
                    v.visibility = View.GONE
                }
            }
        }

        updateDockUI(tab)
    }

    private fun updateDockUI(tab: Int) {
        val tvAccent = android.util.TypedValue()
        theme.resolveAttribute(R.attr.themeAccent, tvAccent, true)
        val activeColor = if (tvAccent.data != 0) tvAccent.data else ContextCompat.getColor(this, R.color.accent_sapphire)

        val tvMuted = android.util.TypedValue()
        theme.resolveAttribute(R.attr.themeTextMuted, tvMuted, true)
        val inactiveColor = if (tvMuted.data != 0) tvMuted.data else ContextCompat.getColor(this, R.color.text_subtle)

        val tvPrimary = android.util.TypedValue()
        theme.resolveAttribute(R.attr.themeTextPrimary, tvPrimary, true)
        val activeTextColor = if (tvPrimary.data != 0) tvPrimary.data else android.graphics.Color.WHITE

        val navItems = listOf(
            Triple(TAB_HOME, binding.pillTabHome, Pair(binding.ivTabHome, binding.tvTabHome)),
            Triple(TAB_CHAT, binding.pillTabChat, Pair(binding.ivTabChat, binding.tvTabChat)),
            Triple(TAB_SETTINGS, binding.pillTabSettings, Pair(binding.ivTabSettings, binding.tvTabSettings)),
            Triple(TAB_UPDATES, binding.pillTabUpdates, Pair(binding.ivTabUpdates, binding.tvTabUpdates))
        )

        navItems.forEach { (tabId, pill, views) ->
            val isSelected = tabId == tab
            val (iv, tv) = views
            if (isSelected) {
                pill.setBackgroundResource(R.drawable.bg_dock_item_active)
                pill.alpha = 0f
                pill.scaleX = 0.88f
                pill.scaleY = 0.88f
                pill.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(240)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                    .start()
                iv.imageTintList = ColorStateList.valueOf(activeTextColor)
                tv.setTextColor(activeTextColor)
                tv.alpha = 1f
            } else {
                pill.animate().cancel()
                pill.setBackgroundResource(0)
                pill.alpha = 1f
                pill.scaleX = 1f
                pill.scaleY = 1f
                iv.imageTintList = ColorStateList.valueOf(inactiveColor)
                tv.setTextColor(inactiveColor)
                tv.alpha = 0.75f
            }
        }
    }

    fun handleMicOrOrbClick() {
        if (!JarvisConversationService.isSessionOn.value) {
            checkPermissionsAndStart()
        } else if (JarvisConversationService.conversationState.value == ConversationState.SPEAKING) {
            JarvisConversationService.interruptPlayback()
        } else {
            viewModel.toggleMicMute()
        }
    }

    fun handleHoldMicOrOrb() {
        binding.tabMic.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        if (JarvisConversationService.isSessionOn.value) {
            viewModel.turnOffSession()
        } else {
            checkPermissionsAndStart()
        }
    }

    fun handleTurnOff() {
        handleHoldMicOrOrb()
    }

    fun handlePowerClick() {
        handleHoldMicOrOrb()
    }

    private fun checkPermissionsAndStart() {
        if (!PermissionHelper.hasRecordAudioPermission(this)) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (!PermissionHelper.hasNotificationPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        if (!PermissionHelper.hasBatteryOptimizationPermission(this)) {
            showBatteryPermissionDialog()
            return
        }

        viewModel.toggleSession()
    }

    private fun showBatteryPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Unrestricted Battery Required")
            .setMessage("Jarvis requires Unrestricted Battery access so it can stay active in the background and respond to wake words.\n\nPlease allow it or open Settings.")
            .setPositiveButton("ALLOW") { _, _ ->
                PermissionHelper.requestBatteryOptimization(this)
            }
            .setNeutralButton("OPEN SETTINGS") { _, _ ->
                selectTab(TAB_SETTINGS)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.isMicMuted, viewModel.conversationState, viewModel.isSessionOn) { isMuted, state, isOn ->
                        Triple(isMuted, state, isOn)
                    }.collect { (isMuted, state, isOn) ->
                        val tvAccent = android.util.TypedValue()
                        theme.resolveAttribute(R.attr.themeAccent, tvAccent, true)
                        val accentColor = if (tvAccent.data != 0) tvAccent.data else ContextCompat.getColor(this@MainActivity, R.color.accent_sapphire)

                        val tvMuted = android.util.TypedValue()
                        theme.resolveAttribute(R.attr.themeTextMuted, tvMuted, true)
                        val inactiveColor = if (tvMuted.data != 0) tvMuted.data else ContextCompat.getColor(this@MainActivity, R.color.text_subtle)

                        if (!isOn) {
                            binding.ivTabMic.setImageResource(R.drawable.ic_mic_jarvis)
                            binding.ivTabMic.imageTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#94A3B8"))
                            binding.tvTabMic.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                            binding.pillTabMic.setBackgroundResource(R.drawable.bg_dock_mic_off)
                            binding.pillTabMic.animate().cancel()
                            binding.pillTabMic.alpha = 0.85f
                            binding.pillTabMic.scaleX = 1f
                            binding.pillTabMic.scaleY = 1f
                        } else if (isMuted) {
                            binding.ivTabMic.setImageResource(R.drawable.ic_mic_mute_jarvis)
                            binding.ivTabMic.imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
                            binding.tvTabMic.setTextColor(android.graphics.Color.WHITE)
                            binding.pillTabMic.setBackgroundResource(R.drawable.bg_dock_mic_muted)
                            binding.pillTabMic.animate().cancel()
                            binding.pillTabMic.alpha = 1f
                            binding.pillTabMic.scaleX = 1f
                            binding.pillTabMic.scaleY = 1f
                        } else {
                            binding.ivTabMic.setImageResource(R.drawable.ic_mic_jarvis)
                            binding.ivTabMic.imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
                            binding.tvTabMic.setTextColor(android.graphics.Color.WHITE)
                            binding.pillTabMic.setBackgroundResource(R.drawable.bg_dock_mic_highlight)
                            binding.pillTabMic.alpha = 1f
                            if (state == ConversationState.LISTENING || state == ConversationState.SPEAKING) {
                                binding.pillTabMic.animate()
                                    .scaleX(1.08f)
                                    .scaleY(1.08f)
                                    .setDuration(300)
                                    .start()
                            } else {
                                binding.pillTabMic.animate().cancel()
                                binding.pillTabMic.scaleX = 1f
                                binding.pillTabMic.scaleY = 1f
                            }
                        }
                    }
                }

                launch {
                    viewModel.eventFlow.collect { message ->
                        activeSnackbar?.dismiss()
                        activeSnackbar = Snackbar.make(binding.rootMainLayout, message, Snackbar.LENGTH_SHORT).apply {
                            setAnchorView(binding.bottomDock)
                            show()
                        }
                    }
                }
            }
        }
    }
}
