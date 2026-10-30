package com.jarvis.assistant.phone

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.security.PhoneLockController

class UnlockPromptActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnRetry: Button
    private lateinit var btnClose: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        setContentView(R.layout.activity_unlock_prompt)

        tvStatus = findViewById(R.id.tvUnlockStatus)
        btnRetry = findViewById(R.id.btnRetryDismiss)
        btnClose = findViewById(R.id.btnClosePrompt)

        btnRetry.setOnClickListener {
            requestSystemUnlock()
        }

        btnClose.setOnClickListener {
            finish()
        }

        requestSystemUnlock()
    }

    private fun requestSystemUnlock() {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard == null) {
            tvStatus.text = "Keyguard manager service unavailable on this device."
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tvStatus.text = "Requesting system keyguard dismissal..."
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        super.onDismissSucceeded()
                        recordUnlockResult("DISMISS_SUCCEEDED", "User unlocked via system keyguard")
                        tvStatus.text = "Authentication succeeded."
                        finish()
                    }

                    override fun onDismissCancelled() {
                        super.onDismissCancelled()
                        recordUnlockResult("DISMISS_CANCELLED", "User cancelled system keyguard prompt")
                        tvStatus.text = "Unlock dismissed or cancelled by user."
                    }

                    override fun onDismissError() {
                        super.onDismissError()
                        recordUnlockResult("DISMISS_ERROR", "Keyguard dismiss error encountered")
                        tvStatus.text = "Unable to dismiss keyguard automatically. Please swipe to unlock."
                    }
                }
            )
        } else {
            tvStatus.text = "Device awake. Please swipe or enter PIN to unlock."
            recordUnlockResult("LEGACY_WAKE", "Legacy wake flags applied")
        }
    }

    private fun recordUnlockResult(status: String, details: String) {
        try {
            val prefs = applicationContext.getSharedPreferences(PhoneLockController.PREFS_AUDIT, Context.MODE_PRIVATE)
            val current = prefs.getString(PhoneLockController.KEY_HISTORY, "") ?: ""
            val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            val entry = "$time | UNLOCK_REQUEST | $status | $details\n"
            val updated = (entry + current).take(2000)
            prefs.edit().putString(PhoneLockController.KEY_HISTORY, updated).apply()
        } catch (ignored: Exception) {}
    }
}
