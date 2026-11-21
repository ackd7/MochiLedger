package com.mochiledger.shared.data

import com.mochiledger.shared.model.AccountSummary
import com.mochiledger.shared.model.AccountType
import com.mochiledger.shared.model.AppAppearanceConfig
import com.mochiledger.shared.model.AutoTrackRule
import com.mochiledger.shared.model.AutoTrackSettings
import com.mochiledger.shared.model.CategoryStat
import com.mochiledger.shared.model.DefaultLedgerSeed
import com.mochiledger.shared.model.LedgerAccount
import com.mochiledger.shared.model.LedgerBudget
import com.mochiledger.shared.model.LedgerCategory
import com.mochiledger.shared.model.LedgerSnapshot
import com.mochiledger.shared.model.LedgerTransaction
import com.mochiledger.shared.model.MonthlySummary
import com.mochiledger.shared.model.ParsedPayment
import com.mochiledger.shared.model.PaymentChannel
import com.mochiledger.shared.model.RecordSource
import com.mochiledger.shared.model.SelfHostedSyncConfig
import com.mochiledger.shared.model.SyncMetadata
import com.mochiledger.shared.model.TransactionType
import com.mochiledger.shared.model.WebDavConfig
import com.mochiledger.shared.parser.PaymentMessageParser
import com.mochiledger.shared.platform.LedgerDatabaseDriver
import com.mochiledger.shared.platform.PlatformBridge
import com.mochiledger.shared.sync.CsvExporter
import com.mochiledger.shared.sync.LedgerJson
import com.mochiledger.shared.sync.LedgerMerger
import com.mochiledger.shared.sync.SelfHostedSyncClient
import com.mochiledger.shared.sync.SyncResult
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
        val snapshot = if (existing == null || (existing.accounts.isEmpty() && existing.categories.isEmpty())) {
            val seeded = LedgerSnapshot(
                schemaVersion = 1,
                exportedAt = now,
                sourceDeviceId = meta.deviceId,
                accounts = DefaultLedgerSeed.defaultAccounts(now),
                categories = DefaultLedgerSeed.defaultCategories(now),
                transactions = seedSampleTransactions(now),
                budgets = DefaultLedgerSeed.defaultBudgets(now),
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
        )
    }

    private fun seedSampleTransactions(now: Long): List<LedgerTransaction> {
        val dayMs = 86_400_000L
        return listOf(
            LedgerTransaction(
                id = "tx_seed_1",
                type = TransactionType.EXPENSE,
                amountCents = 3200,
                categoryId = "cat_food",
                accountId = "acc_alipay",
                timestamp = now - 2 * 3_600_000L,
                merchant = "星巴克咖啡",
                note = "支付宝 · 拿铁",
                source = RecordSource.ALIPAY_LSPOSED,
                rawAutoMessage = "支付宝交易提醒：向星巴克咖啡付款32.00元",
                updatedAt = now,
            ),
            LedgerTransaction(
                id = "tx_seed_2",
                type = TransactionType.EXPENSE,
                amountCents = 2880,
                categoryId = "cat_food",
                accountId = "acc_wechat",
                timestamp = now - 5 * 3_600_000L,
                merchant = "美团外卖",
                note = "微信支付 · 午餐",
                source = RecordSource.WECHAT_LSPOSED,
                rawAutoMessage = "微信支付：向美团外卖付款28.80元",
                updatedAt = now,
            ),
            LedgerTransaction(
                id = "tx_seed_3",
                type = TransactionType.EXPENSE,
                amountCents = 1850,
                categoryId = "cat_transport",
                accountId = "acc_wechat",
                timestamp = now - dayMs,
                merchant = "滴滴出行",
                note = "打车通勤",
                source = RecordSource.WECHAT_LSPOSED,
                updatedAt = now,
            ),
            LedgerTransaction(
                id = "tx_seed_4",
                type = TransactionType.INCOME,
                amountCents = 1250000,
                categoryId = "cat_salary",
                accountId = "acc_bank_cmb",
                timestamp = now - 3 * dayMs,
                merchant = "月度薪资",
                note = "工资入账",
                source = RecordSource.MANUAL,
                updatedAt = now,
            ),
        )
    }

    fun currentSnapshot(): LedgerSnapshot {
        val s = _state.value
        return LedgerSnapshot(
            schemaVersion = 1,
            exportedAt = PlatformBridge.currentTimeMillis(),
            sourceDeviceId = s.metadata.deviceId,
            accounts = s.accounts,
            categories = s.categories,
            transactions = s.transactions,
            budgets = s.budgets,
            rules = s.rules,
        )
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
            WebDavSyncClient.uploadBackupSnapshot(_state.value.metadata.webDav, currentSnapshot(), tag)
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

    fun exportJsonBackup(): Result<String> {
        val json = LedgerJson.codec.encodeToString(currentSnapshot())
        return driver.exportTextFile("MochiLedger-backup-${PlatformBridge.currentTimeMillis()}.mochi.json", json)
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

    fun importJsonSnapshot(jsonText: String): SyncResult {
        return runCatching {
            val remote = LedgerJson.codec.decodeFromString<LedgerSnapshot>(jsonText)
            val now = PlatformBridge.currentTimeMillis()
            val merged = LedgerMerger.merge(currentSnapshot(), remote, now, _state.value.metadata.deviceId)
            applyMergedSnapshot(merged)
            SyncResult(true, "已合并导入 ${remote.transactions.count { !it.isDeleted }} 条账单", merged, now)
        }.getOrElse { e ->
            SyncResult(false, "导入失败：JSON 格式无效（${e.message}）")
        }
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
