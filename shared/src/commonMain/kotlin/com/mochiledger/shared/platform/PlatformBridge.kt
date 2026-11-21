package com.mochiledger.shared.platform

import com.mochiledger.shared.model.LedgerSnapshot
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
    fun loadSnapshot(): LedgerSnapshot?
    fun saveSnapshot(snapshot: LedgerSnapshot)
    fun loadSyncMetadata(): SyncMetadata
    fun saveSyncMetadata(metadata: SyncMetadata)
    fun exportTextFile(suggestedFileName: String, content: String): Result<String>
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
    fun defaultDeviceId(): String
}
