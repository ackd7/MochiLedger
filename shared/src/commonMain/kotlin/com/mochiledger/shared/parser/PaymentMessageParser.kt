package com.mochiledger.shared.parser

import com.mochiledger.shared.model.AutoTrackRule
import com.mochiledger.shared.model.LedgerAccount
import com.mochiledger.shared.model.ParsedPayment
import com.mochiledger.shared.model.PaymentChannel
import com.mochiledger.shared.model.RecordSource
import com.mochiledger.shared.model.TransactionType
import kotlin.math.roundToLong

/**
 * Unified Alipay & WeChat Pay message parser shared across Android (LSPosed / NotificationListener)
 * and Windows Desktop (interactive rule tester & simulator).
 */
object PaymentMessageParser {

    private val amountPatterns = listOf(
        // ¥18.50 / ￥18.50 / RMB 18.50
        Regex("""(?:¥|￥|RMB\s*|人民币\s*)([0-9]+(?:\.[0-9]{1,2})?)"""),
        // 付款18.50元 / 收款0.01元 / 消费199.00元 / 支出35元 / 收入12.3元
        Regex("""(?:付款|支付|消费|支出|扣款|收款|到账|收入|转账|红包|退款|收益|入账)[：:\s+]*([0-9]+(?:\.[0-9]{1,2})?)\s*元?"""),
        // 18.50元
        Regex("""([0-9]+(?:\.[0-9]{1,2})?)\s*元"""),
    )

    private val merchantPatterns = listOf(
        // 向星巴克付款32.00元 / 给张三转账
        Regex("""(?:向|给)\s*([^\s，,。：:；;（(]{1,24}?)\s*(?:付款|支付|转账|消费)"""),
        // 商户：瑞幸咖啡 / 收款方：美团外卖 / 对方：...
        Regex("""(?:商户|商家|收款方|付款方|对方|交易对象)[：:\s]+([^\n，,。；;]{1,28})"""),
        // 在美团外卖消费 / 在7-Eleven支付
        Regex("""在\s*([^\s，,。：:；;]{1,24}?)\s*(?:消费|付款|支付)"""),
        // 收到来自张三的转账 / 张三向你转账
        Regex("""([^\s，,。：:；;]{1,20}?)\s*(?:向你付款|向你转账|给你发了|发来)"""),
    )

    private val incomeKeywords = listOf(
        "收款", "到账", "收入", "入账", "收益", "退款", "已退回", "向你付款", "向你转账", "领取了你的", "收到红包", "到账通知",
    )

    private val expenseKeywords = listOf(
        "付款", "支付", "消费", "支出", "扣款", "已支付", "成功付款", "支付成功", "付款成功", "交易成功", "花呗", "自动扣费", "免密支付", "发出红包",
    )

    private val ignoreKeywords = listOf(
        "验证码", "登录", "安全提醒", "绑定", "广告", "优惠券", "签到", "积分", "蚂蚁森林", "蚂蚁庄园", "好友申请",
    )

    fun parse(
        packageName: String?,
        title: String,
        content: String,
        rules: List<AutoTrackRule> = emptyList(),
        accounts: List<LedgerAccount> = emptyList(),
        source: RecordSource? = null,
        nowMillis: Long = 0L,
    ): ParsedPayment? {
        val fullText = listOf(title.trim(), content.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        if (fullText.isBlank()) return null

        // Filter out non-payment notifications
        if (ignoreKeywords.any { fullText.contains(it, ignoreCase = true) }) {
            return null
        }

        val channel = detectChannel(packageName, fullText) ?: return null
        val amountCents = extractAmountCents(fullText) ?: return null
        if (amountCents <= 0L) return null

        val txType = detectTransactionType(fullText) ?: return null
        val merchant = extractMerchant(title, content, channel, txType)
        val suggestedAccountName = detectAccountName(fullText, channel)
        val matchedRule = matchRule(fullText, merchant, channel, rules)

        val resolvedAccountId = matchedRule?.targetAccountId
            ?: accounts.firstOrNull { !it.isDeleted && it.name.contains(suggestedAccountName, ignoreCase = true) }?.id
            ?: accounts.firstOrNull {
                !it.isDeleted && when (channel) {
                    PaymentChannel.WECHAT -> it.id == "acc_wechat" || it.name.contains("微信")
                    PaymentChannel.ALIPAY -> it.id == "acc_alipay" || it.name.contains("支付宝")
                }
            }?.id

        val resolvedCategoryId = matchedRule?.targetCategoryId ?: defaultCategoryFor(txType, fullText)

        val effectiveSource = source ?: when (channel) {
            PaymentChannel.WECHAT -> RecordSource.WECHAT_LSPOSED
            PaymentChannel.ALIPAY -> RecordSource.ALIPAY_LSPOSED
        }

        val cleanNote = buildString {
            append(channel.label)
            if (merchant.isNotBlank() && merchant != channel.label) {
                append(" · ")
                append(merchant)
            }
        }

        return ParsedPayment(
            channel = channel,
            type = txType,
            amountCents = amountCents,
            merchant = merchant,
            suggestedAccountName = suggestedAccountName,
            suggestedCategoryId = resolvedCategoryId,
            suggestedAccountId = resolvedAccountId,
            note = cleanNote,
            rawText = fullText,
            source = effectiveSource,
            detectedAt = nowMillis,
        )
    }

    private fun detectChannel(packageName: String?, text: String): PaymentChannel? {
        return when {
            packageName == PaymentChannel.WECHAT.packageName -> PaymentChannel.WECHAT
            packageName == PaymentChannel.ALIPAY.packageName -> PaymentChannel.ALIPAY
            text.contains("微信") || text.contains("零钱") -> PaymentChannel.WECHAT
            text.contains("支付宝") || text.contains("花呗") || text.contains("余额宝") -> PaymentChannel.ALIPAY
            else -> null
        }
    }

    private fun extractAmountCents(text: String): Long? {
        for (pattern in amountPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val number = match.groupValues[1].toDoubleOrNull() ?: continue
                if (number > 0.0) {
                    return (number * 100.0).roundToLong()
                }
            }
        }
        return null
    }

    private fun detectTransactionType(text: String): TransactionType? {
        // Strip brand names so "微信支付收款50元" doesn't falsely match "支付" as an expense
        val normalized = text
            .replace("微信支付", "")
            .replace("支付宝", "")
            .trim()
        // Special disambiguation: "向你付款" / "收款" / "到账" / "退款" / "收益" are always income
        if (normalized.contains("向你付款") || normalized.contains("向你转账") ||
            normalized.contains("收款") || normalized.contains("到账") ||
            normalized.contains("退款") || normalized.contains("收益") ||
            normalized.contains("入账")
        ) {
            return TransactionType.INCOME
        }
        val hasIncome = incomeKeywords.any { normalized.contains(it) }
        val hasExpense = expenseKeywords.any { normalized.contains(it) }
        return when {
            hasIncome && !hasExpense -> TransactionType.INCOME
            hasExpense -> TransactionType.EXPENSE
            hasIncome -> TransactionType.INCOME
            else -> null
        }
    }

    private fun extractMerchant(title: String, content: String, channel: PaymentChannel, type: TransactionType): String {
        val combined = "$title $content"
        for (pattern in merchantPatterns) {
            val match = pattern.find(combined)
            val candidate = match?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank() && candidate !in listOf("你", "您", "微信支付", "支付宝")) {
                return candidate
            }
        }
        val cleanTitle = title.trim()
        if (cleanTitle.isNotEmpty() &&
            !cleanTitle.contains("微信支付") &&
            !cleanTitle.contains("支付宝") &&
            !cleanTitle.contains("交易提醒") &&
            !cleanTitle.contains("收款通知") &&
            cleanTitle.length <= 24
        ) {
            return cleanTitle
        }
        return when {
            type == TransactionType.INCOME && combined.contains("余额宝") -> "余额宝收益"
            type == TransactionType.INCOME && combined.contains("红包") -> "${channel.label}红包"
            type == TransactionType.INCOME -> "${channel.label}收款"
            combined.contains("花呗") -> "花呗消费"
            else -> channel.label
        }
    }

    private fun detectAccountName(text: String, channel: PaymentChannel): String {
        return when {
            text.contains("花呗") -> "花呗"
            text.contains("招商银行") || text.contains("招行") -> "招商银行"
            channel == PaymentChannel.WECHAT -> "微信零钱"
            else -> "支付宝"
        }
    }

    private fun matchRule(
        fullText: String,
        merchant: String,
        channel: PaymentChannel,
        rules: List<AutoTrackRule>,
    ): AutoTrackRule? {
        return rules.firstOrNull { rule ->
            if (!rule.enabled || rule.isDeleted) return@firstOrNull false
            if (rule.channel != null && rule.channel != channel) return@firstOrNull false
            val keywords = rule.keyword.split("|", ",", "，").map { it.trim() }.filter { it.isNotEmpty() }
            keywords.any { kw ->
                fullText.contains(kw, ignoreCase = true) || merchant.contains(kw, ignoreCase = true)
            }
        }
    }

    private fun defaultCategoryFor(type: TransactionType, text: String): String {
        return when (type) {
            TransactionType.INCOME -> when {
                text.contains("退款") -> "cat_refund"
                text.contains("收益") || text.contains("余额宝") || text.contains("理财") -> "cat_invest"
                text.contains("红包") || text.contains("转账") -> "cat_redpacket"
                else -> "cat_other_inc"
            }
            TransactionType.EXPENSE -> "cat_other_exp"
            TransactionType.TRANSFER -> "cat_transfer"
        }
    }

    /**
     * Checks whether a newly detected payment duplicates an existing recent auto-tracked payment
     * (e.g., when both LSPosed hook and NotificationListener fire for the same payment).
     */
    fun isDuplicate(
        candidate: ParsedPayment,
        recentAmountCents: Long,
        recentTimestamp: Long,
        recentChannel: PaymentChannel?,
        dedupeWindowSeconds: Int,
    ): Boolean {
        if (candidate.amountCents != recentAmountCents) return false
        if (recentChannel != null && recentChannel != candidate.channel) return false
        val diffMillis = kotlin.math.abs(candidate.detectedAt - recentTimestamp)
        return diffMillis <= dedupeWindowSeconds * 1000L
    }
}
