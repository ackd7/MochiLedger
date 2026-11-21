package com.mochiledger.shared.platform

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.provider.Settings
import com.mochiledger.shared.model.LedgerSnapshot
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
        return AutoTrackRuntimeStatus(
            platformName = "Android ${Build.VERSION.RELEASE}",
            supportsLsposed = true,
            lsposedActive = AndroidPlatformContext.lsposedActiveProvider(),
            notificationListenerEnabled = AndroidPlatformContext.notificationListenerChecker(),
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

    actual fun defaultDeviceId(): String {
        val model = Build.MODEL?.replace(" ", "_") ?: "android"
        return "and-$model-${UUID.randomUUID().toString().take(6)}"
    }
}
