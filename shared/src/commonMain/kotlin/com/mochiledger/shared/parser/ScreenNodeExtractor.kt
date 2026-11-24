package com.mochiledger.shared.parser

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

data class ScreenCaptureCandidate(
    val title: String,
    val content: String,
    val signature: String,
)

/**
 * Pure Kotlin analyzer for AccessibilityService node text streams.
 *
 * Designed to accurately synthesize structured payment payloads from real-time
 * payment completion screens (WeChat, Alipay, UnionPay, Meituan, JD, Taobao, Bank apps, etc.)
 * while strictly blocking historical bill lists, pre-payment cashiers, and chat windows.
 */
object ScreenNodeExtractor {

    private val ignoredPackagePrefixes = listOf(
        "com.mochiledger",
        "com.android.systemui",
        "com.android.settings",
        "com.android.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.bbk.launcher",
        "com.sec.android.app.launcher",
        "com.google.android.inputmethod",
        "com.sohu.inputmethod",
        "com.baidu.input",
        "com.iflytek.inputmethod",
        "com.tencent.wetype",
        "com.tencent.mobileqq",
        "com.tencent.tim",
        "org.telegram.messenger",
        "com.discord",
    )

    // Screens that represent history lists, pre-payment cashiers, or chat windows (MUST NOT trigger)
    private val screenBlockers = listOf(
        // History / Order Lists
        "全部订单", "历史订单", "账单列表", "收支明细", "月账单", "年度账单", "搜索账单",
        // Pre-payment / Checkout / Password prompts (before payment actually succeeds!)
        "确认付款", "立即支付", "立即付款", "确认支付", "提交订单", "去结算",
        "请输入支付密码", "输入密码", "使用密码", "指纹支付", "面容支付", "验证指纹",
        // Chat conversation chrome (prevents screenshots/texts in chat from triggering)
        "按住 说话", "切换到语音", "更多功能按钮，已折叠",
    )

    private val primarySuccessMarkers = listOf(
        "支付成功", "付款成功", "交易成功", "收款成功", "下单成功", "购买成功", "充值成功", "转账成功", "扣款成功",
    )

    private val completionActionHints = listOf(
        "完成", "返回商家", "查看订单", "订单详情", "关闭", "实付", "实付款",
    )

    private val merchantLabels = setOf(
        "收款方", "收款人", "商户全称", "商户名称", "商家名称", "商户", "商家", "店铺", "门店", "对方账户", "收款商户",
    )

    private val productLabels = setOf(
        "商品说明", "商品名称", "商品信息", "购买商品", "商品", "订单信息", "订单详情",
    )

    private val payMethodLabels = setOf(
        "付款方式", "支付方式", "扣款方式", "交易方式", "支付工具",
    )

    private val genericUiWords = setOf(
        "支付成功", "付款成功", "交易成功", "收款成功", "下单成功", "购买成功", "充值成功", "转账成功", "扣款成功",
        "已支付", "微信支付", "支付宝", "云闪付", "美团", "京东", "淘宝",
        "完成", "返回商家", "查看订单", "返回", "关闭", "更多", "详情", "账单详情", "交易详情",
        "优惠", "立减", "满减", "摇一摇", "有奖", "领券", "评价", "开发票", "向商家付款",
        "收款方", "商户全称", "商品", "商品说明", "付款方式", "支付方式", "实付", "实付款", "合计",
    )

    private val currencySymbols = setOf("¥", "￥", "RMB")
    private val decimalNumberRegex = Regex("""^[0-9]+(?:\.[0-9]{1,2})?$""")
    private val fractionTailRegex = Regex("""^\.[0-9]{1,2}$""")
    private val amountCaptureRegex = Regex("""(?:实付|实付款|合计|总计)?[：:\s]*(?:¥|￥|RMB\s*)([0-9]+(?:\.[0-9]{1,2})?)|([0-9]+\.[0-9]{2})\s*元""")
    private val fullDateTimeRegex = Regex("""(20[0-9]{2})[-/年](\d{1,2})[-/月](\d{1,2})日?(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""")

    fun synthesizePaymentPayload(
        packageName: String,
        nodeTexts: List<String>,
        nowMillis: Long,
    ): ScreenCaptureCandidate? {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return null
        val lowerPkg = pkg.lowercase()
        if (ignoredPackagePrefixes.any { lowerPkg.startsWith(it) }) return null

        val normalized = normalizeNodes(nodeTexts)
        if (normalized.size < 2) return null

        val joined = normalized.joinToString(" ")

        // 1. Block history lists, pre-payment cashiers, and chat windows
        if (screenBlockers.any { joined.contains(it) }) return null
        if (joined.contains("待付款") && joined.contains("待收货") && joined.contains("待评价")) return null
        if (joined.contains("通讯录") && joined.contains("发现") && joined.contains("我")) return null

        // 2. Require payment completion status marker
        val statusIdx = normalized.indexOfFirst { node ->
            primarySuccessMarkers.any { marker -> node.contains(marker) }
        }.let { idx ->
            if (idx >= 0) idx else {
                // Fallback: "已支付" accompanied by completion button/amount label
                val paidIdx = normalized.indexOfFirst { it == "已支付" || it == "订单已支付" }
                if (paidIdx >= 0 && completionActionHints.any { hint -> normalized.any { it.contains(hint) } }) {
                    paidIdx
                } else {
                    -1
                }
            }
        }
        if (statusIdx < 0) return null

        // 3. Guard against opening historical bill/order detail pages from hours/days ago
        if (containsStaleTimestamp(joined, nowMillis)) return null

        // 4. Extract amount token from screen nodes
        val amountMatch = amountCaptureRegex.find(joined) ?: return null
        val rawAmountStr = (amountMatch.groupValues[1].ifBlank { amountMatch.groupValues[2] }).trim()
        val amountVal = rawAmountStr.toDoubleOrNull() ?: return null
        if (amountVal <= 0.0) return null

        // 5. Extract structured label -> value fields (merchant / product / payment method)
        var merchant = ""
        var product = ""
        var payMethod = ""

        for (i in 0 until normalized.size - 1) {
            val label = normalized[i].trimEnd('：', ':').trim()
            val nextVal = normalized[i + 1].trim()
            if (nextVal.isBlank() || nextVal in genericUiWords) continue

            when {
                merchant.isBlank() && label in merchantLabels -> merchant = nextVal.take(28)
                product.isBlank() && label in productLabels -> product = nextVal.take(36)
                payMethod.isBlank() && label in payMethodLabels -> payMethod = nextVal.take(24)
            }
        }

        // 6. Positional merchant fallback for clean result pages like ["支付成功", "瑞幸咖啡", "¥19.90", "完成"]
        if (merchant.isBlank()) {
            val amountNodeIdx = normalized.indexOfFirst { it.contains(rawAmountStr) }
            val candidateIndices = listOf(
                statusIdx + 1,
                statusIdx - 1,
                amountNodeIdx - 1,
                statusIdx + 2,
            ).filter { it in normalized.indices }

            for (idx in candidateIndices) {
                val cand = normalized[idx]
                if (isPlausibleMerchantNode(cand, rawAmountStr)) {
                    merchant = cand
                    break
                }
            }
        }

        val statusMarker = normalized[statusIdx].take(16)
        val synthesizedContent = buildString {
            if (merchant.isNotBlank()) append("商户：").append(merchant).append(' ')
            if (product.isNotBlank()) append("商品：").append(product).append(' ')
            if (payMethod.isNotBlank()) append("付款方式：").append(payMethod).append(' ')
            append(statusMarker).append(" ¥").append(rawAmountStr)
        }

        val title = when {
            pkg == "com.tencent.mm" -> "微信支付"
            pkg == "com.eg.android.AlipayGphone" -> "支付宝"
            merchant.isNotBlank() -> merchant
            else -> statusMarker
        }

        val signature = "$pkg|$statusMarker|$rawAmountStr|$merchant"
        return ScreenCaptureCandidate(
            title = title,
            content = synthesizedContent,
            signature = signature,
        )
    }

    /**
     * Merges split currency/amount TextViews common in WeChat/Alipay/Meituan/JD UIs:
     * e.g. ["¥", "19", ".90"] -> ["¥19.90"], ["29.90", "元"] -> ["29.90元"]
     */
    internal fun normalizeNodes(rawNodes: List<String>): List<String> {
        val cleaned = rawNodes
            .map { it.replace('\n', ' ').replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotEmpty() && it.length <= 100 }

        if (cleaned.isEmpty()) return emptyList()

        val merged = mutableListOf<String>()
        var i = 0
        while (i < cleaned.size) {
            var cur = cleaned[i]
            // Merge ["¥" | "￥", "19.90"] -> "¥19.90"
            if (cur in currencySymbols && i + 1 < cleaned.size && decimalNumberRegex.matches(cleaned[i + 1])) {
                cur = "¥${cleaned[i + 1]}"
                i += 1
                // Also merge ["¥19", ".90"] -> "¥19.90"
                if (i + 1 < cleaned.size && fractionTailRegex.matches(cleaned[i + 1]) && !cur.contains('.')) {
                    cur += cleaned[i + 1]
                    i += 1
                }
            } else if (decimalNumberRegex.matches(cur) && !cur.contains('.') && i + 1 < cleaned.size && fractionTailRegex.matches(cleaned[i + 1])) {
                // Merge ["19", ".90"] -> "19.90"
                cur += cleaned[i + 1]
                i += 1
            }

            // Merge ["19.90", "元"] -> "19.90元"
            if ((decimalNumberRegex.matches(cur) || cur.startsWith("¥")) && i + 1 < cleaned.size && cleaned[i + 1] == "元") {
                cur += "元"
                i += 1
            }

            // Avoid consecutive identical duplicate text nodes (e.g. contentDescription == text)
            if (merged.lastOrNull() != cur) {
                merged.add(cur)
            }
            i += 1
        }
        return merged
    }

    private fun isPlausibleMerchantNode(node: String, rawAmountStr: String): Boolean {
        if (node.length !in 2..24) return false
        if (node in genericUiWords) return false
        if (node.contains(rawAmountStr)) return false
        if (node.startsWith("¥") || node.startsWith("￥") || node.endsWith("元")) return false
        if (primarySuccessMarkers.any { node.contains(it) }) return false
        if (node.all { it.isDigit() || it == '.' || it == '-' || it == ':' || it == ' ' }) return false
        if (node.contains("优惠") || node.contains("立减") || node.contains("红包") || node.contains("积分") || node.contains("时间") || node.contains("单号")) return false
        return true
    }

    private fun containsStaleTimestamp(text: String, nowMillis: Long): Boolean {
        if (nowMillis <= 0L) return false
        val tz = TimeZone.currentSystemDefault()
        val nowDt = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(tz)

        for (match in fullDateTimeRegex.findAll(text)) {
            val y = match.groupValues[1].toIntOrNull() ?: continue
            val m = match.groupValues[2].toIntOrNull() ?: continue
            val d = match.groupValues[3].toIntOrNull() ?: continue
            val hourStr = match.groupValues[4]
            val minStr = match.groupValues[5]
            val secStr = match.groupValues[6]

            val parsedAllowed = runCatching {
                if (hourStr.isNotEmpty() && minStr.isNotEmpty()) {
                    val ldt = LocalDateTime(
                        y,
                        m,
                        d,
                        hourStr.toInt(),
                        minStr.toInt(),
                        secStr.toIntOrNull() ?: 0,
                    )
                    val tsMillis = ldt.toInstant(tz).toEpochMilliseconds()
                    // Allow up to 5 minutes difference for a live payment completion screen
                    abs(nowMillis - tsMillis) <= 5 * 60_000L
                } else {
                    val date = LocalDate(y, m, d)
                    date == nowDt.date
                }
            }.getOrDefault(true)

            if (!parsedAllowed) return true
        }
        return false
    }
}
