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
 * Full-app notification listener for MochiLedger.
 *
 * Supports two modes seamlessly:
 * 1. Standalone non-root mode: captures WeChat, Alipay, Meituan, JD, Taobao, UnionPay, Bank SMS & rule-matched apps.
 * 2. Cooperative mode alongside LSPosed: automatically hands off WeChat/Alipay to LSPosed when active,
 *    while continuing to capture Meituan, JD, Taobao, Bank apps, and other rule-matched notifications
 *    with smart cross-app deduplication & detail enrichment in LedgerRepository.
 */
class PaymentNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotif = sbn ?: return
        if (statusBarNotif.isOngoing) return

        val pkg = statusBarNotif.packageName ?: return
        if (pkg == packageName || pkg == "android" || pkg.startsWith("com.android.systemui")) return

        val settings = MochiApplication.instance.repository.state.value.metadata.autoTrack
        if (!settings.enabled || !settings.fallbackNotificationListener) return

        val isWeChat = pkg == PaymentChannel.WECHAT.packageName
        val isAlipay = pkg == PaymentChannel.ALIPAY.packageName
        val lsposedLive = settings.useLsposedMode && XposedStatus.isModuleActive()

        // Smart handoff: if LSPosed is actively hooking WeChat/Alipay, avoid duplicate broadcast from NotificationListener
        if (isWeChat && lsposedLive && settings.hookWeChat) return
        if (isAlipay && lsposedLive && settings.hookAlipay) return

        // Respect per-app switches
        if (isWeChat && !settings.hookWeChat) return
        if (isAlipay && !settings.hookAlipay) return
        if (!isWeChat && !isAlipay && !settings.listenOtherApps) return

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
