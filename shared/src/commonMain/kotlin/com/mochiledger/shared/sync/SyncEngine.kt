package com.mochiledger.shared.sync

import com.mochiledger.shared.model.AutoTrackRule
import com.mochiledger.shared.model.LedgerAccount
import com.mochiledger.shared.model.LedgerBudget
import com.mochiledger.shared.model.LedgerCategory
import com.mochiledger.shared.model.LedgerSnapshot
import com.mochiledger.shared.model.LedgerTransaction
import com.mochiledger.shared.model.SelfHostedSyncConfig
import com.mochiledger.shared.model.WebDavConfig
import com.mochiledger.shared.platform.PlatformBridge
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class SyncResult(
    val success: Boolean,
    val message: String,
    val mergedSnapshot: LedgerSnapshot? = null,
    val syncedAt: Long = 0L,
)

@Serializable
data class ServerSyncRequest(
    val ledgerId: String,
    val deviceId: String,
    val snapshot: LedgerSnapshot,
)

@Serializable
data class ServerSyncResponse(
    val ok: Boolean,
    val serverTime: Long,
    val mergedSnapshot: LedgerSnapshot,
    val message: String = "Synced",
)

object LedgerJson {
    val codec = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

/**
 * Deterministic Last-Write-Wins (LWW) merger for bidirectional multi-device sync
 * across Windows, Android, WebDAV, and the Self-Hosted Sync Server.
 */
object LedgerMerger {
    fun merge(local: LedgerSnapshot, remote: LedgerSnapshot, now: Long, deviceId: String): LedgerSnapshot {
        return LedgerSnapshot(
            schemaVersion = maxOf(local.schemaVersion, remote.schemaVersion),
            exportedAt = now,
            sourceDeviceId = deviceId,
            accounts = mergeEntities(local.accounts, remote.accounts, { it.id }, { it.updatedAt }),
            categories = mergeEntities(local.categories, remote.categories, { it.id }, { it.updatedAt }),
            transactions = mergeEntities(local.transactions, remote.transactions, { it.id }, { it.updatedAt }),
            budgets = mergeEntities(local.budgets, remote.budgets, { it.id }, { it.updatedAt }),
            rules = mergeEntities(local.rules, remote.rules, { it.id }, { it.updatedAt }),
        )
    }

    private fun <T> mergeEntities(
        local: List<T>,
        remote: List<T>,
        idOf: (T) -> String,
        updatedAtOf: (T) -> Long,
    ): List<T> {
        val map = LinkedHashMap<String, T>()
        for (item in local) {
            map[idOf(item)] = item
        }
        for (item in remote) {
            val id = idOf(item)
            val existing = map[id]
            if (existing == null || updatedAtOf(item) >= updatedAtOf(existing)) {
                map[id] = item
            }
        }
        return map.values.toList()
    }
}

/**
 * WebDAV client supporting Jianguoyun (坚果云), Nextcloud, Alist, OpenList, etc.
 */
object WebDavSyncClient {
    private const val SYNC_FILENAME = "sync-state.mochi.json"

    @OptIn(ExperimentalEncodingApi::class)
    private fun authHeaders(config: WebDavConfig): Map<String, String> {
        val raw = "${config.username}:${config.password}".encodeToByteArray()
        val encoded = Base64.encode(raw)
        return mapOf(
            "Authorization" to "Basic $encoded",
            "User-Agent" to "MochiLedger/1.0",
        )
    }

    private fun directoryUrl(config: WebDavConfig): String {
        val base = config.serverUrl.trim().trimEnd('/')
        val dir = config.remoteDirectory.trim().trim('/')
        return if (dir.isEmpty()) "$base/" else "$base/$dir/"
    }

    fun testConnection(config: WebDavConfig): SyncResult {
        if (config.serverUrl.isBlank() || config.username.isBlank()) {
            return SyncResult(false, "请填写完整的 WebDAV 地址和账号")
        }
        return runCatching {
            val dirUrl = directoryUrl(config)
            // Ensure remote directory exists (MKCOL returns 201 Created or 405 Method Not Allowed if it already exists)
            val mkcol = PlatformBridge.httpRequest("MKCOL", dirUrl, headers = authHeaders(config))
            if (mkcol.statusCode in listOf(200, 201, 204, 301, 405)) {
                SyncResult(true, "WebDAV 连接成功（目录已就绪）")
            } else if (mkcol.statusCode == 401 || mkcol.statusCode == 403) {
                SyncResult(false, "认证失败（HTTP ${mkcol.statusCode}），请检查用户名或应用专用密码")
            } else {
                // Some servers prefer PROPFIND/OPTIONS
                val probe = PlatformBridge.httpRequest("OPTIONS", config.serverUrl.trim(), headers = authHeaders(config))
                if (probe.isSuccessful) {
                    SyncResult(true, "WebDAV 服务连接正常")
                } else {
                    SyncResult(false, "连接失败：HTTP ${mkcol.statusCode}")
                }
            }
        }.getOrElse { e ->
            SyncResult(false, "WebDAV 连接异常：${e.message ?: e::class.simpleName}")
        }
    }

    fun syncBidirectional(config: WebDavConfig, local: LedgerSnapshot, deviceId: String): SyncResult {
        if (config.serverUrl.isBlank() || config.username.isBlank()) {
            return SyncResult(false, "WebDAV 未配置完整")
        }
        return runCatching {
            val headers = authHeaders(config)
            val dirUrl = directoryUrl(config)
            PlatformBridge.httpRequest("MKCOL", dirUrl, headers = headers)

            val fileUrl = "$dirUrl$SYNC_FILENAME"
            val getResp = PlatformBridge.httpRequest("GET", fileUrl, headers = headers)
            val now = PlatformBridge.currentTimeMillis()

            val merged = when {
                getResp.isSuccessful && getResp.body.isNotBlank() -> {
                    val remote = LedgerJson.codec.decodeFromString<LedgerSnapshot>(getResp.body)
                    LedgerMerger.merge(local, remote, now, deviceId)
                }
                getResp.statusCode == 404 -> {
                    local.copy(exportedAt = now, sourceDeviceId = deviceId)
                }
                getResp.statusCode == 401 || getResp.statusCode == 403 -> {
                    return SyncResult(false, "WebDAV 鉴权失败（HTTP ${getResp.statusCode}）")
                }
                else -> {
                    return SyncResult(false, "拉取远端数据失败（HTTP ${getResp.statusCode}）")
                }
            }

            val payload = LedgerJson.codec.encodeToString(merged)
            val putHeaders = headers + ("Content-Type" to "application/json; charset=utf-8")
            val putResp = PlatformBridge.httpRequest("PUT", fileUrl, headers = putHeaders, body = payload)
            if (putResp.isSuccessful || putResp.statusCode == 201 || putResp.statusCode == 204) {
                SyncResult(
                    success = true,
                    message = "WebDAV 双向同步成功（共 ${merged.transactions.count { !it.isDeleted }} 条账单）",
                    mergedSnapshot = merged,
                    syncedAt = now,
                )
            } else {
                SyncResult(false, "上传同步数据失败（HTTP ${putResp.statusCode}）")
            }
        }.getOrElse { e ->
            SyncResult(false, "WebDAV 同步异常：${e.message ?: e::class.simpleName}")
        }
    }

    fun uploadBackupSnapshot(config: WebDavConfig, snapshot: LedgerSnapshot, tag: String): SyncResult {
        if (config.serverUrl.isBlank() || config.username.isBlank()) {
            return SyncResult(false, "WebDAV 未配置完整")
        }
        return runCatching {
            val headers = authHeaders(config)
            val dirUrl = directoryUrl(config)
            PlatformBridge.httpRequest("MKCOL", dirUrl, headers = headers)
            val backupUrl = "${dirUrl}backup-$tag.mochi.json"
            val payload = LedgerJson.codec.encodeToString(snapshot)
            val resp = PlatformBridge.httpRequest(
                method = "PUT",
                url = backupUrl,
                headers = headers + ("Content-Type" to "application/json; charset=utf-8"),
                body = payload,
            )
            if (resp.isSuccessful || resp.statusCode == 201 || resp.statusCode == 204) {
                SyncResult(true, "已备份至 WebDAV：backup-$tag.mochi.json", syncedAt = PlatformBridge.currentTimeMillis())
            } else {
                SyncResult(false, "备份失败（HTTP ${resp.statusCode}）")
            }
        }.getOrElse { e ->
            SyncResult(false, "WebDAV 备份异常：${e.message ?: e::class.simpleName}")
        }
    }
}

/**
 * Client for the built-in MochiLedger Ktor Self-Hosted Sync Server.
 */
object SelfHostedSyncClient {
    private fun headers(config: SelfHostedSyncConfig): Map<String, String> {
        val map = mutableMapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "User-Agent" to "MochiLedger/1.0",
        )
        if (config.authToken.isNotBlank()) {
            map["Authorization"] = "Bearer ${config.authToken.trim()}"
        }
        return map
    }

    fun testConnection(config: SelfHostedSyncConfig): SyncResult {
        if (config.serverUrl.isBlank()) {
            return SyncResult(false, "请填写自建服务器地址")
        }
        return runCatching {
            val url = "${config.serverUrl.trim().trimEnd('/')}/api/v1/health"
            val resp = PlatformBridge.httpRequest("GET", url, headers = headers(config))
            if (resp.isSuccessful) {
                SyncResult(true, "自建同步服务器连接正常")
            } else if (resp.statusCode == 401) {
                SyncResult(false, "Token 验证失败（HTTP 401）")
            } else {
                SyncResult(false, "服务器返回异常（HTTP ${resp.statusCode}）")
            }
        }.getOrElse { e ->
            SyncResult(false, "无法连接到自建服务器：${e.message ?: e::class.simpleName}")
        }
    }

    fun syncBidirectional(config: SelfHostedSyncConfig, local: LedgerSnapshot, deviceId: String): SyncResult {
        if (config.serverUrl.isBlank()) {
            return SyncResult(false, "请填写自建服务器地址")
        }
        return runCatching {
            val url = "${config.serverUrl.trim().trimEnd('/')}/api/v1/sync"
            val req = ServerSyncRequest(
                ledgerId = config.ledgerId.ifBlank { "default" },
                deviceId = deviceId,
                snapshot = local,
            )
            val body = LedgerJson.codec.encodeToString(req)
            val resp = PlatformBridge.httpRequest("POST", url, headers = headers(config), body = body)
            if (resp.isSuccessful) {
                val parsed = LedgerJson.codec.decodeFromString<ServerSyncResponse>(resp.body)
                SyncResult(
                    success = parsed.ok,
                    message = "自建服务器同步成功（共 ${parsed.mergedSnapshot.transactions.count { !it.isDeleted }} 条账单）",
                    mergedSnapshot = parsed.mergedSnapshot,
                    syncedAt = parsed.serverTime,
                )
            } else if (resp.statusCode == 401) {
                SyncResult(false, "Token 认证失败（HTTP 401）")
            } else {
                SyncResult(false, "同步失败（HTTP ${resp.statusCode}）：${resp.body.take(120)}")
            }
        }.getOrElse { e ->
            SyncResult(false, "自建服务器同步异常：${e.message ?: e::class.simpleName}")
        }
    }
}

object CsvExporter {
    fun exportTransactionsCsv(
        transactions: List<LedgerTransaction>,
        accounts: Map<String, LedgerAccount>,
        categories: Map<String, LedgerCategory>,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("id,timestamp,type,amount_cny,category,account,target_account,merchant,note,source")
        transactions.filter { !it.isDeleted }.sortedByDescending { it.timestamp }.forEach { tx ->
            val amt = "${tx.amountCents / 100}.${(tx.amountCents % 100).toString().padStart(2, '0')}"
            val cat = categories[tx.categoryId]?.name ?: tx.categoryId
            val acc = accounts[tx.accountId]?.name ?: tx.accountId
            val targetAcc = tx.targetAccountId?.let { accounts[it]?.name ?: it } ?: ""
            sb.appendLine(
                listOf(
                    tx.id,
                    tx.timestamp.toString(),
                    tx.type.name,
                    amt,
                    escapeCsv(cat),
                    escapeCsv(acc),
                    escapeCsv(targetAcc),
                    escapeCsv(tx.merchant),
                    escapeCsv(tx.note),
                    tx.source.name,
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
