package com.mochiledger.shared.parser

import com.mochiledger.shared.model.AutoTrackRule
import com.mochiledger.shared.model.LedgerAccount
import com.mochiledger.shared.model.LedgerTransaction
import com.mochiledger.shared.model.PaymentChannel
import com.mochiledger.shared.model.RecordSource
import com.mochiledger.shared.model.TransactionType
import com.mochiledger.shared.model.parseAmountToCents
import com.mochiledger.shared.platform.PlatformBridge
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

data class StatementEntry(
    val channel: PaymentChannel,
    val timestamp: Long,
    val type: TransactionType,
    val amountCents: Long,
    val merchant: String,
    val product: String,
    val paymentMethod: String,
    val rawCategory: String,
)

data class StatementImportResult(
    val success: Boolean,
    val detectedSource: String,
    val parsedRows: Int,
    val enrichedCount: Int,
    val importedCount: Int,
    val skippedCount: Int,
    val updatedTransactions: List<LedgerTransaction> = emptyList(),
    val message: String,
)

/**
 * Parses official WeChat Pay & Alipay CSV bill statements (UTF-8 / GB18030 decoded),
 * enriches existing auto-tracked transactions with official merchant & product details
 * (without creating duplicates), and optionally imports missing transactions.
 */
object BillStatementImporter {

    private val GENERIC_MERCHANTS = setOf(
        "", "微信支付", "支付宝", "微信支付收款", "支付宝收款", "花呗消费", "微信支付红包", "支付宝红包", "扫二维码付款",
    )

    fun parseCsv(csvText: String): Pair<String, List<StatementEntry>> {
        val lines = csvText.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val headerIdx = lines.indexOfFirst { line ->
            line.contains("交易时间") && line.contains("交易对方") && line.contains("金额")
        }
        if (headerIdx < 0) return "未知账单" to emptyList()

        val preamble = lines.take(headerIdx).joinToString("\n")
        val headers = splitCsvLine(lines[headerIdx]).map { it.trim().removePrefix("\uFEFF") }
        val isWeChat = preamble.contains("微信") || headers.any { it.contains("金额(元)") || it == "商品" }
        val channel = if (isWeChat) PaymentChannel.WECHAT else PaymentChannel.ALIPAY
        val sourceLabel = if (isWeChat) "微信官方账单" else "支付宝官方账单"

        val idxTime = headers.indexOfFirst { it.contains("交易时间") }
        val idxMerchant = headers.indexOfFirst { it.contains("交易对方") }
        val idxProduct = headers.indexOfFirst { it == "商品" || it.contains("商品说明") || it.contains("商品名称") }
        val idxDir = headers.indexOfFirst { it.contains("收/支") || it == "收支" }
        val idxAmount = headers.indexOfFirst { it.contains("金额") }
        val idxPayMethod = headers.indexOfFirst { it.contains("支付方式") || it.contains("收/付款方式") }
        val idxStatus = headers.indexOfFirst { it.contains("状态") }
        val idxCategory = headers.indexOfFirst { it.contains("交易分类") || it.contains("交易类型") }

        val entries = mutableListOf<StatementEntry>()
        val tz = TimeZone.currentSystemDefault()

        for (i in (headerIdx + 1) until lines.size) {
            val rawLine = lines[i].trim()
            if (rawLine.isEmpty()) continue
            val cols = splitCsvLine(rawLine)
            if (cols.size <= maxOf(idxTime, idxAmount, idxMerchant)) continue

            val dirText = cols.getOrNull(idxDir)?.trim().orEmpty()
            val statusText = cols.getOrNull(idxStatus)?.trim().orEmpty()
            if (dirText.contains("不计收支") || dirText == "/" || dirText.isBlank()) continue
            if (statusText.contains("交易关闭") || statusText.contains("已全额退款") || statusText.contains("退款成功")) {
                // Keep actual refund income rows if dirText == 收入, skip closed/cancelled expenses
                if (!dirText.contains("收入")) continue
            }

            val txType = when {
                dirText.contains("支出") -> TransactionType.EXPENSE
                dirText.contains("收入") -> TransactionType.INCOME
                else -> continue
            }

            val timeText = cols.getOrNull(idxTime)?.trim().orEmpty()
            val timestamp = parseDateTimeToMillis(timeText, tz) ?: continue
            val amountCents = parseAmountToCents(cols.getOrNull(idxAmount)?.trim().orEmpty()) ?: continue
            if (amountCents <= 0L) continue

            val merchant = cols.getOrNull(idxMerchant)?.trim().orEmpty().trim('"')
            val rawProduct = cols.getOrNull(idxProduct)?.trim().orEmpty().trim('"')
            val product = if (rawProduct == "/" || rawProduct == "-") "" else rawProduct
            val payMethod = cols.getOrNull(idxPayMethod)?.trim().orEmpty().let { if (it == "/") "" else it }
            val rawCat = cols.getOrNull(idxCategory)?.trim().orEmpty()

            entries.add(
                StatementEntry(
                    channel = channel,
                    timestamp = timestamp,
                    type = txType,
                    amountCents = amountCents,
                    merchant = merchant,
                    product = product,
                    paymentMethod = payMethod,
                    rawCategory = rawCat,
                ),
            )
        }
        return sourceLabel to entries
    }

    fun applyImportAndEnrich(
        csvText: String,
        existingTransactions: List<LedgerTransaction>,
        accounts: List<LedgerAccount>,
        rules: List<AutoTrackRule>,
        importMissing: Boolean = true,
        nowMillis: Long = PlatformBridge.currentTimeMillis(),
    ): StatementImportResult {
        val (sourceLabel, entries) = parseCsv(csvText)
        if (entries.isEmpty()) {
            return StatementImportResult(
                success = false,
                detectedSource = sourceLabel,
                parsedRows = 0,
                enrichedCount = 0,
                importedCount = 0,
                skippedCount = 0,
                message = "未识别到有效的微信或支付宝官方账单记录，请确认选择的是官方导出的 CSV 文件",
            )
        }

        val mutableTxs = existingTransactions.toMutableList()
        var enrichedCount = 0
        var importedCount = 0
        var skippedCount = 0

        for (entry in entries) {
            // Find matching existing transaction within ±180s with same type & amount
            val matchIdx = mutableTxs.indexOfFirst { tx ->
                !tx.isDeleted &&
                    tx.type == entry.type &&
                    tx.amountCents == entry.amountCents &&
                    kotlin.math.abs(tx.timestamp - entry.timestamp) <= 180_000L
            }

            val combinedText = "${entry.merchant} ${entry.product} ${entry.rawCategory}"
            val matchedRule = rules.firstOrNull { rule ->
                if (!rule.enabled || rule.isDeleted) return@firstOrNull false
                if (rule.channel != null && rule.channel != entry.channel) return@firstOrNull false
                val kws = rule.keyword.split("|", ",", "，").map { it.trim() }.filter { it.isNotEmpty() }
                kws.any { kw -> combinedText.contains(kw, ignoreCase = true) }
            }

            if (matchIdx >= 0) {
                val existing = mutableTxs[matchIdx]
                val betterMerchant = if (existing.merchant.trim() in GENERIC_MERCHANTS && entry.merchant.isNotBlank()) {
                    entry.merchant
                } else {
                    existing.merchant.ifBlank { entry.merchant }
                }

                val detailNote = buildDetailNote(entry)
                val betterNote = when {
                    detailNote.isBlank() -> existing.note
                    existing.note.isBlank() -> detailNote
                    existing.note.contains(entry.product, ignoreCase = true) && entry.product.isNotBlank() -> existing.note
                    existing.note.startsWith(entry.channel.label) -> detailNote
                    else -> "${existing.note} · $detailNote"
                }

                val betterCategory = if ((existing.categoryId == "cat_other_exp" || existing.categoryId == "cat_other_inc") && matchedRule != null) {
                    matchedRule.targetCategoryId
                } else {
                    existing.categoryId
                }

                if (betterMerchant != existing.merchant || betterNote != existing.note || betterCategory != existing.categoryId) {
                    mutableTxs[matchIdx] = existing.copy(
                        merchant = betterMerchant,
                        note = betterNote,
                        categoryId = betterCategory,
                        updatedAt = nowMillis,
                    )
                    enrichedCount++
                } else {
                    skippedCount++
                }
            } else if (importMissing) {
                val resolvedAccId = resolveAccountId(entry, accounts, matchedRule)
                val resolvedCatId = matchedRule?.targetCategoryId ?: fallbackCategoryId(entry)
                val newTx = LedgerTransaction(
                    id = PlatformBridge.randomUuid(),
                    type = entry.type,
                    amountCents = entry.amountCents,
                    categoryId = resolvedCatId,
                    accountId = resolvedAccId,
                    timestamp = entry.timestamp,
                    merchant = entry.merchant.ifBlank { entry.channel.label },
                    note = buildDetailNote(entry),
                    source = RecordSource.IMPORT,
                    updatedAt = nowMillis,
                )
                mutableTxs.add(newTx)
                importedCount++
            } else {
                skippedCount++
            }
        }

        val summaryMsg = buildString {
            append("$sourceLabel 处理完成（共 ${entries.size} 笔）：")
            append("补全明细 $enrichedCount 笔")
            if (importMissing) append("，新增导入 $importedCount 笔")
            if (skippedCount > 0) append("，已存在跳过 $skippedCount 笔")
        }

        return StatementImportResult(
            success = true,
            detectedSource = sourceLabel,
            parsedRows = entries.size,
            enrichedCount = enrichedCount,
            importedCount = importedCount,
            skippedCount = skippedCount,
            updatedTransactions = mutableTxs,
            message = summaryMsg,
        )
    }

    private fun buildDetailNote(entry: StatementEntry): String {
        return listOf(entry.product, entry.rawCategory)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "/" && it != "商户消费" && it != "扫二维码付款" }
            .distinct()
            .joinToString(" · ")
    }

    private fun resolveAccountId(
        entry: StatementEntry,
        accounts: List<LedgerAccount>,
        matchedRule: AutoTrackRule?,
    ): String {
        val active = accounts.filter { !it.isDeleted }
        if (matchedRule?.targetAccountId != null) return matchedRule.targetAccountId
        if (entry.paymentMethod.isNotBlank()) {
            active.firstOrNull { entry.paymentMethod.contains(it.name, ignoreCase = true) || it.name.contains(entry.paymentMethod, ignoreCase = true) }
                ?.let { return it.id }
        }
        val channelHint = if (entry.channel == PaymentChannel.WECHAT) "微信" else "支付宝"
        return active.firstOrNull { it.name.contains(channelHint) }?.id
            ?: active.firstOrNull()?.id
            ?: "acc_cash"
    }

    private fun fallbackCategoryId(entry: StatementEntry): String {
        val text = "${entry.rawCategory} ${entry.product} ${entry.merchant}"
        if (entry.type == TransactionType.INCOME) {
            return when {
                text.contains("退款") -> "cat_refund"
                text.contains("收益") || text.contains("理财") || text.contains("余额宝") -> "cat_invest"
                text.contains("红包") || text.contains("转账") -> "cat_redpacket"
                else -> "cat_other_inc"
            }
        }
        return when {
            text.contains("餐饮") || text.contains("美食") || text.contains("外卖") -> "cat_food"
            text.contains("交通") || text.contains("出行") || text.contains("打车") -> "cat_transport"
            text.contains("日用") || text.contains("购物") || text.contains("服饰") || text.contains("百货") -> "cat_shopping"
            text.contains("水电") || text.contains("充值") || text.contains("通讯") || text.contains("住房") -> "cat_housing"
            text.contains("娱乐") || text.contains("电影") || text.contains("游戏") -> "cat_entertainment"
            text.contains("医疗") || text.contains("药") || text.contains("医院") -> "cat_medical"
            else -> "cat_other_exp"
        }
    }

    private fun parseDateTimeToMillis(raw: String, tz: TimeZone): Long? {
        val cleaned = raw.trim().replace("/", "-")
        val match = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?""").find(cleaned)
            ?: return null
        return runCatching {
            val ldt = LocalDateTime(
                year = match.groupValues[1].toInt(),
                monthNumber = match.groupValues[2].toInt(),
                dayOfMonth = match.groupValues[3].toInt(),
                hour = match.groupValues[4].toInt(),
                minute = match.groupValues[5].toInt(),
                second = match.groupValues[6].toIntOrNull() ?: 0,
            )
            ldt.toInstant(tz).toEpochMilliseconds()
        }.getOrNull()
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(cur.toString())
                    cur.clear()
                }
                else -> cur.append(ch)
            }
            i++
        }
        result.add(cur.toString())
        return result
    }
}
