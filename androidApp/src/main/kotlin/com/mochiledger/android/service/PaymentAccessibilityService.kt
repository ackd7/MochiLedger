package com.mochiledger.android.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.mochiledger.android.MochiApplication
import com.mochiledger.android.xposed.MochiXposedEntry
import com.mochiledger.android.xposed.XposedStatus
import com.mochiledger.shared.model.PaymentChannel
import com.mochiledger.shared.parser.ScreenNodeExtractor

/**
 * Real-time Accessibility screen reader for payment completion pages.
 *
 * Captures amount, merchant, product, and payment method from WeChat, Alipay, UnionPay,
 * Meituan, JD, Taobao, Bank apps, and rule-matched apps when a live payment result page appears.
 */
class PaymentAccessibilityService : AccessibilityService() {

    private var lastScanPackage: String = ""
    private var lastContentScanAt: Long = 0L
    private var lastCapturedSignature: String = ""
    private var lastCapturedAt: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        val pkg = ev.packageName?.toString()?.trim().orEmpty()
        if (pkg.isEmpty() || pkg == packageName || pkg == "android" || pkg.startsWith("com.android.systemui")) {
            return
        }

        val app = runCatching { MochiApplication.instance }.getOrNull() ?: return
        val settings = app.repository.state.value.metadata.autoTrack
        if (!settings.enabled || !settings.useAccessibilityMode) return

        val isWeChat = pkg == PaymentChannel.WECHAT.packageName
        val isAlipay = pkg == PaymentChannel.ALIPAY.packageName
        val lsposedLive = settings.useLsposedMode && XposedStatus.isModuleActive()

        if (isWeChat && !settings.hookWeChat) return
        if (isAlipay && !settings.hookAlipay) return
        if (isWeChat && lsposedLive) return
        if (isAlipay && lsposedLive) return
        if (!isWeChat && !isAlipay && !settings.listenOtherApps) return

        val now = System.currentTimeMillis()
        if (ev.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (pkg == lastScanPackage && now - lastContentScanAt < 400L) {
                return
            }
            lastContentScanAt = now
        }
        lastScanPackage = pkg

        val root = rootInActiveWindow ?: ev.source ?: return
        val nodeTexts = collectNodeTexts(root, maxNodes = 220)
        if (nodeTexts.size < 2) return

        val candidate = ScreenNodeExtractor.synthesizePaymentPayload(
            packageName = pkg,
            nodeTexts = nodeTexts,
            nowMillis = now,
        ) ?: return

        if (candidate.signature == lastCapturedSignature && now - lastCapturedAt < 15_000L) {
            return
        }
        lastCapturedSignature = candidate.signature
        lastCapturedAt = now

        val intent = Intent(MochiXposedEntry.ACTION_AUTO_RECORD).apply {
            component = ComponentName(packageName, MochiXposedEntry.RECEIVER_CLASS)
            putExtra(MochiXposedEntry.EXTRA_PACKAGE, pkg)
            putExtra(MochiXposedEntry.EXTRA_TITLE, candidate.title)
            putExtra(MochiXposedEntry.EXTRA_CONTENT, candidate.content)
            putExtra(MochiXposedEntry.EXTRA_CHANNEL_SOURCE, "ACCESSIBILITY")
            putExtra(MochiXposedEntry.EXTRA_TIMESTAMP, now)
        }
        sendBroadcast(intent)
    }

    override fun onInterrupt() = Unit

    private fun collectNodeTexts(root: AccessibilityNodeInfo, maxNodes: Int): List<String> {
        val result = ArrayList<String>(48)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited += 1

            val txt = node.text?.toString()?.trim().orEmpty()
            if (txt.isNotEmpty()) {
                result.add(txt)
            } else {
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                if (desc.isNotEmpty()) {
                    result.add(desc)
                }
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                if (visited + queue.size >= maxNodes) break
                val child = runCatching { node.getChild(i) }.getOrNull()
                if (child != null) {
                    queue.addLast(child)
                }
            }
        }
        return result
    }
}
