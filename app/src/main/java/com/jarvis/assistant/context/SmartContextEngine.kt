package com.jarvis.assistant.context

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AssistantProfile(val title: String, val banglaTitle: String, val description: String, val emoji: String) {
    STANDARD("Standard Stark Core", "স্ট্যান্ডার্ড স্টারক কোর", "Full cloud AI cognition & visual HUD reactivity.", "⚡"),
    FOCUS("Focus Mode", "ফোকাস মোড", "Urgent alerts only, silent execution & minimal interruptions.", "🎯"),
    DRIVING("Driving Assistant", "ড্রাইভিং অ্যাসিস্ট্যান্ট", "Voice-first response, high volume & automated readouts.", "🚗"),
    MEETING("Meeting Protocol", "মিটিং প্রটোকল", "Muted voice output, visual notifications & discrete cards.", "🤝"),
    BATTERY_SAVER("Energy Conscious", "ব্যাটারি সেভার", "Prioritizes lightweight local offline processing.", "🔋")
}

data class ContextSnapshot(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isWifiConnected: Boolean,
    val isHeadsetConnected: Boolean,
    val isAudioMuted: Boolean,
    val activeProfile: AssistantProfile
)

object SmartContextEngine {

    private const val PREFS_NAME = "jarvis_smart_context"
    private const val KEY_PROFILE = "key_active_profile"

    private val _currentContext = MutableStateFlow(
        ContextSnapshot(
            batteryPercent = 85,
            isCharging = false,
            isWifiConnected = true,
            isHeadsetConnected = false,
            isAudioMuted = false,
            activeProfile = AssistantProfile.STANDARD
        )
    )
    val currentContext: StateFlow<ContextSnapshot> = _currentContext.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedProfileName = prefs.getString(KEY_PROFILE, AssistantProfile.STANDARD.name)
        val profile = try {
            AssistantProfile.valueOf(savedProfileName ?: AssistantProfile.STANDARD.name)
        } catch (e: Exception) {
            AssistantProfile.STANDARD
        }

        refreshSignals(context, profile)
    }

    fun setProfile(context: Context, profile: AssistantProfile) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PROFILE, profile.name).apply()
        refreshSignals(context, profile)
    }

    fun refreshSignals(context: Context, profile: AssistantProfile = _currentContext.value.activeProfile): ContextSnapshot {
        // Battery Signal
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, batteryFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 85
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // Network Signal
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: true

        // Audio Signal
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        @Suppress("DEPRECATION")
        val isHeadset = am?.isWiredHeadsetOn == true || am?.isBluetoothA2dpOn == true
        val isMuted = am?.ringerMode != AudioManager.RINGER_MODE_NORMAL

        val snapshot = ContextSnapshot(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            isWifiConnected = isWifi,
            isHeadsetConnected = isHeadset,
            isAudioMuted = isMuted,
            activeProfile = profile
        )

        _currentContext.value = snapshot
        return snapshot
    }
}
