package com.mochiledger.android.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mochiledger.android.MochiApplication
import com.mochiledger.android.xposed.MochiXposedEntry
import com.mochiledger.shared.model.PaymentChannel

/**
 * Fallback notification listener for Android devices without LSPosed/root.
 * Deduplication in LedgerRepository ensures no double-recording if both LSPosed and NotificationListener are enabled.
 */
class PaymentNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg != PaymentChannel.WECHAT.packageName && pkg != PaymentChannel.ALIPAY.packageName) return

        val settings = MochiApplication.instance.repository.state.value.metadata.autoTrack
        if (!settings.enabled || !settings.fallbackNotificationListener) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val content = bigText.ifBlank { text }
        if (title.isBlank() && content.isBlank()) return

        val intent = Intent(MochiXposedEntry.ACTION_AUTO_RECORD).apply {
            component = ComponentName(packageName, MochiXposedEntry.RECEIVER_CLASS)
            putExtra(MochiXposedEntry.EXTRA_PACKAGE, pkg)
            putExtra(MochiXposedEntry.EXTRA_TITLE, title)
            putExtra(MochiXposedEntry.EXTRA_CONTENT, content)
            putExtra(MochiXposedEntry.EXTRA_CHANNEL_SOURCE, "NOTIFICATION")
            putExtra(MochiXposedEntry.EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
        sendBroadcast(intent)
    }
}
