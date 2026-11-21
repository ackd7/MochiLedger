package com.mochiledger.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
}

@Serializable
enum class AccountType(val label: String, val isLiability: Boolean = false) {
    CASH("现金"),
    DIGITAL_WALLET("电子钱包"),
    BANK_CARD("储蓄卡"),
    CREDIT_CARD("信用账户", isLiability = true),
    INVESTMENT("理财投资"),
}

@Serializable
enum class RecordSource(val label: String) {
    MANUAL("手动记账"),
    WECHAT_LSPOSED("LSPosed · 微信"),
    ALIPAY_LSPOSED("LSPosed · 支付宝"),
    NOTIFICATION_LISTENER("系统通知监听"),
    IMPORT("账单导入"),
}

@Serializable
enum class PaymentChannel(val label: String, val packageName: String) {
    WECHAT("微信支付", "com.tencent.mm"),
    ALIPAY("支付宝", "com.eg.android.AlipayGphone"),
}

@Serializable
data class LedgerAccount(
    val id: String,
    val name: String,
    val type: AccountType,
    val initialBalanceCents: Long = 0L,
    val currency: String = "CNY",
    val iconKey: String = "wallet",
    val colorHex: Long = 0xFF4F6EF7,
    val creditLimitCents: Long = 0L,
    val note: String = "",
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
)

@Serializable
data class LedgerCategory(
    val id: String,
    val name: String,
    val type: TransactionType,
    val iconKey: String = "tag",
    val colorHex: Long = 0xFF4F6EF7,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
)

@Serializable
data class LedgerTransaction(
    val id: String,
    val type: TransactionType,
    val amountCents: Long,
    val categoryId: String,
    val accountId: String,
    val targetAccountId: String? = null,
    val timestamp: Long,
    val merchant: String = "",
    val note: String = "",
    val tags: List<String> = emptyList(),
    val source: RecordSource = RecordSource.MANUAL,
    val rawAutoMessage: String? = null,
    val updatedAt: Long = timestamp,
    val isDeleted: Boolean = false,
)

@Serializable
data class LedgerBudget(
    val id: String,
    val name: String,
    val categoryId: String? = null, // null means total monthly budget
    val limitCents: Long,
    val monthKey: String = "ALL", // "ALL" for recurring monthly or "YYYY-MM"
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
)

@Serializable
data class AutoTrackRule(
    val id: String,
    val name: String,
    val keyword: String,
    val channel: PaymentChannel? = null, // null = both WeChat & Alipay
    val targetCategoryId: String,
    val targetAccountId: String? = null,
    val enabled: Boolean = true,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
)

@Serializable
data class WebDavConfig(
    val enabled: Boolean = false,
    val serverUrl: String = "https://dav.jianguoyun.com/dav/",
    val username: String = "",
    val password: String = "",
    val remoteDirectory: String = "MochiLedger",
    val lastSyncAt: Long = 0L,
)

@Serializable
data class SelfHostedSyncConfig(
    val enabled: Boolean = false,
    val serverUrl: String = "http://127.0.0.1:8787",
    val authToken: String = "",
    val ledgerId: String = "default",
    val lastSyncAt: Long = 0L,
)

@Serializable
data class AutoTrackSettings(
    val enabled: Boolean = true,
    val hookWeChat: Boolean = true,
    val hookAlipay: Boolean = true,
    val fallbackNotificationListener: Boolean = true,
    val autoConfirm: Boolean = true,
    val dedupeWindowSeconds: Int = 15,
)

@Serializable
data class AppAppearanceConfig(
    val colorMode: Int = 0, // 0 = System, 1 = Light, 2 = Dark
    val accentPreset: Int = 0, // 0 = Mochi Indigo, 1 = Matcha Mint, 2 = Sakura Rose, 3 = Amber Gold
    val enableSquircle: Boolean = true,
    val enableGlassEffects: Boolean = true,
)

@Serializable
data class SyncMetadata(
    val deviceId: String = "",
    val webDav: WebDavConfig = WebDavConfig(),
    val selfHosted: SelfHostedSyncConfig = SelfHostedSyncConfig(),
    val autoTrack: AutoTrackSettings = AutoTrackSettings(),
    val appearance: AppAppearanceConfig = AppAppearanceConfig(),
)

/**
 * Interoperable snapshot exchanged identically between Windows, Android, WebDAV, and Self-Hosted Sync Server.
 */
@Serializable
data class LedgerSnapshot(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val sourceDeviceId: String = "",
    val accounts: List<LedgerAccount> = emptyList(),
    val categories: List<LedgerCategory> = emptyList(),
    val transactions: List<LedgerTransaction> = emptyList(),
    val budgets: List<LedgerBudget> = emptyList(),
    val rules: List<AutoTrackRule> = emptyList(),
)

@Serializable
data class ParsedPayment(
    val channel: PaymentChannel,
    val type: TransactionType,
    val amountCents: Long,
    val merchant: String,
    val suggestedAccountName: String,
    val suggestedCategoryId: String?,
    val suggestedAccountId: String?,
    val note: String,
    val rawText: String,
    val source: RecordSource,
    val detectedAt: Long,
)

data class AccountSummary(
    val account: LedgerAccount,
    val currentBalanceCents: Long,
)

data class CategoryStat(
    val category: LedgerCategory,
    val amountCents: Long,
    val percentage: Float,
    val transactionCount: Int,
)

data class MonthlySummary(
    val year: Int,
    val month: Int,
    val incomeCents: Long,
    val expenseCents: Long,
    val netCents: Long,
    val expenseByCategory: List<CategoryStat>,
    val incomeByCategory: List<CategoryStat>,
    val dailyExpenseCents: Map<Int, Long>,
)

object DefaultLedgerSeed {
    fun defaultAccounts(now: Long): List<LedgerAccount> = listOf(
        LedgerAccount(
            id = "acc_wechat",
            name = "微信零钱",
            type = AccountType.DIGITAL_WALLET,
            initialBalanceCents = 128000,
            iconKey = "wechat",
            colorHex = 0xFF07C160,
            updatedAt = now,
        ),
        LedgerAccount(
            id = "acc_alipay",
            name = "支付宝",
            type = AccountType.DIGITAL_WALLET,
            initialBalanceCents = 256000,
            iconKey = "alipay",
            colorHex = 0xFF1677FF,
            updatedAt = now,
        ),
        LedgerAccount(
            id = "acc_huabei",
            name = "花呗",
            type = AccountType.CREDIT_CARD,
            initialBalanceCents = 0,
            creditLimitCents = 1500000,
            iconKey = "credit",
            colorHex = 0xFF00A6FB,
            updatedAt = now,
        ),
        LedgerAccount(
            id = "acc_bank_cmb",
            name = "招商银行储蓄卡",
            type = AccountType.BANK_CARD,
            initialBalanceCents = 1850000,
            iconKey = "bank",
            colorHex = 0xFFE53935,
            updatedAt = now,
        ),
        LedgerAccount(
            id = "acc_cash",
            name = "现金钱包",
            type = AccountType.CASH,
            initialBalanceCents = 50000,
            iconKey = "cash",
            colorHex = 0xFFF59E0B,
            updatedAt = now,
        ),
    )

    fun defaultCategories(now: Long): List<LedgerCategory> = listOf(
        // Expense categories
        LedgerCategory("cat_food", "餐饮美食", TransactionType.EXPENSE, "food", 0xFFFF6B6B, 1, now),
        LedgerCategory("cat_transport", "交通出行", TransactionType.EXPENSE, "transport", 0xFF4ECDC4, 2, now),
        LedgerCategory("cat_shopping", "购物消费", TransactionType.EXPENSE, "shopping", 0xFFFF9F1C, 3, now),
        LedgerCategory("cat_housing", "居家水电", TransactionType.EXPENSE, "home", 0xFF5C7CFA, 4, now),
        LedgerCategory("cat_entertainment", "休闲娱乐", TransactionType.EXPENSE, "game", 0xFF9775FA, 5, now),
        LedgerCategory("cat_digital", "数码订阅", TransactionType.EXPENSE, "digital", 0xFF339AF0, 6, now),
        LedgerCategory("cat_medical", "医疗健康", TransactionType.EXPENSE, "medical", 0xFF20C997, 7, now),
        LedgerCategory("cat_social", "人情往来", TransactionType.EXPENSE, "gift", 0xFFF06595, 8, now),
        LedgerCategory("cat_other_exp", "其他支出", TransactionType.EXPENSE, "other", 0xFF868E96, 9, now),
        // Income categories
        LedgerCategory("cat_salary", "工资薪酬", TransactionType.INCOME, "salary", 0xFF2B8A3E, 101, now),
        LedgerCategory("cat_invest", "理财收益", TransactionType.INCOME, "invest", 0xFF0CA678, 102, now),
        LedgerCategory("cat_redpacket", "红包转账", TransactionType.INCOME, "gift", 0xFFE8590C, 103, now),
        LedgerCategory("cat_refund", "退款报销", TransactionType.INCOME, "refund", 0xFF1C7ED6, 104, now),
        LedgerCategory("cat_other_inc", "其他收入", TransactionType.INCOME, "other", 0xFF495057, 105, now),
        // Transfer
        LedgerCategory("cat_transfer", "账户互转", TransactionType.TRANSFER, "transfer", 0xFF4F6EF7, 200, now),
    )

    fun defaultRules(now: Long): List<AutoTrackRule> = listOf(
        AutoTrackRule("rule_meituan", "外卖餐饮", "美团|饿了么|瑞幸|星巴克|麦当劳|肯德基|喜茶|霸王茶姬|餐饮|食堂", null, "cat_food", null, true, now),
        AutoTrackRule("rule_transport", "打车通勤", "滴滴|高德打车|地铁|公交|铁路12306|中石化|中石油|高速|充电", null, "cat_transport", null, true, now),
        AutoTrackRule("rule_shopping", "电商购物", "淘宝|天猫|京东|拼多多|盒马|山姆|沃尔玛|超市|便利店", null, "cat_shopping", null, true, now),
        AutoTrackRule("rule_digital", "云服务与会员", "Apple|iCloud|Steam|B站|网易云|腾讯视频|爱奇艺|阿里云|腾讯云", null, "cat_digital", null, true, now),
        AutoTrackRule("rule_housing", "水电话费", "国家电网|水务|燃气|中国移动|中国联通|中国电信|宽带|物业", null, "cat_housing", null, true, now),
        AutoTrackRule("rule_yuebao", "余额宝收益", "余额宝|理财通|收益|分红", PaymentChannel.ALIPAY, "cat_invest", "acc_alipay", true, now),
    )

    fun defaultBudgets(now: Long): List<LedgerBudget> = listOf(
        LedgerBudget("budget_total", "月度总预算", null, 500000, "ALL", now),
        LedgerBudget("budget_food", "餐饮月预算", "cat_food", 150000, "ALL", now),
    )
}

fun formatCents(cents: Long, withSymbol: Boolean = true, showSign: Boolean = false): String {
    val negative = cents < 0
    val abs = if (negative) -cents else cents
    val whole = abs / 100
    val frac = abs % 100
    val wholeStr = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    val fracStr = frac.toString().padStart(2, '0')
    val sign = when {
        negative -> "-"
        showSign && cents > 0 -> "+"
        else -> ""
    }
    val prefix = if (withSymbol) "¥" else ""
    return "$sign$prefix$wholeStr.$fracStr"
}

fun parseAmountToCents(raw: String): Long? {
    val cleaned = raw.trim().replace(",", "").replace("¥", "").replace("￥", "")
    if (cleaned.isEmpty()) return null
    val value = cleaned.toDoubleOrNull() ?: return null
    if (value < 0) return null
    return kotlin.math.round(value * 100.0).toLong()
}
