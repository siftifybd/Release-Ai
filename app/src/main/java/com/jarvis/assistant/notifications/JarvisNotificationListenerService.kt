package com.jarvis.assistant.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class JarvisNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        try {
            val pkg = sbn.packageName ?: return
            // Ignore internal notifications from JARVIS to prevent loops
            if (pkg == packageName) return

            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
                ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

            if (title.isBlank() && text.isBlank()) return

            val pm = packageManager
            val appName = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                pkg.substringAfterLast('.')
            }

            // Determine priority
            val isHighPriority = notification.priority >= Notification.PRIORITY_HIGH ||
                    (pkg.contains("dialer", ignoreCase = true)) ||
                    (pkg.contains("messaging", ignoreCase = true)) ||
                    (pkg.contains("whatsapp", ignoreCase = true)) ||
                    (pkg.contains("telegram", ignoreCase = true)) ||
                    (pkg.contains("calendar", ignoreCase = true))

            val isLowPriority = notification.priority <= Notification.PRIORITY_LOW ||
                    (pkg.contains("download", ignoreCase = true)) ||
                    (pkg.contains("battery", ignoreCase = true))

            val priority = when {
                isHighPriority -> NotificationPriority.IMPORTANT
                isLowPriority -> NotificationPriority.SILENT
                else -> NotificationPriority.LATER
            }

            val summaryText = if (text.isNotBlank()) text else title
            NotificationDigestManager.addNotification(
                context = applicationContext,
                appName = appName,
                title = title.ifBlank { appName },
                summary = summaryText,
                priority = priority
            )
        } catch (e: Exception) {
            Log.e("JarvisNotifListener", "Failed to process notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
