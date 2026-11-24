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
    UNIONPAY("云闪付", "com.unionpay"),
    MEITUAN("美团", "com.sankuai.meituan"),
    JINGDONG("京东", "com.jingdong.app.mall"),
    TAOBAO("淘宝", "com.taobao.taobao"),
    PINDUODUO("拼多多", "com.xunmeng.pinduoduo"),
    ELEME("饿了么", "me.ele"),
    DIDI("滴滴出行", "com.sdu.didi.psnger"),
    BANK_SMS("银行通知", "com.android.mms"),
    OTHER_APP("应用通知", ""),
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
    val useLsposedMode: Boolean = true,
    val hookWeChat: Boolean = true,
    val hookAlipay: Boolean = true,
    val fallbackNotificationListener: Boolean = true,
    val listenOtherApps: Boolean = true,
    val autoConfirm: Boolean = true,
    val dedupeWindowSeconds: Int = 20,
    val floatingConfirmEnabled: Boolean = true,
    val floatingConfirmSeconds: Int = 6,
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
    const val VERSION_NAME = "1.5.0"
    const val GITHUB_OWNER = "ackd7"
    const val GITHUB_REPO = "MochiLedger"
    const val GITHUB_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"
    const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val RELEASES_LATEST_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    val CHANGELOG: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            version = "v1.5.0",
            title = "手动记账自由选时间 · 全应用通知规则记账（美团/京东/云闪付/银行等）· LSPosed 与通知监听协同共开",
            changes = listOf(
                "手动记账与编辑账单新增日期+时间选择器（标准星期对齐日历 + 时分精确调节 + 现在/昨天快捷按钮），不再受限于录入时刻",
                "系统通知监听全面拓展至美团、京东、淘宝、拼多多、饿了么、滴滴、云闪付、银行短信/App 及任意符合分类规则的第三方应用，内置促销/物流防误判过滤",
                "解除 LSPosed 模式与系统通知监听的互斥限制，支持两者同时开启：LSPosed 深度捕获微信/支付宝收款人，通知监听同步捕获美团/京东等应用订单，跨应用同金额自动去重并互补商品明细",
            ),
        ),
        ChangelogEntry(
            version = "v1.4.0",
            title = "Google Calendar 日历选择器 · Android 永久统一签名 · LSPosed 收款人记忆 · 极简扁平钱包图标 · 高星记账规则库",
            changes = listOf(
                "参考 Google Calendar 重构统计页日期选择器：标准星期对齐月历、账单日期小圆点、点击年月展开快捷跨年跨月网格、顶部「今天」与底部「看整月 / 看全年 / 查看当日」一键切换",
                "引入 Android 永久固定签名证书（mochiledger.jks），彻底解决每次更新提示签名不同的问题，后续版本均可直接覆盖升级",
                "升级 LSPosed 收银台收款人记忆 + 弹窗监听，自动补全通知中缺失的收款人名字；账单详情页支持查看原始支付信息",
                "采用 Simple Icons 官方极简扁平矢量路径重构微信、支付宝、Apple Pay、Visa 钱包图标，严格延续 Miuix + Mochi 审美；全端选项条支持触控左滑横拉",
                "参考 ezBookkeeping / AutoAccounting 等高星项目大幅扩充默认智能分类规则库，并在自动记账方案下方增加互斥与搭配使用提示",
            ),
        ),
        ChangelogEntry(
            version = "v1.3.0",
            title = "全端交互与界面精雕 · 今日记账 · 精细日期与分类统计 · 专属资产图标 · 扁平化备份中心",
            changes = listOf(
                "自动记账明确区分「方案一：Root / LSPosed」与「方案二：免 Root 通知监听」，开启 LSPosed 自动关闭通知监听，切换冲突时弹出提示",
                "备份与同步页彻底扁平化：去除冗长二级页，直接展示 WebDAV 与自建服务器运行状态卡片、导入导出与自动备份设置，底部新增「备份与同步使用教程」入口",
                "资产页为微信、支付宝、银行卡、信用卡、现金、理财配备专属品牌矢量图标与配色，移除顶部预览添加按钮，改为统一右下角悬浮「+」按钮",
                "统计页升级「按日 / 按月 / 按年」精细日期选择器，支持收支/全部切换并支持点击任意分类（餐饮、购物等）筛选查看对应账单明细",
                "账单主页默认聚焦「今日记账」（可一键切本月），每笔账单精简掉技术来源标签，干净展示时间、付款钱包与金额",
            ),
        ),
        ChangelogEntry(
            version = "v1.2.0",
            title = "LSPosed 商品明细抓取 · 付款后轻量悬浮确认条 · 微信/支付宝账单补全 · 权限体检",
            changes = listOf(
                "升级 LSPosed 支付页深度解析：自动从支付成功页与账单详情页提取商户全称、商品说明与付款方式并写入备注",
                "新增「付款后轻量悬浮确认条」（免 Root 可用）：自动记账后顶部弹出 6 秒倒计时小卡片，支持 1 秒点击快捷场景标签（早餐/午饭/买菜/超市等）或补充备注，解决路边摊扫码无明细痛点",
                "新增「微信 / 支付宝官方账单 CSV 智能导入与补全」：全端支持自动识别 UTF-8 / GBK 编码账单，按时间+金额智能匹配本地已有账单补全商品明细（不重复入账）并导入漏记账单",
                "新增「自动记账权限健康体检」面板：实时检查 LSPosed、通知使用权、悬浮窗、后台电池优化白名单与通知提醒 5 项权限，缺项一键直达系统设置，防止自动记账失效",
            ),
        ),
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
        AutoTrackRule("rule_meituan", "餐饮美食", "美团|饿了么|瑞幸|星巴克|库迪|麦当劳|肯德基|汉堡王|塔斯汀|喜茶|霸王茶姬|蜜雪冰城|茶百道|古茗|奈雪|海底捞|萨莉亚|必胜客|沙县|拉面|麻辣烫|烧烤|火锅|奶茶|咖啡|餐饮|食堂|早餐|午餐|晚餐|外卖|面包|烘焙|水果|百果园", null, "cat_food", null, true, now),
        AutoTrackRule("rule_transport", "交通出行", "滴滴|高德打车|曹操出行|T3出行|花小猪|哈啰|美团单车|青桔|地铁|公交|乘车码|铁路12306|携程|去哪儿|飞猪|中石化|中石油|壳牌|特来电|星星充电|小桔充电|高速|ETC|停车|洗车|顺丰|菜鸟|快递", null, "cat_transport", null, true, now),
        AutoTrackRule("rule_shopping", "购物超市", "淘宝|天猫|京东|拼多多|唯品会|得物|抖音电商|盒马|山姆|开市客|沃尔玛|永辉|大润发|华润万家|胖东来|罗森|全家|7-Eleven|711|美宜佳|便利蜂|超市|便利店|屈臣氏|名创优品|优衣库|迪卡侬|无印良品|宜家", null, "cat_shopping", null, true, now),
        AutoTrackRule("rule_digital", "数码订阅", "Apple|App Store|iCloud|Google|Steam|Nintendo|PlayStation|Xbox|B站|哔哩哔哩|网易云|QQ音乐|腾讯视频|爱奇艺|优酷|百度网盘|夸克|WPS|ChatGPT|Claude|OpenAI|GitHub|阿里云|腾讯云|Cloudflare|会员|订阅", null, "cat_digital", null, true, now),
        AutoTrackRule("rule_housing", "居家水电", "国家电网|南方电网|电费|水务|水费|燃气|天然气|供暖|中国移动|中国联通|中国电信|广电|话费|宽带|物业|房租|自如|贝壳", null, "cat_housing", null, true, now),
        AutoTrackRule("rule_entertainment", "休闲娱乐", "电影|猫眼|淘票票|万达影城|KTV|剧本杀|密室|台球|健身|乐刻|Keep|门票|景区|乐园|迪士尼|腾讯游戏|米哈游|网易游戏", null, "cat_entertainment", null, true, now),
        AutoTrackRule("rule_medical", "医疗药店", "医院|诊所|口腔|牙科|眼科|药房|药店|大药房|益丰|老百姓|一心堂|叮当快药|京东健康|阿里健康|体检|挂号|医保", null, "cat_medical", null, true, now),
        AutoTrackRule("rule_yuebao", "理财收益", "余额宝|理财通|零钱通|收益|分红|利息|基金", null, "cat_invest", null, true, now),
        AutoTrackRule("rule_salary", "工资薪酬", "工资|薪酬|薪资|奖金|绩效|补贴|津贴|代发", null, "cat_salary", null, true, now),
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
