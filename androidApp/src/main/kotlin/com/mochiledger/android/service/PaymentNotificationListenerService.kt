package com.mochiledger.android.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mochiledger.android.MochiApplication
import com.mochiledger.android.xposed.MochiXposedEntry
import com.mochiledger.android.xposed.XposedStatus
import com.mochiledger.shared.model.PaymentChannel

/**
 * Built-in full-app notification listener for MochiLedger.
 *
 * Automatically active whenever Auto-Track (`settings.enabled`) is turned on:
 * - Captures WeChat, Alipay, Meituan, JD, Taobao, UnionPay, Bank SMS & rule-matched apps.
 * - Automatically hands off WeChat/Alipay to LSPosed when LSPosed is actively running,
 *   while continuing to capture third-party order notifications for deduplication & enrichment.
 */
class PaymentNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotif = sbn ?: return
        if (statusBarNotif.isOngoing) return

        val pkg = statusBarNotif.packageName ?: return
        if (pkg == packageName || pkg == "android" || pkg.startsWith("com.android.systemui")) return

        val app = runCatching { MochiApplication.instance }.getOrNull() ?: return
        val settings = app.repository.state.value.metadata.autoTrack
        // Notification reading is automatically enabled whenever Auto-Track master switch is on
        if (!settings.enabled) return

        val isWeChat = pkg == PaymentChannel.WECHAT.packageName
        val isAlipay = pkg == PaymentChannel.ALIPAY.packageName
        val lsposedLive = settings.useLsposedMode && XposedStatus.isModuleActive()

        // Smart handoff: if LSPosed is actively hooking WeChat/Alipay, avoid duplicate broadcast
        if (isWeChat && lsposedLive && settings.hookWeChat) return
        if (isAlipay && lsposedLive && settings.hookAlipay) return

        // Respect per-app switches for WeChat/Alipay; all other apps are automatically monitored for suspected consumption
        if (isWeChat && !settings.hookWeChat) return
        if (isAlipay && !settings.hookAlipay) return

        val extras = statusBarNotif.notification?.extras ?: return
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
