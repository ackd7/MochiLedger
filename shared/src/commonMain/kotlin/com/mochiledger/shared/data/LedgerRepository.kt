package com.mochiledger.shared.data

import com.mochiledger.shared.model.AccountSummary
import com.mochiledger.shared.model.AccountType
import com.mochiledger.shared.model.AppAppearanceConfig
import com.mochiledger.shared.model.AutoBackupConfig
import com.mochiledger.shared.model.AutoTrackRule
import com.mochiledger.shared.model.AutoTrackSettings
import com.mochiledger.shared.model.BackupServerConfigBundle
import com.mochiledger.shared.model.CategoryStat
import com.mochiledger.shared.model.DefaultLedgerSeed
import com.mochiledger.shared.model.LedgerAccount
import com.mochiledger.shared.model.LedgerBudget
import com.mochiledger.shared.model.LedgerCategory
import com.mochiledger.shared.model.LedgerSnapshot
import com.mochiledger.shared.model.LedgerTransaction
import com.mochiledger.shared.model.LocalBackupFileInfo
import com.mochiledger.shared.model.MonthlySummary
import com.mochiledger.shared.model.ParsedPayment
import com.mochiledger.shared.model.PaymentChannel
import com.mochiledger.shared.model.RecordSource
import com.mochiledger.shared.model.SelfHostedSyncConfig
import com.mochiledger.shared.model.SyncMetadata
import com.mochiledger.shared.model.TransactionType
import com.mochiledger.shared.model.WebDavConfig
import com.mochiledger.shared.model.formatFileSize
import com.mochiledger.shared.parser.PaymentMessageParser
import com.mochiledger.shared.platform.LedgerDatabaseDriver
import com.mochiledger.shared.platform.PlatformBridge
import com.mochiledger.shared.sync.CsvExporter
import com.mochiledger.shared.sync.GitHubUpdateChecker
import com.mochiledger.shared.sync.LedgerJson
import com.mochiledger.shared.sync.LedgerMerger
import com.mochiledger.shared.sync.SelfHostedSyncClient
import com.mochiledger.shared.sync.SyncResult
import com.mochiledger.shared.sync.UpdateCheckResult
import com.mochiledger.shared.sync.WebDavSyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString

data class LedgerUiState(
    val accounts: List<LedgerAccount> = emptyList(),
    val categories: List<LedgerCategory> = emptyList(),
    val transactions: List<LedgerTransaction> = emptyList(),
    val budgets: List<LedgerBudget> = emptyList(),
    val rules: List<AutoTrackRule> = emptyList(),
    val metadata: SyncMetadata = SyncMetadata(),
    val databasePath: String = "",
    val backupDirectoryPath: String = "",
    val localBackups: List<LocalBackupFileInfo> = emptyList(),
    val isSyncing: Boolean = false,
) {
    val activeAccounts: List<LedgerAccount> get() = accounts.filter { !it.isDeleted }
    val activeCategories: List<LedgerCategory> get() = categories.filter { !it.isDeleted }.sortedBy { it.sortOrder }
    val activeTransactions: List<LedgerTransaction> get() = transactions.filter { !it.isDeleted }.sortedByDescending { it.timestamp }
    val activeBudgets: List<LedgerBudget> get() = budgets.filter { !it.isDeleted }
    val activeRules: List<AutoTrackRule> get() = rules.filter { !it.isDeleted }

    fun accountSummaries(): List<AccountSummary> {
        val deltas = mutableMapOf<String, Long>()
        for (tx in activeTransactions) {
            when (tx.type) {
                TransactionType.EXPENSE -> {
                    deltas[tx.accountId] = (deltas[tx.accountId] ?: 0L) - tx.amountCents
                }
                TransactionType.INCOME -> {
                    deltas[tx.accountId] = (deltas[tx.accountId] ?: 0L) + tx.amountCents
                }
                TransactionType.TRANSFER -> {
                    deltas[tx.accountId] = (deltas[tx.accountId] ?: 0L) - tx.amountCents
                    tx.targetAccountId?.let { target ->
                        deltas[target] = (deltas[target] ?: 0L) + tx.amountCents
                    }
                }
            }
        }
        return activeAccounts.map { acc ->
            AccountSummary(
                account = acc,
                currentBalanceCents = acc.initialBalanceCents + (deltas[acc.id] ?: 0L),
            )
        }
    }

    fun monthlySummary(year: Int, month: Int): MonthlySummary {
        val tz = TimeZone.currentSystemDefault()
        val monthTxs = activeTransactions.filter { tx ->
            val dt = Instant.fromEpochMilliseconds(tx.timestamp).toLocalDateTime(tz)
            dt.year == year && dt.monthNumber == month
        }
        val catMap = categories.associateBy { it.id }

        var income = 0L
        var expense = 0L
        val expByCat = mutableMapOf<String, Pair<Long, Int>>()
        val incByCat = mutableMapOf<String, Pair<Long, Int>>()
        val dailyExp = mutableMapOf<Int, Long>()

        for (tx in monthTxs) {
            val dt = Instant.fromEpochMilliseconds(tx.timestamp).toLocalDateTime(tz)
            when (tx.type) {
                TransactionType.EXPENSE -> {
                    expense += tx.amountCents
                    val prev = expByCat[tx.categoryId] ?: (0L to 0)
                    expByCat[tx.categoryId] = (prev.first + tx.amountCents) to (prev.second + 1)
                    dailyExp[dt.dayOfMonth] = (dailyExp[dt.dayOfMonth] ?: 0L) + tx.amountCents
                }
                TransactionType.INCOME -> {
                    income += tx.amountCents
                    val prev = incByCat[tx.categoryId] ?: (0L to 0)
                    incByCat[tx.categoryId] = (prev.first + tx.amountCents) to (prev.second + 1)
                }
                TransactionType.TRANSFER -> Unit
            }
        }

        fun buildStats(map: Map<String, Pair<Long, Int>>, total: Long): List<CategoryStat> {
            return map.entries.mapNotNull { (catId, pair) ->
                val cat = catMap[catId] ?: return@mapNotNull null
                val pct = if (total > 0L) pair.first.toFloat() / total.toFloat() else 0f
                CategoryStat(cat, pair.first, pct, pair.second)
            }.sortedByDescending { it.amountCents }
        }

        return MonthlySummary(
            year = year,
            month = month,
            incomeCents = income,
            expenseCents = expense,
            netCents = income - expense,
            expenseByCategory = buildStats(expByCat, expense),
            incomeByCategory = buildStats(incByCat, income),
            dailyExpenseCents = dailyExp,
        )
    }
}

class LedgerRepository(
    private val driver: LedgerDatabaseDriver = PlatformBridge.createDatabaseDriver(),
) {
    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<LedgerUiState> = _state.asStateFlow()

    private fun loadInitialState(): LedgerUiState {
        val now = PlatformBridge.currentTimeMillis()
        var meta = driver.loadSyncMetadata()
        if (meta.deviceId.isBlank()) {
            meta = meta.copy(deviceId = PlatformBridge.defaultDeviceId())
            driver.saveSyncMetadata(meta)
        }

        val existing = driver.loadSnapshot()
        val snapshot = if (existing == null || existing.categories.isEmpty()) {
            val seeded = LedgerSnapshot(
                schemaVersion = 1,
                exportedAt = now,
                sourceDeviceId = meta.deviceId,
                accounts = emptyList(),
                categories = DefaultLedgerSeed.defaultCategories(now),
                transactions = emptyList(),
                budgets = emptyList(),
                rules = DefaultLedgerSeed.defaultRules(now),
            )
            driver.saveSnapshot(seeded)
            seeded
        } else {
            existing
        }

        return LedgerUiState(
            accounts = snapshot.accounts,
            categories = snapshot.categories,
            transactions = snapshot.transactions,
            budgets = snapshot.budgets,
            rules = snapshot.rules,
            metadata = meta,
            databasePath = driver.databasePath,
            backupDirectoryPath = driver.backupDirectoryPath,
            localBackups = driver.listLocalBackups(),
        )
    }

    fun currentSnapshot(): LedgerSnapshot {
        val s = _state.value
        return LedgerSnapshot(
            schemaVersion = 2,
            exportedAt = PlatformBridge.currentTimeMillis(),
            sourceDeviceId = s.metadata.deviceId,
            accounts = s.accounts,
            categories = s.categories,
            transactions = s.transactions,
            budgets = s.budgets,
            rules = s.rules,
        )
    }

    fun createFullBackupSnapshot(
        includeServerConfig: Boolean = _state.value.metadata.autoBackup.includeServerConfig,
    ): LedgerSnapshot {
        val s = _state.value
        val bundle = if (includeServerConfig) {
            BackupServerConfigBundle(
                webDav = s.metadata.webDav,
                selfHosted = s.metadata.selfHosted,
                autoTrack = s.metadata.autoTrack,
                autoBackup = s.metadata.autoBackup,
                appearance = s.metadata.appearance,
            )
        } else {
            null
        }
        return currentSnapshot().copy(serverConfig = bundle)
    }

    fun createFullBackupJson(
        includeServerConfig: Boolean = _state.value.metadata.autoBackup.includeServerConfig,
    ): String {
        return LedgerJson.codec.encodeToString(createFullBackupSnapshot(includeServerConfig))
    }

    fun refreshLocalBackups() {
        _state.value = _state.value.copy(localBackups = driver.listLocalBackups())
    }

    private fun persist(updated: LedgerUiState) {
        _state.value = updated
        val snap = LedgerSnapshot(
            schemaVersion = 1,
            exportedAt = PlatformBridge.currentTimeMillis(),
            sourceDeviceId = updated.metadata.deviceId,
            accounts = updated.accounts,
            categories = updated.categories,
            transactions = updated.transactions,
            budgets = updated.budgets,
            rules = updated.rules,
        )
        driver.saveSnapshot(snap)
    }

    fun upsertTransaction(tx: LedgerTransaction) {
        val now = PlatformBridge.currentTimeMillis()
        val normalized = tx.copy(
            id = tx.id.ifBlank { PlatformBridge.randomUuid() },
            updatedAt = now,
            isDeleted = false,
        )
        val list = _state.value.transactions.filterNot { it.id == normalized.id } + normalized
        persist(_state.value.copy(transactions = list))
    }

    fun deleteTransaction(id: String) {
        val now = PlatformBridge.currentTimeMillis()
        val list = _state.value.transactions.map {
            if (it.id == id) it.copy(isDeleted = true, updatedAt = now) else it
        }
        persist(_state.value.copy(transactions = list))
    }

    fun upsertAccount(account: LedgerAccount) {
        val now = PlatformBridge.currentTimeMillis()
        val normalized = account.copy(
            id = account.id.ifBlank { "acc_${PlatformBridge.randomUuid().take(8)}" },
            updatedAt = now,
            isDeleted = false,
        )
        val list = _state.value.accounts.filterNot { it.id == normalized.id } + normalized
        persist(_state.value.copy(accounts = list))
    }

    fun deleteAccount(id: String) {
        val now = PlatformBridge.currentTimeMillis()
        val list = _state.value.accounts.map {
            if (it.id == id) it.copy(isDeleted = true, updatedAt = now) else it
        }
        persist(_state.value.copy(accounts = list))
    }

    fun upsertBudget(budget: LedgerBudget) {
        val now = PlatformBridge.currentTimeMillis()
        val normalized = budget.copy(
            id = budget.id.ifBlank { "budget_${PlatformBridge.randomUuid().take(8)}" },
            updatedAt = now,
            isDeleted = false,
        )
        val list = _state.value.budgets.filterNot { it.id == normalized.id } + normalized
        persist(_state.value.copy(budgets = list))
    }

    fun upsertRule(rule: AutoTrackRule) {
        val now = PlatformBridge.currentTimeMillis()
        val normalized = rule.copy(
            id = rule.id.ifBlank { "rule_${PlatformBridge.randomUuid().take(8)}" },
            updatedAt = now,
            isDeleted = false,
        )
        val list = _state.value.rules.filterNot { it.id == normalized.id } + normalized
        persist(_state.value.copy(rules = list))
    }

    fun deleteRule(id: String) {
        val now = PlatformBridge.currentTimeMillis()
        val list = _state.value.rules.map {
            if (it.id == id) it.copy(isDeleted = true, updatedAt = now) else it
        }
        persist(_state.value.copy(rules = list))
    }

    fun updateWebDavConfig(config: WebDavConfig) {
        val meta = _state.value.metadata.copy(webDav = config)
        driver.saveSyncMetadata(meta)
        _state.value = _state.value.copy(metadata = meta)
    }

    fun updateSelfHostedConfig(config: SelfHostedSyncConfig) {
        val meta = _state.value.metadata.copy(selfHosted = config)
        driver.saveSyncMetadata(meta)
        _state.value = _state.value.copy(metadata = meta)
    }

    fun updateAutoTrackSettings(settings: AutoTrackSettings) {
        val meta = _state.value.metadata.copy(autoTrack = settings)
        driver.saveSyncMetadata(meta)
        _state.value = _state.value.copy(metadata = meta)
    }

    fun updateAppearance(appearance: AppAppearanceConfig) {
        val meta = _state.value.metadata.copy(appearance = appearance)
        driver.saveSyncMetadata(meta)
        _state.value = _state.value.copy(metadata = meta)
    }

    fun updateAutoBackupConfig(config: AutoBackupConfig) {
        val meta = _state.value.metadata.copy(autoBackup = config)
        driver.saveSyncMetadata(meta)
        _state.value = _state.value.copy(metadata = meta)
    }

    fun parsePaymentPreview(
        packageName: String?,
        title: String,
        content: String,
        source: RecordSource? = null,
    ): ParsedPayment? {
        val s = _state.value
        return PaymentMessageParser.parse(
            packageName = packageName,
            title = title,
            content = content,
            rules = s.activeRules,
            accounts = s.activeAccounts,
            source = source,
            nowMillis = PlatformBridge.currentTimeMillis(),
        )
    }

    /**
     * Ingests an auto-tracked payment from LSPosed or NotificationListener with deduplication.
     * Returns the newly recorded transaction, or null if filtered/deduplicated.
     */
    fun recordParsedPayment(parsed: ParsedPayment): LedgerTransaction? {
        val s = _state.value
        val window = s.metadata.autoTrack.dedupeWindowSeconds
        val duplicate = s.activeTransactions.take(20).any { recent ->
            val recentChannel = when (recent.source) {
                RecordSource.WECHAT_LSPOSED -> PaymentChannel.WECHAT
                RecordSource.ALIPAY_LSPOSED -> PaymentChannel.ALIPAY
                else -> null
            }
            PaymentMessageParser.isDuplicate(
                candidate = parsed,
                recentAmountCents = recent.amountCents,
                recentTimestamp = recent.timestamp,
                recentChannel = recentChannel,
                dedupeWindowSeconds = window,
            )
        }
        if (duplicate) return null

        val fallbackAccountId = s.activeAccounts.firstOrNull()?.id ?: "acc_cash"
        val fallbackCategoryId = when (parsed.type) {
            TransactionType.EXPENSE -> "cat_other_exp"
            TransactionType.INCOME -> "cat_other_inc"
            TransactionType.TRANSFER -> "cat_transfer"
        }

        val tx = LedgerTransaction(
            id = PlatformBridge.randomUuid(),
            type = parsed.type,
            amountCents = parsed.amountCents,
            categoryId = parsed.suggestedCategoryId ?: fallbackCategoryId,
            accountId = parsed.suggestedAccountId ?: fallbackAccountId,
            timestamp = if (parsed.detectedAt > 0) parsed.detectedAt else PlatformBridge.currentTimeMillis(),
            merchant = parsed.merchant,
            note = parsed.note,
            source = parsed.source,
            rawAutoMessage = parsed.rawText,
            updatedAt = PlatformBridge.currentTimeMillis(),
        )
        upsertTransaction(tx)
        return tx
    }

    suspend fun syncWithWebDav(): SyncResult = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(isSyncing = true)
        try {
            val s = _state.value
            val result = WebDavSyncClient.syncBidirectional(
                config = s.metadata.webDav,
                local = currentSnapshot(),
                deviceId = s.metadata.deviceId,
            )
            if (result.success && result.mergedSnapshot != null) {
                applyMergedSnapshot(result.mergedSnapshot)
                updateWebDavConfig(s.metadata.webDav.copy(lastSyncAt = result.syncedAt))
            }
            result
        } finally {
            _state.value = _state.value.copy(isSyncing = false)
        }
    }

    suspend fun backupToWebDav(): SyncResult = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(isSyncing = true)
        try {
            val now = PlatformBridge.currentTimeMillis()
            val dt = Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.currentSystemDefault())
            val tag = "${dt.year}${dt.monthNumber.toString().padStart(2, '0')}${dt.dayOfMonth.toString().padStart(2, '0')}-${dt.hour.toString().padStart(2, '0')}${dt.minute.toString().padStart(2, '0')}"
            WebDavSyncClient.uploadBackupSnapshot(_state.value.metadata.webDav, createFullBackupSnapshot(), tag)
        } finally {
            _state.value = _state.value.copy(isSyncing = false)
        }
    }

    suspend fun testWebDav(): SyncResult = withContext(Dispatchers.IO) {
        WebDavSyncClient.testConnection(_state.value.metadata.webDav)
    }

    suspend fun syncWithSelfHosted(): SyncResult = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(isSyncing = true)
        try {
            val s = _state.value
            val result = SelfHostedSyncClient.syncBidirectional(
                config = s.metadata.selfHosted,
                local = currentSnapshot(),
                deviceId = s.metadata.deviceId,
            )
            if (result.success && result.mergedSnapshot != null) {
                applyMergedSnapshot(result.mergedSnapshot)
                updateSelfHostedConfig(s.metadata.selfHosted.copy(lastSyncAt = result.syncedAt))
            }
            result
        } finally {
            _state.value = _state.value.copy(isSyncing = false)
        }
    }

    suspend fun testSelfHosted(): SyncResult = withContext(Dispatchers.IO) {
        SelfHostedSyncClient.testConnection(_state.value.metadata.selfHosted)
    }

    /**
     * Multi-source sync: runs WebDAV and/or Self-Hosted Server together when enabled,
     * merging and deduplicating transactions & accounts across all sources.
     */
    suspend fun syncAllEnabledSources(): SyncResult = withContext(Dispatchers.IO) {
        val meta = _state.value.metadata
        val useWebDav = meta.webDav.enabled && meta.webDav.serverUrl.isNotBlank() && meta.webDav.username.isNotBlank()
        val useSelfHosted = meta.selfHosted.enabled && meta.selfHosted.serverUrl.isNotBlank()
        if (!useWebDav && !useSelfHosted) {
            return@withContext SyncResult(false, "请先进入 WebDAV 或自建服务器页面并开启至少一种同步方式")
        }
        val messages = mutableListOf<String>()
        var anySuccess = false
        if (useWebDav) {
            val res = syncWithWebDav()
            messages.add("WebDAV：${res.message}")
            if (res.success) anySuccess = true
        }
        if (useSelfHosted) {
            val res = syncWithSelfHosted()
            messages.add("自建服：${res.message}")
            if (res.success) anySuccess = true
        }
        // Second pass to propagate merged/deduped state back if both were enabled
        if (useWebDav && useSelfHosted && anySuccess) {
            syncWithWebDav()
        }
        SyncResult(
            success = anySuccess,
            message = messages.joinToString(" | "),
            syncedAt = PlatformBridge.currentTimeMillis(),
        )
    }

    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        GitHubUpdateChecker.checkLatest()
    }

    fun exportJsonBackup(): Result<String> {
        val json = createFullBackupJson()
        val tag = formatFileTimestamp(PlatformBridge.currentTimeMillis())
        return driver.exportTextFile("MochiLedger-backup-$tag.mochi.json", json)
    }

    fun exportCsvBackup(): Result<String> {
        val s = _state.value
        val csv = CsvExporter.exportTransactionsCsv(
            transactions = s.transactions,
            accounts = s.accounts.associateBy { it.id },
            categories = s.categories.associateBy { it.id },
        )
        return driver.exportTextFile("MochiLedger-transactions-${PlatformBridge.currentTimeMillis()}.csv", csv)
    }

    suspend fun performManualBackup(alsoSyncRemote: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val now = PlatformBridge.currentTimeMillis()
        val cfg = _state.value.metadata.autoBackup
        val tag = formatFileTimestamp(now)
        val fileName = "MochiLedger-manual-$tag.mochi.json"
        val json = createFullBackupJson(cfg.includeServerConfig)
        val saved = driver.saveLocalBackup(fileName, json, cfg.maxLocalBackups)
        refreshLocalBackups()
        saved.fold(
            onSuccess = { info ->
                var msg = "手动备份成功：${info.fileName}（${formatFileSize(info.sizeBytes)}）"
                if (cfg.includeServerConfig) msg += "，已含服务器配置"
                if (alsoSyncRemote && hasAnyEnabledRemoteSync()) {
                    val remoteRes = syncAllEnabledSources()
                    msg += if (remoteRes.success) " · 远端同步成功" else " · 远端同步提示：${remoteRes.message}"
                }
                SyncResult(true, msg, syncedAt = now)
            },
            onFailure = { e ->
                SyncResult(false, "手动备份失败：${e.message}")
            },
        )
    }

    suspend fun checkAndRunDailyFirstOpenBackup(): SyncResult? = withContext(Dispatchers.IO) {
        val cfg = _state.value.metadata.autoBackup
        if (!cfg.dailyFirstOpenEnabled) return@withContext null
        val now = PlatformBridge.currentTimeMillis()
        val today = formatDateKey(now)
        if (cfg.lastDailyBackupDate == today) return@withContext null

        val tag = formatFileTimestamp(now)
        val fileName = "MochiLedger-daily-$tag.mochi.json"
        val json = createFullBackupJson(cfg.includeServerConfig)
        val saved = driver.saveLocalBackup(fileName, json, cfg.maxLocalBackups)
        updateAutoBackupConfig(cfg.copy(lastDailyBackupDate = today, lastAutoBackupAt = now))
        refreshLocalBackups()

        if (cfg.alsoSyncRemote && hasAnyEnabledRemoteSync()) {
            runCatching { syncAllEnabledSources() }
        }
        saved.fold(
            onSuccess = { info ->
                val suffix = if (cfg.includeServerConfig) "（含服务器配置）" else ""
                SyncResult(true, "已完成今日首次打开自动备份：${formatFileSize(info.sizeBytes)}$suffix", syncedAt = now)
            },
            onFailure = { e ->
                SyncResult(false, "每日首开自动备份失败：${e.message}")
            },
        )
    }

    suspend fun checkAndRunScheduledBackup(): SyncResult? = withContext(Dispatchers.IO) {
        val cfg = _state.value.metadata.autoBackup
        if (!cfg.scheduledEnabled) return@withContext null
        val now = PlatformBridge.currentTimeMillis()
        val intervalMs = cfg.intervalHours.coerceAtLeast(1) * 3_600_000L
        if (cfg.lastAutoBackupAt > 0L && (now - cfg.lastAutoBackupAt) < intervalMs) {
            return@withContext null
        }
        val tag = formatFileTimestamp(now)
        val fileName = "MochiLedger-scheduled-$tag.mochi.json"
        val json = createFullBackupJson(cfg.includeServerConfig)
        val saved = driver.saveLocalBackup(fileName, json, cfg.maxLocalBackups)
        updateAutoBackupConfig(cfg.copy(lastAutoBackupAt = now))
        refreshLocalBackups()

        if (cfg.alsoSyncRemote && hasAnyEnabledRemoteSync()) {
            runCatching { syncAllEnabledSources() }
        }
        saved.fold(
            onSuccess = { info ->
                SyncResult(true, "已完成定时自动备份：${formatFileSize(info.sizeBytes)}", syncedAt = now)
            },
            onFailure = { e ->
                SyncResult(false, "定时自动备份失败：${e.message}")
            },
        )
    }

    fun importBackupFromFile(absolutePath: String, restoreServerConfig: Boolean = true): SyncResult {
        return driver.readTextFile(absolutePath).fold(
            onSuccess = { json -> importJsonSnapshot(json, restoreServerConfig) },
            onFailure = { e -> SyncResult(false, "读取备份文件失败：${e.message}") },
        )
    }

    fun deleteLocalBackup(absolutePath: String): Boolean {
        val ok = driver.deleteLocalBackup(absolutePath)
        refreshLocalBackups()
        return ok
    }

    fun importJsonSnapshot(jsonText: String, restoreServerConfig: Boolean = true): SyncResult {
        return runCatching {
            val remote = LedgerJson.codec.decodeFromString<LedgerSnapshot>(jsonText)
            val now = PlatformBridge.currentTimeMillis()
            val merged = LedgerMerger.merge(currentSnapshot(), remote, now, _state.value.metadata.deviceId)
            applyMergedSnapshot(merged)

            var restoredConfig = false
            if (restoreServerConfig && remote.serverConfig != null) {
                val bundle = remote.serverConfig
                val curMeta = _state.value.metadata
                val updatedMeta = curMeta.copy(
                    webDav = bundle.webDav,
                    selfHosted = bundle.selfHosted,
                    autoTrack = bundle.autoTrack,
                    autoBackup = bundle.autoBackup.copy(
                        lastAutoBackupAt = maxOf(curMeta.autoBackup.lastAutoBackupAt, bundle.autoBackup.lastAutoBackupAt),
                        lastDailyBackupDate = curMeta.autoBackup.lastDailyBackupDate.ifBlank { bundle.autoBackup.lastDailyBackupDate },
                    ),
                    appearance = bundle.appearance ?: curMeta.appearance,
                )
                driver.saveSyncMetadata(updatedMeta)
                _state.value = _state.value.copy(metadata = updatedMeta)
                restoredConfig = true
            }

            val txCount = remote.transactions.count { !it.isDeleted }
            val accCount = remote.accounts.count { !it.isDeleted }
            val configNote = if (restoredConfig) "，并已还原同步服务器与备份配置" else ""
            SyncResult(true, "已合并导入 $txCount 条账单、$accCount 个账户$configNote", merged, now)
        }.getOrElse { e ->
            SyncResult(false, "导入失败：JSON 格式无效（${e.message}）")
        }
    }

    private fun hasAnyEnabledRemoteSync(): Boolean {
        val meta = _state.value.metadata
        val useWebDav = meta.webDav.enabled && meta.webDav.serverUrl.isNotBlank() && meta.webDav.username.isNotBlank()
        val useSelfHosted = meta.selfHosted.enabled && meta.selfHosted.serverUrl.isNotBlank()
        return useWebDav || useSelfHosted
    }

    private fun formatDateKey(ms: Long): String {
        val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')}"
    }

    private fun formatFileTimestamp(ms: Long): String {
        val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.year}${dt.monthNumber.toString().padStart(2, '0')}${dt.dayOfMonth.toString().padStart(2, '0')}-" +
            "${dt.hour.toString().padStart(2, '0')}${dt.minute.toString().padStart(2, '0')}${dt.second.toString().padStart(2, '0')}"
    }

    private fun applyMergedSnapshot(merged: LedgerSnapshot) {
        val updated = _state.value.copy(
            accounts = merged.accounts,
            categories = merged.categories,
            transactions = merged.transactions,
            budgets = merged.budgets,
            rules = merged.rules,
        )
        persist(updated)
    }
}
