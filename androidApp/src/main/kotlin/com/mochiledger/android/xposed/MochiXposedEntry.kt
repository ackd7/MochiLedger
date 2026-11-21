package com.mochiledger.android.xposed

import android.app.AndroidAppHelper
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed / Xposed entry point for MochiLedger.
 *
 * Scopes:
 * - com.mochiledger.android (self-hook to report active module status)
 * - com.tencent.mm (WeChat Pay notifications & in-process payment alerts)
 * - com.eg.android.AlipayGphone (Alipay payment notifications & push alerts)
 */
class MochiXposedEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            SELF_PACKAGE -> hookSelfStatus(lpparam)
            WECHAT_PACKAGE -> hookPaymentNotifications(lpparam, WECHAT_PACKAGE)
            ALIPAY_PACKAGE -> hookPaymentNotifications(lpparam, ALIPAY_PACKAGE)
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
            // Hook NotificationManager.notify(String tag, int id, Notification notification)
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
            XposedBridge.log("$TAG: Hook failed in $targetPackage: ${it.message}")
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

            // Quick pre-filter so non-payment chat notifications don't wake MochiLedger
            val merged = "$title $content"
            if (!looksLikePayment(merged)) return

            val appContext = AndroidAppHelper.currentApplication()?.applicationContext ?: return
            val intent = Intent(ACTION_AUTO_RECORD).apply {
                component = ComponentName(SELF_PACKAGE, RECEIVER_CLASS)
                putExtra(EXTRA_PACKAGE, sourcePackage)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CONTENT, content)
                putExtra(EXTRA_CHANNEL_SOURCE, "LSPOSED")
                putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
            }
            appContext.sendBroadcast(intent)
        }.onFailure {
            XposedBridge.log("$TAG: Dispatch error: ${it.message}")
        }
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

        private val PAYMENT_HINTS = listOf(
            "微信支付", "支付宝", "付款", "收款", "消费", "支出", "花呗", "余额宝", "转账", "退款", "扣款", "到账", "元", "¥", "￥",
        )
    }
}
