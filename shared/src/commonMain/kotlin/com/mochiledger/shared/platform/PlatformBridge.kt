package com.mochiledger.shared.platform

import com.mochiledger.shared.model.LedgerSnapshot
import com.mochiledger.shared.model.LocalBackupFileInfo
import com.mochiledger.shared.model.SyncMetadata

data class HttpResponseData(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
) {
    val isSuccessful: Boolean get() = statusCode in 200..299
}

data class AutoTrackRuntimeStatus(
    val platformName: String,
    val supportsLsposed: Boolean,
    val lsposedActive: Boolean,
    val notificationListenerEnabled: Boolean,
    val hookVersion: String = "1.0.0",
)

interface LedgerDatabaseDriver {
    val databasePath: String
    val backupDirectoryPath: String
    fun loadSnapshot(): LedgerSnapshot?
    fun saveSnapshot(snapshot: LedgerSnapshot)
    fun loadSyncMetadata(): SyncMetadata
    fun saveSyncMetadata(metadata: SyncMetadata)
    fun exportTextFile(suggestedFileName: String, content: String): Result<String>
    fun saveLocalBackup(fileName: String, content: String, maxKeep: Int = 30): Result<LocalBackupFileInfo>
    fun listLocalBackups(): List<LocalBackupFileInfo>
    fun readTextFile(absolutePath: String): Result<String>
    fun deleteLocalBackup(absolutePath: String): Boolean
}

expect object PlatformBridge {
    fun currentTimeMillis(): Long
    fun randomUuid(): String
    fun createDatabaseDriver(): LedgerDatabaseDriver
    fun httpRequest(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 15_000,
    ): HttpResponseData
    fun getAutoTrackRuntimeStatus(): AutoTrackRuntimeStatus
    fun openNotificationListenerSettings(): Boolean
    fun openUrl(url: String): Boolean
    fun defaultDeviceId(): String
    fun pickBackupFileContent(onResult: (Result<Pair<String, String>>) -> Unit)
    fun saveFileWithPicker(suggestedFileName: String, content: String, onResult: (Result<String>) -> Unit)
}
