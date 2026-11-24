package com.mochiledger.shared.platform

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.mochiledger.shared.model.LedgerSnapshot
import com.mochiledger.shared.model.LocalBackupFileInfo
import com.mochiledger.shared.model.SyncMetadata
import com.mochiledger.shared.sync.LedgerJson
import kotlinx.serialization.encodeToString
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

/**
 * Android runtime hooks wired by :androidApp Application class & LSPosed self-hook.
 */
object AndroidPlatformContext {
    @Volatile
    var appContext: Context? = null

    @Volatile
    var lsposedActiveProvider: () -> Boolean = { false }

    @Volatile
    var notificationListenerChecker: () -> Boolean = { false }

    @Volatile
    var accessibilityChecker: () -> Boolean = { false }

    @Volatile
    var openBackupFilePicker: (((Result<Pair<String, String>>) -> Unit) -> Unit)? = null

    @Volatile
    var openStatementCsvPicker: (((Result<Pair<String, String>>) -> Unit) -> Unit)? = null

    @Volatile
    var saveBackupFilePicker: ((String, String, (Result<String>) -> Unit) -> Unit)? = null
}

private class AndroidSqliteDriver(private val context: Context) : LedgerDatabaseDriver {
    private val helper = object : SQLiteOpenHelper(context, "mochiledger.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS mochi_kv (
                    k TEXT PRIMARY KEY,
                    v TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS accounts (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    initial_balance_cents INTEGER NOT NULL,
                    currency TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    is_deleted INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS categories (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    sort_order INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    is_deleted INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS transactions (
                    id TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    amount_cents INTEGER NOT NULL,
                    category_id TEXT NOT NULL,
                    account_id TEXT NOT NULL,
                    target_account_id TEXT,
                    timestamp INTEGER NOT NULL,
                    merchant TEXT NOT NULL,
                    note TEXT NOT NULL,
                    source TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    is_deleted INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    override val databasePath: String get() = context.getDatabasePath("mochiledger.db").absolutePath
    override val backupDirectoryPath: String
        get() = (context.getExternalFilesDir("backups") ?: File(context.filesDir, "backups"))
            .apply { mkdirs() }
            .absolutePath

    override fun loadSnapshot(): LedgerSnapshot? {
        return runCatching {
            helper.readableDatabase.rawQuery("SELECT v FROM mochi_kv WHERE k = ?", arrayOf("snapshot")).use { c ->
                if (c.moveToFirst()) {
                    LedgerJson.codec.decodeFromString<LedgerSnapshot>(c.getString(0))
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    override fun saveSnapshot(snapshot: LedgerSnapshot) {
        val db = helper.writableDatabase
        val json = LedgerJson.codec.encodeToString(snapshot)
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                "mochi_kv",
                null,
                ContentValues().apply {
                    put("k", "snapshot")
                    put("v", json)
                    put("updated_at", now)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            db.delete("accounts", null, null)
            db.delete("categories", null, null)
            db.delete("transactions", null, null)
            for (acc in snapshot.accounts) {
                db.insert(
                    "accounts",
                    null,
                    ContentValues().apply {
                        put("id", acc.id)
                        put("name", acc.name)
                        put("type", acc.type.name)
                        put("initial_balance_cents", acc.initialBalanceCents)
                        put("currency", acc.currency)
                        put("updated_at", acc.updatedAt)
                        put("is_deleted", if (acc.isDeleted) 1 else 0)
                    },
                )
            }
            for (cat in snapshot.categories) {
                db.insert(
                    "categories",
                    null,
                    ContentValues().apply {
                        put("id", cat.id)
                        put("name", cat.name)
                        put("type", cat.type.name)
                        put("sort_order", cat.sortOrder)
                        put("updated_at", cat.updatedAt)
                        put("is_deleted", if (cat.isDeleted) 1 else 0)
                    },
                )
            }
            for (tx in snapshot.transactions) {
                db.insert(
                    "transactions",
                    null,
                    ContentValues().apply {
                        put("id", tx.id)
                        put("type", tx.type.name)
                        put("amount_cents", tx.amountCents)
                        put("category_id", tx.categoryId)
                        put("account_id", tx.accountId)
                        put("target_account_id", tx.targetAccountId)
                        put("timestamp", tx.timestamp)
                        put("merchant", tx.merchant)
                        put("note", tx.note)
                        put("source", tx.source.name)
                        put("updated_at", tx.updatedAt)
                        put("is_deleted", if (tx.isDeleted) 1 else 0)
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun loadSyncMetadata(): SyncMetadata {
        return runCatching {
            helper.readableDatabase.rawQuery("SELECT v FROM mochi_kv WHERE k = ?", arrayOf("sync_meta")).use { c ->
                if (c.moveToFirst()) {
                    LedgerJson.codec.decodeFromString<SyncMetadata>(c.getString(0))
                } else {
                    SyncMetadata()
                }
            }
        }.getOrElse { SyncMetadata() }
    }

    override fun saveSyncMetadata(metadata: SyncMetadata) {
        val json = LedgerJson.codec.encodeToString(metadata)
        helper.writableDatabase.insertWithOnConflict(
            "mochi_kv",
            null,
            ContentValues().apply {
                put("k", "sync_meta")
                put("v", json)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun exportTextFile(suggestedFileName: String, content: String): Result<String> {
        return runCatching {
            val base = context.getExternalFilesDir("exports") ?: File(context.filesDir, "exports")
            base.mkdirs()
            val outFile = File(base, suggestedFileName)
            outFile.writeText(content, Charsets.UTF_8)
            outFile.absolutePath
        }
    }

    override fun saveLocalBackup(fileName: String, content: String, maxKeep: Int): Result<LocalBackupFileInfo> {
        return runCatching {
            val dir = File(backupDirectoryPath).apply { mkdirs() }
            val outFile = File(dir, fileName)
            outFile.writeText(content, Charsets.UTF_8)
            if (maxKeep > 0) {
                val allFiles = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
                    ?.sortedByDescending { it.lastModified() }
                    .orEmpty()
                val autoFiles = allFiles.filter { it.name.contains("-daily-") || it.name.contains("-scheduled-") }
                if (autoFiles.size > maxKeep) {
                    autoFiles.drop(maxKeep).forEach { runCatching { it.delete() } }
                }
            }
            LocalBackupFileInfo(
                fileName = outFile.name,
                absolutePath = outFile.absolutePath,
                sizeBytes = outFile.length(),
                lastModifiedMs = outFile.lastModified(),
                triggerType = detectBackupTrigger(outFile.name),
            )
        }
    }

    override fun listLocalBackups(): List<LocalBackupFileInfo> {
        val dir = File(backupDirectoryPath)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { f ->
                LocalBackupFileInfo(
                    fileName = f.name,
                    absolutePath = f.absolutePath,
                    sizeBytes = f.length(),
                    lastModifiedMs = f.lastModified(),
                    triggerType = detectBackupTrigger(f.name),
                )
            }
            .orEmpty()
    }

    override fun readTextFile(absolutePath: String): Result<String> {
        return runCatching { File(absolutePath).readText(Charsets.UTF_8) }
    }

    override fun deleteLocalBackup(absolutePath: String): Boolean {
        return runCatching { File(absolutePath).delete() }.getOrDefault(false)
    }

    private fun detectBackupTrigger(name: String): String = when {
        name.contains("-daily-") -> "daily"
        name.contains("-scheduled-") -> "scheduled"
        else -> "manual"
    }
}

actual object PlatformBridge {
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()

    actual fun randomUuid(): String = UUID.randomUUID().toString()

    actual fun createDatabaseDriver(): LedgerDatabaseDriver {
        val ctx = requireNotNull(AndroidPlatformContext.appContext) {
            "AndroidPlatformContext.appContext must be initialized in Application.onCreate()"
        }
        return AndroidSqliteDriver(ctx)
    }

    actual fun httpRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HttpResponseData {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.instanceFollowRedirects = true
        try {
            conn.requestMethod = method
        } catch (_: java.net.ProtocolException) {
            val field = HttpURLConnection::class.java.getDeclaredField("method")
            field.isAccessible = true
            field.set(conn, method)
        }
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..399) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        val respHeaders = conn.headerFields
            .filterKeys { it != null }
            .mapValues { it.value.joinToString(", ") }
        conn.disconnect()
        return HttpResponseData(code, text, respHeaders)
    }

    actual fun getAutoTrackRuntimeStatus(): AutoTrackRuntimeStatus {
        val ctx = AndroidPlatformContext.appContext
        val overlayGranted = ctx?.let { runCatching { Settings.canDrawOverlays(it) }.getOrDefault(false) } ?: false
        val batteryIgnored = ctx?.let {
            runCatching {
                val pm = it.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(it.packageName) ?: false
            }.getOrDefault(false)
        } ?: false
        val postNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ctx != null) {
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return AutoTrackRuntimeStatus(
            platformName = "Android ${Build.VERSION.RELEASE}",
            supportsLsposed = true,
            lsposedActive = AndroidPlatformContext.lsposedActiveProvider(),
            notificationListenerEnabled = AndroidPlatformContext.notificationListenerChecker(),
            accessibilityEnabled = AndroidPlatformContext.accessibilityChecker(),
            overlayPermissionGranted = overlayGranted,
            batteryOptimizationIgnored = batteryIgnored,
            postNotificationsGranted = postNotificationsGranted,
        )
    }

    actual fun openNotificationListenerSettings(): Boolean {
        val ctx = AndroidPlatformContext.appContext ?: return false
        return runCatching {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    actual fun openAccessibilitySettings(): Boolean {
        val ctx = AndroidPlatformContext.appContext ?: return false
        return runCatching {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    actual fun openOverlayPermissionSettings(): Boolean {
        val ctx = AndroidPlatformContext.appContext ?: return false
        return runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    actual fun openBatteryOptimizationSettings(): Boolean {
        val ctx = AndroidPlatformContext.appContext ?: return false
        return runCatching {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${ctx.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        }.recoverCatching {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(fallback)
            true
        }.getOrDefault(false)
    }

    actual fun openAppNotificationSettings(): Boolean {
        val ctx = AndroidPlatformContext.appContext ?: return false
        return runCatching {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    actual fun openUrl(url: String): Boolean {
        val ctx = AndroidPlatformContext.appContext ?: return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    actual fun defaultDeviceId(): String {
        val model = Build.MODEL?.replace(" ", "_") ?: "android"
        return "and-$model-${UUID.randomUUID().toString().take(6)}"
    }

    actual fun pickBackupFileContent(onResult: (Result<Pair<String, String>>) -> Unit) {
        val picker = AndroidPlatformContext.openBackupFilePicker
        if (picker != null) {
            picker(onResult)
        } else {
            onResult(Result.failure(IllegalStateException("文件选择器尚未就绪")))
        }
    }

    actual fun pickStatementCsvContent(onResult: (Result<Pair<String, String>>) -> Unit) {
        val picker = AndroidPlatformContext.openStatementCsvPicker
        if (picker != null) {
            picker(onResult)
        } else {
            onResult(Result.failure(IllegalStateException("账单文件选择器尚未就绪")))
        }
    }

    actual fun saveFileWithPicker(suggestedFileName: String, content: String, onResult: (Result<String>) -> Unit) {
        val saver = AndroidPlatformContext.saveBackupFilePicker
        if (saver != null) {
            saver(suggestedFileName, content, onResult)
        } else {
            // Fallback: save to app external exports directory
            val ctx = AndroidPlatformContext.appContext
            if (ctx != null) {
                val res = runCatching {
                    val base = ctx.getExternalFilesDir("exports") ?: File(ctx.filesDir, "exports")
                    base.mkdirs()
                    val out = File(base, suggestedFileName)
                    out.writeText(content, Charsets.UTF_8)
                    out.absolutePath
                }
                onResult(res)
            } else {
                onResult(Result.failure(IllegalStateException("存储不可用")))
            }
        }
    }
}
