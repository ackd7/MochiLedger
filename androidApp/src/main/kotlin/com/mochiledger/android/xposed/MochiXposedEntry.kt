package com.mochiledger.android.xposed

import android.app.Activity
import android.app.AndroidAppHelper
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.ConcurrentHashMap

/**
 * LSPosed / Xposed entry point for MochiLedger.
 *
 * Version-independent dual-layer hooks (no obfuscated private class names required):
 * 1. Notification layer: NotificationManager.notify() in WeChat & Alipay.
 * 2. Payment-Success UI layer: Activity.onResume() view-tree scan on payment result screens,
 *    allowing automatic bookkeeping even when system notifications are disabled.
 */
class MochiXposedEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            SELF_PACKAGE -> hookSelfStatus(lpparam)
            WECHAT_PACKAGE -> {
                hookPaymentNotifications(lpparam, WECHAT_PACKAGE)
                hookPaymentSuccessUi(WECHAT_PACKAGE)
            }
            ALIPAY_PACKAGE -> {
                hookPaymentNotifications(lpparam, ALIPAY_PACKAGE)
                hookPaymentSuccessUi(ALIPAY_PACKAGE)
            }
        }
    }

    private fun hookSelfStatus(lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching {
            XposedHelpers.findAndHookMethod(
                "com.mochiledger.android.xposed.XposedStatus",
                lpparam.classLoader,
                "isModuleActive",
                XC_MethodReplacement.returnConstant(true),
            )
            XposedBridge.log("$TAG: Self-hook active in $SELF_PACKAGE")
        }.onFailure {
            XposedBridge.log("$TAG: Self-hook failed: ${it.message}")
        }
    }

    private fun hookPaymentNotifications(
        lpparam: XC_LoadPackage.LoadPackageParam,
        targetPackage: String,
    ) {
        runCatching {
            XposedHelpers.findAndHookMethod(
                NotificationManager::class.java,
                "notify",
                String::class.java,
                Int::class.javaPrimitiveType,
                Notification::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val notification = param.args.getOrNull(2) as? Notification ?: return
                        dispatchNotificationEvent(targetPackage, notification)
                    }
                },
            )
            XposedBridge.log("$TAG: Hooked NotificationManager.notify in $targetPackage")
        }.onFailure {
            XposedBridge.log("$TAG: Notification hook failed in $targetPackage: ${it.message}")
        }
    }

    private fun hookPaymentSuccessUi(targetPackage: String) {
        runCatching {
            XposedHelpers.findAndHookMethod(
                Activity::class.java,
                "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        val className = activity.javaClass.name
                        if (!isPaymentScreen(className)) return

                        val handler = Handler(Looper.getMainLooper())
                        // Scan twice to catch asynchronously rendered payment result screens
                        listOf(550L, 1400L).forEach { delayMs ->
                            handler.postDelayed(
                                {
                                    if (!activity.isFinishing && !activity.isDestroyed) {
                                        scanActivityPaymentResult(targetPackage, activity)
                                    }
                                },
                                delayMs,
                            )
                        }
                    }
                },
            )
            XposedBridge.log("$TAG: Hooked Activity.onResume UI payment capture in $targetPackage")
        }.onFailure {
            XposedBridge.log("$TAG: UI hook failed in $targetPackage: ${it.message}")
        }
    }

    private fun isPaymentScreen(className: String): Boolean {
        val lower = className.lowercase()
        return PAYMENT_ACTIVITY_HINTS.any { lower.contains(it) }
    }

    private fun scanActivityPaymentResult(sourcePackage: String, activity: Activity) {
        runCatching {
            val root = activity.window?.decorView ?: return
            val texts = ArrayList<String>(48)
            collectVisibleTexts(root, texts, maxItems = 48)
            if (texts.isEmpty()) return

            val joined = texts.joinToString(" ")
            val hasSuccessSignal = UI_SUCCESS_KEYWORDS.any { joined.contains(it) }
            val hasAmount = UI_AMOUNT_REGEX.containsMatchIn(joined)
            if (!hasSuccessSignal || !hasAmount) return

            // In-process cooldown dedupe (10s) so the two delayed scans don't broadcast twice
            val amountMatch = UI_AMOUNT_REGEX.find(joined)?.value.orEmpty()
            val dedupeKey = "$sourcePackage|$amountMatch"
            val now = System.currentTimeMillis()
            val lastSent = recentUiBroadcasts[dedupeKey] ?: 0L
            if (now - lastSent < 10_000L) return
            recentUiBroadcasts[dedupeKey] = now

            val structured = buildStructuredSummary(texts, joined)
            val title = if (sourcePackage == WECHAT_PACKAGE) "微信支付" else "支付宝交易提醒"
            sendToMochiLedger(
                sourcePackage = sourcePackage,
                title = title,
                content = structured.take(360),
                channelSource = "LSPOSED_UI",
            )
        }.onFailure {
            XposedBridge.log("$TAG: UI scan error: ${it.message}")
        }
    }

    /**
     * Extracts structured merchant, product/item description, and payment method from sequential
     * TextView nodes on WeChat/Alipay payment result and bill detail screens.
     */
    private fun buildStructuredSummary(texts: List<String>, fallbackJoined: String): String {
        var merchant = ""
        var product = ""
        var payMethod = ""

        for (i in texts.indices) {
            val cur = texts[i].trim().trimEnd(':', '：')
            val next = texts.getOrNull(i + 1)?.trim().orEmpty()
            if (next.isEmpty() || next.length > 60) continue

            when {
                merchant.isEmpty() && cur in MERCHANT_LABELS -> merchant = next
                product.isEmpty() && cur in PRODUCT_LABELS -> product = next
                payMethod.isEmpty() && cur in PAY_METHOD_LABELS -> payMethod = next
            }
        }

        // Heuristic: on WeChat/Alipay payment result screens, the text right after "支付成功"/"付款成功"
        // or right before the ¥ amount is often the merchant name.
        if (merchant.isEmpty()) {
            val amountIdx = texts.indexOfFirst { UI_AMOUNT_REGEX.containsMatchIn(it) }
            if (amountIdx > 0) {
                val prev = texts[amountIdx - 1].trim()
                if (prev.length in 2..30 &&
                    UI_SUCCESS_KEYWORDS.none { prev.contains(it) } &&
                    prev !in listOf("完成", "返回商家", "查看账单", "支付方式", "付款方式")
                ) {
                    merchant = prev
                }
            }
        }

        val prefixParts = mutableListOf<String>()
        if (merchant.isNotBlank()) prefixParts.add("商户：$merchant")
        if (product.isNotBlank()) prefixParts.add("商品：$product")
        if (payMethod.isNotBlank()) prefixParts.add("付款方式：$payMethod")

        return if (prefixParts.isNotEmpty()) {
            "${prefixParts.joinToString(" ")} $fallbackJoined"
        } else {
            fallbackJoined
        }
    }

    private fun collectVisibleTexts(view: View, out: MutableList<String>, maxItems: Int) {
        if (out.size >= maxItems || view.visibility != View.VISIBLE) return
        if (view is TextView) {
            val s = view.text?.toString()?.trim().orEmpty()
            if (s.isNotEmpty() && s.length <= 80) {
                out.add(s)
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectVisibleTexts(view.getChildAt(i), out, maxItems)
                if (out.size >= maxItems) break
            }
        }
    }

    private fun dispatchNotificationEvent(sourcePackage: String, notification: Notification) {
        runCatching {
            val extras = notification.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            val content = bigText.ifBlank { text }

            if (title.isBlank() && content.isBlank()) return

            val merged = "$title $content"
            if (!looksLikePayment(merged)) return

            sendToMochiLedger(
                sourcePackage = sourcePackage,
                title = title,
                content = content,
                channelSource = "LSPOSED",
            )
        }.onFailure {
            XposedBridge.log("$TAG: Dispatch error: ${it.message}")
        }
    }

    private fun sendToMochiLedger(
        sourcePackage: String,
        title: String,
        content: String,
        channelSource: String,
    ) {
        val appContext = AndroidAppHelper.currentApplication()?.applicationContext ?: return
        val intent = Intent(ACTION_AUTO_RECORD).apply {
            component = ComponentName(SELF_PACKAGE, RECEIVER_CLASS)
            putExtra(EXTRA_PACKAGE, sourcePackage)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_CONTENT, content)
            putExtra(EXTRA_CHANNEL_SOURCE, channelSource)
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
        appContext.sendBroadcast(intent)
    }

    private fun looksLikePayment(text: String): Boolean {
        return PAYMENT_HINTS.any { text.contains(it) }
    }

    companion object {
        private const val TAG = "MochiLedger-LSPosed"
        const val SELF_PACKAGE = "com.mochiledger.android"
        const val RECEIVER_CLASS = "com.mochiledger.android.service.AutoRecordReceiver"
        const val WECHAT_PACKAGE = "com.tencent.mm"
        const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"

        const val ACTION_AUTO_RECORD = "com.mochiledger.ACTION_AUTO_RECORD"
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_CHANNEL_SOURCE = "extra_source"
        const val EXTRA_TIMESTAMP = "extra_timestamp"

        private val recentUiBroadcasts = ConcurrentHashMap<String, Long>()

        private val PAYMENT_HINTS = listOf(
            "微信支付", "支付宝", "付款", "收款", "消费", "支出", "花呗", "余额宝", "转账", "退款", "扣款", "到账", "元", "¥", "￥",
        )

        private val PAYMENT_ACTIVITY_HINTS = listOf(
            "pay", "wallet", "cashier", "remittance", "transfer", "luckymoney", "result", "trade", "bill", "receipt",
        )

        private val UI_SUCCESS_KEYWORDS = listOf(
            "支付成功", "付款成功", "收款成功", "转账成功", "交易成功", "已支付", "已付款", "收款到账",
        )

        private val UI_AMOUNT_REGEX = Regex("""(?:¥|￥)\s*[0-9]+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?\s*元""")

        private val MERCHANT_LABELS = setOf("商户", "商户全称", "商家", "收款方", "付款给", "交易对方", "收款商家")
        private val PRODUCT_LABELS = setOf("商品", "商品说明", "商品名称", "商品信息", "订单信息", "备注")
        private val PAY_METHOD_LABELS = setOf("支付方式", "付款方式", "扣款方式")
    }
}
