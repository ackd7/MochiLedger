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
data class AutoBackupConfig(
    val dailyFirstOpenEnabled: Boolean = true,
    val scheduledEnabled: Boolean = false,
    val intervalHours: Int = 6, // 1, 3, 6, 12, 24
    val alsoSyncRemote: Boolean = true,
    val includeServerConfig: Boolean = true,
    val maxLocalBackups: Int = 30,
    val lastAutoBackupAt: Long = 0L,
    val lastDailyBackupDate: String = "", // YYYY-MM-DD
)

@Serializable
data class BackupServerConfigBundle(
    val webDav: WebDavConfig = WebDavConfig(),
    val selfHosted: SelfHostedSyncConfig = SelfHostedSyncConfig(),
    val autoTrack: AutoTrackSettings = AutoTrackSettings(),
    val autoBackup: AutoBackupConfig = AutoBackupConfig(),
    val appearance: AppAppearanceConfig? = null,
)

data class LocalBackupFileInfo(
    val fileName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val triggerType: String = "manual", // manual, daily, scheduled
)

@Serializable
data class SyncMetadata(
    val deviceId: String = "",
    val webDav: WebDavConfig = WebDavConfig(),
    val selfHosted: SelfHostedSyncConfig = SelfHostedSyncConfig(),
    val autoTrack: AutoTrackSettings = AutoTrackSettings(),
    val appearance: AppAppearanceConfig = AppAppearanceConfig(),
    val autoBackup: AutoBackupConfig = AutoBackupConfig(),
)

/**
 * Interoperable snapshot exchanged identically between Windows, Android, WebDAV, and Self-Hosted Sync Server.
 * When exported/saved as a full backup, [serverConfig] also preserves WebDAV, Self-Hosted Sync Server,
 * AutoTrack, and AutoBackup configurations for one-click cross-device restoration.
 */
@Serializable
data class LedgerSnapshot(
    val schemaVersion: Int = 2,
    val exportedAt: Long,
    val sourceDeviceId: String = "",
    val accounts: List<LedgerAccount> = emptyList(),
    val categories: List<LedgerCategory> = emptyList(),
    val transactions: List<LedgerTransaction> = emptyList(),
    val budgets: List<LedgerBudget> = emptyList(),
    val rules: List<AutoTrackRule> = emptyList(),
    val serverConfig: BackupServerConfigBundle? = null,
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

data class ChangelogEntry(
    val version: String,
    val title: String,
    val changes: List<String>,
)

object AppInfo {
    const val VERSION_NAME = "1.1.0"
    const val GITHUB_OWNER = "ackd7"
    const val GITHUB_REPO = "MochiLedger"
    const val GITHUB_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
    const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val RELEASES_LATEST_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    val CHANGELOG: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            version = "v1.1.0",
            title = "全新应用图标 · 定时自动备份 · 每日首开备份 · 全量含服务器配置备份恢复",
            changes = listOf(
                "全新重绘「墨记」应用图标（深靛紫渐变 + 磨砂层叠账本 + 琥珀金币），Android 自适应图标与 Windows EXE/任务栏全面焕新",
                "新增「每天首次打开软件自动备份」与「定时自动备份」（支持 1 / 3 / 6 / 12 / 24 小时间隔，可联动远端同步并自动清理旧份）",
                "全面升级手动备份与导入备份：支持一键本地备份、本地历史备份列表查看大小/一键恢复/删除，以及系统文件选择器导入导出 .mochi.json",
                "全量备份支持同时打包同步服务器信息（WebDAV / 自建服务器地址与 Token / 自动记账与自动备份设置），换机重装一键无缝还原",
            ),
        ),
        ChangelogEntry(
            version = "v1.0.9",
            title = "Windows 圆角窗口 · 电脑与手机端统一底部悬浮导航",
            changes = listOf(
                "电脑端移除左侧边栏，改为与手机端完全一致的底部悬浮胶囊导航栏，保持全端统一视觉体验",
                "Windows 端启用透明无边框圆角窗口（18dp 圆角 + 细微玻璃边框，最大化时自动贴合屏幕边缘）",
            ),
        ),
        ChangelogEntry(
            version = "v1.0.8",
            title = "统计页布局与动态月份标题优化",
            changes = listOf(
                "统计页「支出分析 / 收入分析」切换栏改为顶部居中，解决小屏手机顶部按钮拥挤裁切问题",
                "日期月份选择器移入汇总卡片内部，切换月份后环形图中央自动动态显示对应年月的总支出/总收入",
            ),
        ),
        ChangelogEntry(
            version = "v1.0.7",
            title = "Windows 与 Android 双端统一应用图标修复",
            changes = listOf(
                "修复部分 Android 桌面（如 HyperOS / MIUI）安装后应用图标回退为默认机器人的问题，补全全分辨率 mipmap PNG 并优化自适应背景",
                "Windows 端 EXE、NSIS 安装程序与任务栏图标同步统一为同一套墨蓝账本图标，双端视觉完全一致",
            ),
        ),
        ChangelogEntry(
            version = "v1.0.6",
            title = "LSPosed 支付成功页 UI 捕获 · AutoAccounting 静默协议",
            changes = listOf(
                "LSPosed 模块新增支付成功页 UI 捕获（不依赖微信/支付宝混淆类名，关闭通知推送也能自动记账）",
                "开放静默记账协议 mochiledger://add，支持与 AutoAccounting（自动记账）及 Tasker 无缝联动",
                "增强支付结果页文案识别与防重复去重机制",
            ),
        ),
        ChangelogEntry(
            version = "v1.0.5",
            title = "Android 应用图标 · 页面切换动效 · 系统返回手势 · 极简返回按钮",
            changes = listOf(
                "为 Android 端新增自适应矢量应用图标（支持 Android 13+ 单色主题图标）",
                "电脑端与手机端全面新增平滑的页面切换与二级/三级页面推进弹出动画",
                "修复手机端在二级/三级页面触发系统返回手势直接退出到桌面的问题，现可逐级顺滑返回",
                "将「更新日志」收纳至「当前版本」页面内的三级子页面中",
                "统一二级/三级页面返回按钮为纯图标样式，并消除多层返回栏重叠",
            ),
        ),
        ChangelogEntry(
            version = "v1.0.4",
            title = "无边框窗口 · 扁平矢量图标 · 快捷日期选择 · 更新日志",
            changes = listOf(
                "电脑端移除原生标题栏，窗口最小化/最大化/关闭按钮融入 UI 顶部并支持拖拽",
                "电脑端「同步」与手机端「自动记账 / 同步」统一收纳进设置二级菜单",
                "Windows 与 Android 全端图标重构为统一扁平化矢量图标",
                "统计页面右上角支持点击弹出快捷年份/月份选择器",
                "修复版本更新检查失效问题，新增 GitHub Redirect 兜底并在软件内提供更新日志",
            ),
        ),
        ChangelogEntry(
            version = "v1.0.3",
            title = "纯净初始化 · 多源去重同步 · 悬浮底栏 · 三架构 APK",
            changes = listOf(
                "全新安装不再预置示例账单与初始资产余额",
                "同步设置改为二级入口，支持 WebDAV 与自建服多源联合同步与重复项去重",
                "账单主页改为右下角悬浮「+」记账按钮，手机端升级悬浮胶囊底栏",
                "Android 新增 universal / arm64-v8a / armeabi-v7a 三架构分包",
            ),
        ),
        ChangelogEntry(
            version = "v1.0.2",
            title = "Windows 运行时修复",
            changes = listOf(
                "补齐 jlink 运行时 java.sql 与 jdk.crypto.ec 模块，修复 SQLite 与 HTTPS 同步",
            ),
        ),
        ChangelogEntry(
            version = "v1.0.1",
            title = "Windows NSIS 安装包",
            changes = listOf(
                "新增 Windows NSIS 图形安装程序（支持中文向导、快捷方式与卸载保留数据）",
            ),
        ),
        ChangelogEntry(
            version = "v1.0.0",
            title = "墨记 MochiLedger 首个开源版本",
            changes = listOf(
                "Windows + Android 跨端记账、WebDAV / 独立自建同步服务端、LSPosed 微信/支付宝自动记账",
            ),
        ),
    )
}

object DefaultLedgerSeed {
    fun defaultAccounts(now: Long): List<LedgerAccount> = emptyList()

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

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        val rounded = kotlin.math.round(kb * 10.0) / 10.0
        return "$rounded KB"
    }
    val mb = kb / 1024.0
    val rounded = kotlin.math.round(mb * 100.0) / 100.0
    return "$rounded MB"
}
