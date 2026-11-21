package com.mochiledger.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.provider.Settings
import com.mochiledger.android.xposed.XposedStatus
import com.mochiledger.shared.data.LedgerRepository
import com.mochiledger.shared.platform.AndroidPlatformContext

class MochiApplication : Application() {

    lateinit var repository: LedgerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        AndroidPlatformContext.appContext = applicationContext
        AndroidPlatformContext.lsposedActiveProvider = { XposedStatus.isModuleActive() }
        AndroidPlatformContext.notificationListenerChecker = { isNotificationListenerEnabled() }

        repository = LedgerRepository()
        createNotificationChannel()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(packageName)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_AUTO_RECORD,
                "自动记账通知",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "通过 LSPosed 或系统通知自动识别微信/支付宝账单后显示提醒"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_AUTO_RECORD = "mochi_auto_record"

        @Volatile
        lateinit var instance: MochiApplication
            private set
    }
}
