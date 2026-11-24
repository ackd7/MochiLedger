package com.mochiledger.android.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.mochiledger.android.MochiApplication
import com.mochiledger.android.xposed.MochiXposedEntry
import com.mochiledger.shared.model.PaymentChannel
import com.mochiledger.shared.model.RecordSource
import com.mochiledger.shared.model.formatCents

/**
 * Receives structured payment events broadcast by MochiXposedEntry (LSPosed) or NotificationListenerService,
 * parses them with the shared PaymentMessageParser, deduplicates, persists into SQLite, and notifies the user.
 */
class AutoRecordReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MochiXposedEntry.ACTION_AUTO_RECORD) return

        val pkg = intent.getStringExtra(MochiXposedEntry.EXTRA_PACKAGE)
        val title = intent.getStringExtra(MochiXposedEntry.EXTRA_TITLE).orEmpty()
        val content = intent.getStringExtra(MochiXposedEntry.EXTRA_CONTENT).orEmpty()
        val sourceTag = intent.getStringExtra(MochiXposedEntry.EXTRA_CHANNEL_SOURCE).orEmpty()

        val repo = MochiApplication.instance.repository
        val settings = repo.state.value.metadata.autoTrack
        if (!settings.enabled) return
        if (sourceTag != "NOTIFICATION" && !settings.useLsposedMode) return
        if (sourceTag == "NOTIFICATION" && !settings.fallbackNotificationListener) return
        if (pkg == PaymentChannel.WECHAT.packageName && !settings.hookWeChat) return
        if (pkg == PaymentChannel.ALIPAY.packageName && !settings.hookAlipay) return
        if (sourceTag == "NOTIFICATION" &&
            pkg != PaymentChannel.WECHAT.packageName &&
            pkg != PaymentChannel.ALIPAY.packageName &&
            !settings.listenOtherApps
        ) {
            return
        }

        val recordSource = when {
            sourceTag == "NOTIFICATION" -> RecordSource.NOTIFICATION_LISTENER
            pkg == PaymentChannel.WECHAT.packageName -> RecordSource.WECHAT_LSPOSED
            pkg == PaymentChannel.ALIPAY.packageName -> RecordSource.ALIPAY_LSPOSED
            else -> RecordSource.NOTIFICATION_LISTENER
        }

        val parsed = repo.parsePaymentPreview(pkg, title, content, recordSource) ?: return
        val savedTx = repo.recordParsedPayment(parsed) ?: return

        if (settings.floatingConfirmEnabled) {
            QuickConfirmOverlayManager.show(context, savedTx.id, settings.floatingConfirmSeconds)
        }

        postConfirmationNotification(
            context = context,
            title = "墨记已自动记账 · ${parsed.channel.label}",
            body = "${savedTx.merchant.ifBlank { "支付记录" }} ${formatCents(savedTx.amountCents)}",
        )
    }

    private fun postConfirmationNotification(context: Context, title: String, body: String) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val notification = NotificationCompat.Builder(context, MochiApplication.CHANNEL_AUTO_RECORD)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % 100_000).toInt(), notification)
        }
    }
}
