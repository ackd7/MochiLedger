package com.mochiledger.shared.platform

import com.mochiledger.shared.model.LedgerSnapshot
import com.mochiledger.shared.model.LocalBackupFileInfo
import com.mochiledger.shared.model.SyncMetadata
import com.mochiledger.shared.sync.LedgerJson
import kotlinx.serialization.encodeToString
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

private class DesktopSqliteDriver(private val dbFile: File) : LedgerDatabaseDriver {
    override val databasePath: String get() = dbFile.absolutePath
    override val backupDirectoryPath: String get() = File(dbFile.parentFile, "backups").apply { mkdirs() }.absolutePath

    init {
        dbFile.parentFile?.mkdirs()
        withConnection { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS mochi_kv (
                        k TEXT PRIMARY KEY,
                        v TEXT NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
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
                st.execute(
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
                st.execute(
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
        }
    }

    private inline fun <T> withConnection(block: (Connection) -> T): T {
        return DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use(block)
    }

    override fun loadSnapshot(): LedgerSnapshot? {
        return runCatching {
            withConnection { conn ->
                conn.prepareStatement("SELECT v FROM mochi_kv WHERE k = 'snapshot'").use { ps ->
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            LedgerJson.codec.decodeFromString<LedgerSnapshot>(rs.getString(1))
                        } else {
                            null
                        }
                    }
                }
            }
        }.getOrNull()
    }

    override fun saveSnapshot(snapshot: LedgerSnapshot) {
        val json = LedgerJson.codec.encodeToString(snapshot)
        val now = System.currentTimeMillis()
        withConnection { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("INSERT OR REPLACE INTO mochi_kv(k, v, updated_at) VALUES('snapshot', ?, ?)").use { ps ->
                    ps.setString(1, json)
                    ps.setLong(2, now)
                    ps.executeUpdate()
                }
                conn.createStatement().use { st ->
                    st.executeUpdate("DELETE FROM accounts")
                    st.executeUpdate("DELETE FROM categories")
                    st.executeUpdate("DELETE FROM transactions")
                }
                conn.prepareStatement(
                    "INSERT INTO accounts(id, name, type, initial_balance_cents, currency, updated_at, is_deleted) VALUES(?,?,?,?,?,?,?)"
                ).use { ps ->
                    for (acc in snapshot.accounts) {
                        ps.setString(1, acc.id)
                        ps.setString(2, acc.name)
                        ps.setString(3, acc.type.name)
                        ps.setLong(4, acc.initialBalanceCents)
                        ps.setString(5, acc.currency)
                        ps.setLong(6, acc.updatedAt)
                        ps.setInt(7, if (acc.isDeleted) 1 else 0)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                conn.prepareStatement(
                    "INSERT INTO categories(id, name, type, sort_order, updated_at, is_deleted) VALUES(?,?,?,?,?,?)"
                ).use { ps ->
                    for (cat in snapshot.categories) {
                        ps.setString(1, cat.id)
                        ps.setString(2, cat.name)
                        ps.setString(3, cat.type.name)
                        ps.setInt(4, cat.sortOrder)
                        ps.setLong(5, cat.updatedAt)
                        ps.setInt(6, if (cat.isDeleted) 1 else 0)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                conn.prepareStatement(
                    "INSERT INTO transactions(id, type, amount_cents, category_id, account_id, target_account_id, timestamp, merchant, note, source, updated_at, is_deleted) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)"
                ).use { ps ->
                    for (tx in snapshot.transactions) {
                        ps.setString(1, tx.id)
                        ps.setString(2, tx.type.name)
                        ps.setLong(3, tx.amountCents)
                        ps.setString(4, tx.categoryId)
                        ps.setString(5, tx.accountId)
                        ps.setString(6, tx.targetAccountId)
                        ps.setLong(7, tx.timestamp)
                        ps.setString(8, tx.merchant)
                        ps.setString(9, tx.note)
                        ps.setString(10, tx.source.name)
                        ps.setLong(11, tx.updatedAt)
                        ps.setInt(12, if (tx.isDeleted) 1 else 0)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                conn.commit()
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            }
        }
    }

    override fun loadSyncMetadata(): SyncMetadata {
        return runCatching {
            withConnection { conn ->
                conn.prepareStatement("SELECT v FROM mochi_kv WHERE k = 'sync_meta'").use { ps ->
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            LedgerJson.codec.decodeFromString<SyncMetadata>(rs.getString(1))
                        } else {
                            SyncMetadata()
                        }
                    }
                }
            }
        }.getOrElse { SyncMetadata() }
    }

    override fun saveSyncMetadata(metadata: SyncMetadata) {
        val json = LedgerJson.codec.encodeToString(metadata)
        val now = System.currentTimeMillis()
        withConnection { conn ->
            conn.prepareStatement("INSERT OR REPLACE INTO mochi_kv(k, v, updated_at) VALUES('sync_meta', ?, ?)").use { ps ->
                ps.setString(1, json)
                ps.setLong(2, now)
                ps.executeUpdate()
            }
        }
    }

    override fun exportTextFile(suggestedFileName: String, content: String): Result<String> {
        return runCatching {
            val exportDir = File(dbFile.parentFile, "exports").apply { mkdirs() }
            val outFile = File(exportDir, suggestedFileName)
            outFile.writeText(content, Charsets.UTF_8)
            outFile.absolutePath
        }
    }

    override fun saveLocalBackup(fileName: String, content: String, maxKeep: Int): Result<LocalBackupFileInfo> {
        return runCatching {
            val dir = File(backupDirectoryPath).apply { mkdirs() }
            val outFile = File(dir, fileName)
            outFile.writeText(content, Charsets.UTF_8)
            // Prune oldest auto backups if exceeding maxKeep (keep manual backups untouched unless > 2*maxKeep)
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
        val appData = System.getenv("APPDATA")
        val baseDir = if (!appData.isNullOrBlank()) {
            File(appData, "MochiLedger")
        } else {
            File(System.getProperty("user.home"), ".mochiledger")
        }
        return DesktopSqliteDriver(File(baseDir, "mochiledger.db"))
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
        // Support WebDAV methods like MKCOL / PROPFIND on Java HttpURLConnection
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
            platformName = "Windows / Desktop JVM",
            supportsLsposed = false,
            lsposedActive = false,
            notificationListenerEnabled = false,
        )
    }

    actual fun openNotificationListenerSettings(): Boolean = false

    actual fun openAccessibilitySettings(): Boolean = false

    actual fun openOverlayPermissionSettings(): Boolean = false

    actual fun openBatteryOptimizationSettings(): Boolean = false

    actual fun openAppNotificationSettings(): Boolean = false

    actual fun openUrl(url: String): Boolean {
        return runCatching {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(URI(url))
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    actual fun defaultDeviceId(): String {
        val host = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("desktop")
        return "win-$host-${UUID.randomUUID().toString().take(6)}"
    }

    actual fun pickBackupFileContent(onResult: (Result<Pair<String, String>>) -> Unit) {
        Thread {
            val res = runCatching {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "选择墨记备份文件 (.mochi.json)", java.awt.FileDialog.LOAD)
                dialog.file = "*.json"
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                dialog.dispose()
                if (dir == null || file == null) {
                    throw IllegalStateException("已取消选择文件")
                }
                val selected = File(dir, file)
                selected.name to selected.readText(Charsets.UTF_8)
            }
            onResult(res)
        }.start()
    }

    actual fun pickStatementCsvContent(onResult: (Result<Pair<String, String>>) -> Unit) {
        Thread {
            val res = runCatching {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "选择微信或支付宝官方账单 (.csv)", java.awt.FileDialog.LOAD)
                dialog.file = "*.csv"
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                dialog.dispose()
                if (dir == null || file == null) {
                    throw IllegalStateException("已取消选择账单文件")
                }
                val selected = File(dir, file)
                val bytes = selected.readBytes()
                val text = decodeStatementBytes(bytes)
                selected.name to text
            }
            onResult(res)
        }.start()
    }

    private fun decodeStatementBytes(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        // Alipay official CSV exports are frequently GBK/GB18030 encoded; detect replacement char or missing headers
        val looksLikeUtf8 = !utf8.contains('\uFFFD') &&
            (utf8.contains("交易时间") || utf8.contains("交易对方") || utf8.contains("微信") || utf8.contains("支付宝"))
        if (looksLikeUtf8) return utf8.removePrefix("\uFEFF")
        return runCatching {
            String(bytes, java.nio.charset.Charset.forName("GB18030")).removePrefix("\uFEFF")
        }.getOrElse { utf8.removePrefix("\uFEFF") }
    }

    actual fun saveFileWithPicker(suggestedFileName: String, content: String, onResult: (Result<String>) -> Unit) {
        Thread {
            val res = runCatching {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "导出墨记全量备份文件", java.awt.FileDialog.SAVE)
                dialog.file = suggestedFileName
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                dialog.dispose()
                if (dir == null || file == null) {
                    throw IllegalStateException("已取消导出")
                }
                val outFile = File(dir, file)
                outFile.writeText(content, Charsets.UTF_8)
                outFile.absolutePath
            }
            onResult(res)
        }.start()
    }
}
